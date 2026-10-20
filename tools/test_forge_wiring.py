#!/usr/bin/env python3
"""Source-level Forge adapter contract tests to catch lost integration wiring.

These deliberately complement Java policy tests and JDK8 compilation; they do
not simulate Minecraft rendering, prove in-game behavior or parse third-party
classes.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
FORGE = ROOT / "forge189/src/main/java/dev/ghostfusion/forge189"


def content(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


class ForgeWiring(unittest.TestCase):
    def setUp(self):
        self.feature = (FORGE / "ForgeFeatureDriver.java").read_text(encoding="utf-8")
        self.gl = (FORGE / "RenderStateGuard.java").read_text(encoding="utf-8")
        self.adapter = (FORGE / "ForgeEventAdapter.java").read_text(encoding="utf-8")

    def test_recovery_promotes_verified_typed_snapshot_before_live_mutation(self):
        core=content("src/main/java/dev/ghostfusion/core/ConfigIntegrity.java")
        profiles=content("src/main/java/dev/ghostfusion/core/ProfileStore.java")
        ui=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        self.assertIn("while((count=in.read(block))!=-1)",core)
        self.assertIn("count>MAX_BYTES-bytes.size()",core)
        self.assertIn("if(p.size()>MAX_FIELDS)",core)
        self.assertIn("typedValidator.test(staged)",core)
        self.assertIn("restoreBackup(primary,",profiles)
        self.assertIn("restoreBackup(primary,",ui)
        self.assertIn("loadFile(primary);",profiles)
        self.assertIn("loadFile(primary);",ui)
        self.assertNotIn("loadFile(backup);",profiles)
        self.assertNotIn("loadFile(backup);",ui)

    def test_typed_backup_protection_uses_same_live_load_parser(self):
        integrity=content("src/main/java/dev/ghostfusion/core/ConfigIntegrity.java")
        profiles=content("src/main/java/dev/ghostfusion/core/ProfileStore.java")
        ui=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        self.assertIn("typedValidator.test(p)",integrity)
        self.assertIn("stage(ConfigIntegrity.read(source))",profiles)
        self.assertIn("stage(ConfigIntegrity.read(file))",ui)
        self.assertIn("rotateVerifiedBackup(destination,",profiles)
        self.assertIn("rotateVerifiedBackup(file,",ui)
        self.assertIn("stage(candidate)",profiles)
        self.assertIn("stage(candidate)",ui)

    def test_each_feature_has_runtime_fault_gate(self):
        for stable_id in (
            "hud.keystrokes", "hud.cps", "hud.target", "hud.diagnostics"
        ):
            with self.subTest(stable_id=stable_id):
                self.assertIn(f'drawFeature("{stable_id}"', self.feature)
                self.assertNotIn(f'host.featureEnabled("{stable_id}")', self.feature)
        self.assertIn('host.runFeature("movement.sprint"', self.feature)
        self.assertIn('host.runFeature("render.esp"', self.feature)
        self.assertIn("host.runFeature(id,", self.feature)
        self.assertIn("RenderStateGuard.draw(painter)", self.feature)
        self.assertIn('private void sprintTick()', self.feature)

    def test_keystrokes_use_native_bindings_not_hardcoded_wasd(self):
        self.assertIn("GameSettings.getKeyDisplayString(code)", self.feature)
        for key in ("Forward","Back","Left","Right","Jump","Sneak"):
            with self.subTest(binding=key):
                pattern=rf'key\(keyName\(mc\.gameSettings\.keyBind{key}\)'
                self.assertRegex(self.feature, pattern)
        for name in ("W","A","S","D","SPACE","SHIFT"):
            self.assertNotIn(f'key("{name}"',self.feature)
        self.assertIn('getStringWidth(label)>width-5',self.feature)

    def test_separate_gl_guard_scopes_and_matrix_restoration(self):
        self.assertIn("GL11.GL_ALL_ATTRIB_BITS",self.gl)
        self.assertIn("GL11.GL_PROJECTION",self.gl)
        self.assertIn("GL11.GL_MODELVIEW",self.gl)
        self.assertGreaterEqual(self.gl.count("GL11.glPushMatrix()"),2)
        self.assertGreaterEqual(self.gl.count("GL11.glPopMatrix()"),2)
        self.assertIn("GL11.glPopAttrib()",self.gl)
        self.assertIn("finally",self.gl)
        self.assertEqual(self.feature.count('drawFeature("hud.'),4)

    def test_fault_warning_reaches_real_forge_overlay(self):
        self.assertIn("host.faultWarning()",self.adapter)
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        self.assertIn('public boolean runFeature(String id, Runnable callback)',host)
        self.assertIn('featureFaults.execute(id, callback)',host)
        self.assertIn('faultWarning = "Module disabled:',host)
        self.assertIn('diagnostics.record(Counter.FEATURE_FAULT)',host)

    def test_gui_viewport_wiring_not_regressed(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        self.assertIn("GuiLayout.calculate(width,height,ui.layout())",gui)
        self.assertIn("syncSearch(g)",gui)
        self.assertIn("resized.setText(previous)",gui)
        self.assertIn("resized.setFocused(focused)",gui)

    def test_combat_fault_boundaries_and_intent_cancellation(self):
        combat=(FORGE/"ForgeCombatController.java").read_text(encoding="utf-8")
        for id in ("combat.aimassist","combat.clicker","combat.hitselect","combat.blockhit"):
            self.assertIn('"'+id+'"',combat)
        for constant in ("AIM_ID","CLICK_ID","HIT_ID","BLOCK_ID"):
            self.assertIn('host.runFeature('+constant+',',combat)
        self.assertIn('session.rotations().cancel(AIM_ID);',combat)
        self.assertGreaterEqual(combat.count('session.actions().cancelOwner(CLICK_ID);'),2)
        self.assertGreaterEqual(combat.count('session.actions().cancelOwner(BLOCK_ID);'),2)
        self.assertIn('extraAttackQueued[0]',combat)
        self.assertIn('KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode())',combat)
        self.assertIn('KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode())',combat)
        self.assertNotIn('attackEntity(',combat)
        self.assertNotIn('sendPacket(',combat)

    def test_exact_world_identity_and_snapshot_fencing(self):
        from_source=(FORGE/"ForgeEventAdapter.java").read_text(encoding="utf-8")
        combat=(FORGE/"ForgeCombatController.java").read_text(encoding="utf-8")
        services=content("src/main/java/dev/ghostfusion/services/SessionServices.java")
        snap=content("src/main/java/dev/ghostfusion/services/WorldSnapshot.java")
        self.assertIn("worldIdentity.observe(minecraft.theWorld,minecraft.thePlayer)",from_source)
        self.assertIn("Transition.SWITCH",from_source)
        self.assertIn("host.worldPresence(false);",from_source)
        self.assertIn("host.worldPresence(true);",from_source)
        self.assertIn("worldIdentity.reset();",from_source)
        self.assertIn("WorldSnapshot.validPlayerPose(",from_source)
        self.assertIn("WorldSnapshotAdmission<EntityPlayer>()",from_source)
        self.assertIn("nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible)",from_source)
        self.assertIn("new ArrayList<WorldSnapshot.Entity>(nearestSnapshot.size())",from_source)
        self.assertIn("nearestSnapshot.clear();\n            }",from_source)
        self.assertNotIn("if (players.size() == 256) break;",from_source)
        self.assertIn("snapshot=null;",services)
        self.assertIn("rotations.prune(tick,generation)",services)
        self.assertIn("snapshot()==null",combat)
        self.assertIn("session.snapshot().tick!=session.tick()",combat)
        self.assertIn("pitch>=-90f && pitch<=90f",snap)

    def test_snapshot_admission_is_nearest_first_and_clears_raw_player_refs(self):
        snapshot=self.adapter
        budget=content("src/main/java/dev/ghostfusion/features/EspNearestBudget.java")
        self.assertIn("private final WorldSnapshotAdmission<EntityPlayer> nearestSnapshot",snapshot)
        self.assertIn("p.getHealth()",snapshot)
        self.assertIn("health<=0",snapshot)
        self.assertIn("double distanceSq=dx*dx+dy*dy+dz*dz;",snapshot)
        self.assertLess(snapshot.index("nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible)"),
                        snapshot.index("host.services().publish(new WorldSnapshot("))
        self.assertIn("} finally {\n                nearestSnapshot.clear();",snapshot)
        self.assertNotIn("Set<Integer> seenEntityIds",snapshot)
        self.assertIn("capacity>256",budget)
        self.assertIn("dev.ghostfusion.tests.NearestSnapshotTests",
                      content(".github/workflows/core-ci.yml"))

    def test_visibility_partitioned_snapshots_keep_visible_candidates(self):
        adapter=self.adapter
        admission=content("src/main/java/dev/ghostfusion/services/WorldSnapshotAdmission.java")
        self.assertIn("new WorldSnapshotAdmission<EntityPlayer>()",adapter)
        self.assertIn("boolean visible=demand.testVisibility() &&",adapter)
        self.assertIn("nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible)",adapter)
        self.assertIn("nearestSnapshot.forEach(new WorldSnapshotAdmission.Visitor<EntityPlayer>()",adapter)
        self.assertIn("p.getEntityId(),p.posX,p.posY,p.posZ,health,true,visible,true",adapter)
        self.assertIn("new EspNearestBudget<T>(256)",admission)
        self.assertIn("hidden.removeId(id)",admission)
        self.assertIn("visible.containsId(id)",admission)
        self.assertIn("sink.visit(visible.get(a++),true)",admission)
        self.assertIn("sink.visit(hidden.get(b++),false)",admission)
        self.assertIn("dev.ghostfusion.tests.VisibilitySnapshotTests",
                      content(".github/workflows/core-ci.yml"))

    def test_snapshot_work_is_demand_and_range_gated_before_visibility_rays(self):
        source=self.adapter
        demand=content("src/main/java/dev/ghostfusion/services/SnapshotDemand.java")
        self.assertIn("SnapshotDemand.of(",source)
        self.assertIn('host.featureEnabled("hud.target"),host.targetVisible(),host.targetRange()',source)
        self.assertIn('host.featureEnabled("combat.aimassist"),host.aimVisibleOnly(),host.aimRange()',source)
        self.assertIn("if(demand.needed()) for (Object raw : minecraft.theWorld.playerEntities)",source)
        self.assertLess(source.index("if(!demand.accepts(distanceSq))continue;"),
                        source.index("minecraft.thePlayer.canEntityBeSeen(p);"))
        self.assertIn("boolean visible=demand.testVisibility() &&",source)
        self.assertIn("if(!demand.admit(visible))continue;",source)
        self.assertIn("nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible)",source)
        self.assertIn("host.services().publish(new WorldSnapshot(",source)
        self.assertIn("} finally {\n                nearestSnapshot.clear();",source)
        self.assertIn("public boolean testVisibility(){return needVisible;}",demand)
        self.assertIn("public boolean needed(){return needVisible||needUnfiltered;}",demand)
        self.assertIn("square<=maximumDistanceSq",demand)
        self.assertIn("dev.ghostfusion.tests.SnapshotDemandTests",
                      content(".github/workflows/core-ci.yml"))

    def test_r210_warning_not_gated_by_status_and_lifecycle_fps_pause(self):
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        perf=content("src/main/java/dev/ghostfusion/host/FramePerformance.java")
        diag=content("src/main/java/dev/ghostfusion/host/RuntimeDiagnostics.java")
        placement=content("src/main/java/dev/ghostfusion/ui/HudWarningPlacement.java")
        self.assertIn("if(blocked) framePerformance.suspend();",host)
        self.assertIn("!focused || inputBlocked || !worldPresent",host)
        self.assertIn("host.performancePause();\n            return;",self.adapter)
        self.assertIn("final int nx=warningStatusX;",self.adapter)
        self.assertIn("HudWarningPlacement.y(",self.adapter)
        self.assertIn("int warningStatusX=0,warningStatusBottom=2;",self.adapter)
        self.assertIn("perf.invalid_intervals",diag)
        self.assertIn("invalidIntervals",perf)
        self.assertIn("Math.max(0,Math.min(wanted,",placement)
        self.assertIn("dev.ghostfusion.tests.LifecycleVisibilityTests",
                      content(".github/workflows/core-ci.yml"))

    def test_forge_frame_performance_is_focused_and_opt_in(self):
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        sample=content("src/main/java/dev/ghostfusion/host/FramePerformance.java")
        diag=content("src/main/java/dev/ghostfusion/host/RuntimeDiagnostics.java")
        self.assertIn("host.performanceFrame(System.nanoTime()",self.adapter)
        self.assertIn("minecraft.currentScreen==null && minecraft.inGameHasFocus",self.adapter)
        self.assertIn("host.diagnosticsEnabled()",self.adapter)
        self.assertIn("host.performancePause();",self.adapter)
        self.assertIn("framePerformance.suspend();",host)
        self.assertIn("framePerformance.frame(nowNanos,usedHeapBytes)",host)
        self.assertIn("diagnostics.save(configDirectory,framePerformance.snapshot())",host)
        self.assertIn("new long[MAX_FPS+1]",sample)
        self.assertIn("perf.frame_samples",diag)
        self.assertIn("perf.p99_fps",diag)
        self.assertIn("perf.peak_heap_mib",diag)
        self.assertIn("String fps=\"FPS min/p50/p95/p99 \"",self.feature)
        self.assertIn("dev.ghostfusion.tests.FramePerformanceTests",
                      content(".github/workflows/core-ci.yml"))

    def test_opt_in_esp_health_bar_geometry_and_persistence_wiring(self):
        renderer=(FORGE/"PlayerEspRenderer.java").read_text(encoding="utf-8")
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        geometry=content("src/main/java/dev/ghostfusion/features/EspHealthBarGeometry.java")
        jar=content("tools/verify_forge_jar.py")
        self.assertIn('new Setting.Bool("show_health", false)',host)
        self.assertIn("espStyle, espHealth);",host)
        self.assertIn("public boolean espShowHealth()",host)
        self.assertIn("boolean health=host.espShowHealth();",renderer)
        self.assertIn("EspHealthBarGeometry.emit(xx,yy,zz,p.height,p.width,",renderer)
        self.assertIn("p.getHealth(),p.getMaxHealth()",renderer)
        self.assertIn("if(!good) host.diagnostics().record(Counter.ESP_INVALID_GEOMETRY)",renderer)
        self.assertIn("GL11.glLineWidth(host.espLineWidth());",renderer)
        self.assertIn("Math.hypot(viewerX-x,viewerZ-z)",geometry)
        self.assertIn("sink.segment(false,fraction",geometry)
        self.assertIn("if(fraction>0) sink.segment(true,fraction",geometry)
        self.assertIn("EspHealthBarGeometry.class",jar)
        self.assertIn("dev.ghostfusion.tests.EspHealthBarTests",
                      content(".github/workflows/core-ci.yml"))

    def test_esp_runtime_diagnostics_and_malformed_player_screening(self):
        esp=(FORGE/"PlayerEspRenderer.java").read_text(encoding="utf-8")
        diagnostics=content("src/main/java/dev/ghostfusion/host/RuntimeDiagnostics.java")
        ui=(FORGE/"ForgeFeatureDriver.java").read_text(encoding="utf-8")
        for name in ("ESP_RENDER_FRAME","ESP_DRAWN_PLAYER",
                     "ESP_DRAWN_SKELETON","ESP_INVALID_GEOMETRY"):
            self.assertIn("Counter."+name,esp)
            self.assertIn(name,diagnostics)
            self.assertIn("Counter."+name,ui)
        self.assertIn("Double.isFinite(p.prevRenderYawOffset)",esp)
        self.assertIn("!Double.isFinite(p.height)",esp)
        self.assertIn("partialTicks<0f || partialTicks>1f",esp)
        self.assertIn("nearest.offer(player,player.getEntityId(),distance)",esp)
        self.assertIn("if(skeleton) host.diagnostics().record(Counter.ESP_DRAWN_SKELETON)",esp)
        self.assertIn("finally { GL11.glEnd(); }",esp)
        self.assertIn("int width=236, height=134;",ui)
        self.assertIn("dev.ghostfusion.tests.EspTelemetryTests",
                      content(".github/workflows/core-ci.yml"))

    def test_original_skeleton_esp_mode_is_wired_to_forge(self):
        esp=(FORGE/"PlayerEspRenderer.java").read_text(encoding="utf-8")
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        policy=content("src/main/java/dev/ghostfusion/features/EspSkeletonGeometry.java")
        modes=content("src/main/java/dev/ghostfusion/features/EspRenderMode.java")
        self.assertIn('"skeleton", "box_skeleton"',host)
        self.assertIn("EspRenderMode.of(host.espStyle())",esp)
        self.assertIn("mode.skeleton",esp)
        self.assertIn("EspSkeletonGeometry.emit(xx,yy,zz,yaw",esp)
        self.assertIn("p.prevRenderYawOffset,p.renderYawOffset,partialTicks",esp)
        self.assertIn("GL11.glBegin(GL11.GL_LINES)",esp)
        self.assertIn("finally { GL11.glEnd(); }",esp)
        self.assertIn("Validate geometry BEFORE admission",esp)
        self.assertLess(esp.index("if(!renderable(player,partialTicks,skeleton)) continue;"),
                        esp.index("nearest.offer(player,player.getEntityId(),distance)"))
        self.assertLess(esp.index("nearest.offer(player,player.getEntityId(),distance)"),
                        esp.index("GL11.glColor4f(0.62f,0.56f,1.0f,0.82f)"))
        self.assertIn("Double.isFinite(x)",policy)
        self.assertIn('BOX_SKELETON("box_skeleton"',modes)
        self.assertIn("dev.ghostfusion.tests.EspSkeletonTests",content(".github/workflows/core-ci.yml"))

    def test_esp_nearest_80_owner_and_cleanup_wired(self):
        esp=(FORGE/"PlayerEspRenderer.java").read_text(encoding="utf-8")
        budget=content("src/main/java/dev/ghostfusion/features/EspNearestBudget.java")
        self.assertIn("new EspNearestBudget<EntityPlayer>(80)",esp)
        self.assertIn("double distance=mc.thePlayer.getDistanceSqToEntity(player);",esp)
        self.assertIn("nearest.offer(player,player.getEntityId(),distance)",esp)
        self.assertIn("for(int index=0;index<nearest.size();index++)",esp)
        self.assertIn("EntityPlayer p=nearest.get(index);",esp)
        self.assertGreaterEqual(esp.count("nearest.clear();"),2)
        self.assertIn("} finally {\n                nearest.clear();",esp)
        self.assertIn("private boolean renderable(EntityPlayer p,float partialTicks,boolean skeleton)",esp)
        self.assertIn("private static boolean finiteBox(AxisAlignedBB box)",esp)
        self.assertIn("private final Thread owner=Thread.currentThread()",budget)
        self.assertIn("players[i]=null",budget)
        self.assertIn("ids[at]=id",budget)
        self.assertIn("dev.ghostfusion.tests.EspNearestBudgetTests",
                      content(".github/workflows/core-ci.yml"))

    def test_esp_interpolated_geometry_is_finite(self):
        esp=(FORGE/"PlayerEspRenderer.java").read_text(encoding="utf-8")
        self.assertIn("Double.isFinite(xx)",esp)
        self.assertIn("Double.isFinite(yy)",esp)
        self.assertIn("Double.isFinite(zz)",esp)
        for field in ("minX","maxX","minY","maxY","minZ","maxZ"):
            self.assertIn("Double.isFinite(box."+field+")",esp)
        self.assertIn("Double.isFinite(rm.viewerPosX)",esp)

    def test_render_and_mouse_are_fenced_by_live_identity(self):
        tracker=content("src/main/java/dev/ghostfusion/services/WorldIdentityTracker.java")
        self.assertIn("public boolean matches(Object currentWorld,Object currentPlayer)",tracker)
        self.assertIn("world==currentWorld && player==currentPlayer",tracker)
        # Mouse and 3D render still return immediately. The 2D overlay
        # now suspends timing BEFORE its own early return on identity loss.
        self.assertEqual(self.adapter.count(
            "if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;"),2)
        self.assertIn("if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) {",self.adapter)
        self.assertIn("host.performancePause();\n            return;",self.adapter)
        self.assertIn("features.render3d(event.partialTicks)",self.adapter)
        self.assertIn("features.mouseInput()",self.adapter)
        self.assertRegex(self.adapter,r"final boolean status\s*=\s*host\.render2d\(\);")

    def test_gui_hitboxes_match_visible_rows(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        model=content("src/main/java/dev/ghostfusion/ui/GuiHitZones.java")
        for call in ("GuiHitZones.moduleIndex(", "GuiHitZones.settingIndex(",
                     "GuiHitZones.moduleArea(", "GuiHitZones.settingsArea(",
                     "GuiHitZones.visibleSettings("):
            self.assertIn(call,gui)
        self.assertIn("row>=g.visibleRows",model)
        self.assertIn("offset%28>=25",model)
        self.assertIn("(py-top)%22>=19",model)
        self.assertIn("if (button != 0 || !GuiChrome.interactive(zone) ||",gui)
        self.assertIn("!inside(mx,my,x,y,w,h)) return;",gui)
        self.assertNotIn("int idx = scroll + (my-listY)/28;",gui)

    def test_clickgui_header_and_sidebar_chrome_are_bounded(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        chrome=content("src/main/java/dev/ghostfusion/ui/GuiChrome.java")
        self.assertIn("GuiChrome.interactive(",gui)
        self.assertIn("GuiChrome.headerAt(zone,mx,my)",gui)
        self.assertIn("GuiChrome.categoryIndex(zone,mx,my)",gui)
        self.assertIn("GuiChrome.interactive(GuiLayout.calculate(width,height,ui.layout()))",gui)
        self.assertIn("hudDragging=false;",gui)
        self.assertIn("capture=null;",gui)
        self.assertIn("g.width >= g.sidebar + 230",chrome)
        self.assertIn("g.height >= 250",chrome)
        self.assertNotIn("g.width >= 445",chrome)
        self.assertNotIn("inside(mx,my,x+4,y+63,side-8,6*23)",gui)

    def test_compact_and_filtered_selection_are_live_in_forge(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        selection=content("src/main/java/dev/ghostfusion/ui/GuiSelection.java")
        self.assertIn("GuiSelection.reconcile(selected,filtered)",gui)
        self.assertIn("if (selected == null) {",gui)
        self.assertIn('label("No matching modules"',gui)
        self.assertIn("if (selected == null) return;",gui)
        self.assertIn("entries(); // immediately invalidate settings/key capture",gui)
        self.assertIn("entries(); // reject stale selected settings",gui)
        self.assertIn("if (visible.isEmpty()) return null;",selection)
        ci=content(".github/workflows/core-ci.yml")
        self.assertIn("dev.ghostfusion.tests.CompactGuiTests",ci)

    def test_core_module_fault_never_retains_private_messages(self):
        module=content("src/main/java/dev/ghostfusion/core/ModuleRegistry.java")
        self.assertNotIn("cause.getMessage()",module)
        self.assertIn('e.lastFault = type.length() > 60',module)
        ci=content(".github/workflows/core-ci.yml")
        self.assertIn("dev.ghostfusion.tests.FaultPrivacyTests",ci)

    def test_reserved_f7_cannot_be_bound_or_loaded_from_profile(self):
        registry=content("src/main/java/dev/ghostfusion/core/ModuleRegistry.java")
        reserved=content("src/main/java/dev/ghostfusion/core/ReservedKeys.java")
        loader=content("src/main/java/dev/ghostfusion/core/ProfileStore.java")
        gate=content("src/main/java/dev/ghostfusion/features/PhysicalInputGate.java")
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        self.assertIn("public static final int CLICK_GUI = 65",reserved)
        self.assertIn("ReservedKeys.reserved(key)",registry)
        router=content("src/main/java/dev/ghostfusion/core/InputRouter.java")
        self.assertIn("if (key == 0 || ReservedKeys.reserved(key)) return;",router)
        self.assertIn("ModuleRegistry.validateKey(stage.key)",loader)
        self.assertIn("ReservedKeys.CLICK_GUI",gate)
        self.assertIn("if (keyCode == Keyboard.KEY_F7) {",gui)
        self.assertIn("mc.displayGuiScreen(null);",gui)
        ci=content(".github/workflows/core-ci.yml")
        self.assertIn("dev.ghostfusion.tests.ReservedKeyTests",ci)

    def test_multi_widget_hud_positions_wired_to_forge(self):
        driver=(FORGE/"ForgeFeatureDriver.java").read_text(encoding="utf-8")
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        state=content("src/main/java/dev/ghostfusion/ui/UiState.java")
        store=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        positions=content("src/main/java/dev/ghostfusion/ui/HudPositions.java")
        for widget in ("KEYSTROKES","CPS","TARGET"):
            self.assertIn("Widget."+widget,driver)
            self.assertRegex(positions, r"(?m)^\s*"+widget+r'\("')
        self.assertGreaterEqual(driver.count(".widgets().pixelX("),3)
        self.assertGreaterEqual(driver.count(".widgets().pixelY("),3)
        self.assertIn("draggingWidget=widget;",gui)
        self.assertIn("geometry.moveWidget(ui.widgets(),draggingWidget,",gui)
        self.assertIn("for(Widget widget:Widget.values())",gui)
        self.assertIn("public HudPositions widgets() { return widgets; }",state)
        self.assertIn('p.setProperty("schema", "1")',store)
        self.assertIn("HudPositions.property(widget,true)",store)
        self.assertIn("HudPositions.property(widget,false)",store)
        self.assertIn("final double[][] positions",store)
        self.assertIn("dev.ghostfusion.tests.HudPlacementTests",
                      content(".github/workflows/core-ci.yml"))

    def test_legacy_status_hud_storage_remains_backward_compatible(self):
        store=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        state=content("src/main/java/dev/ghostfusion/ui/UiState.java")
        self.assertIn('p.getProperty("hud.x")',store)
        self.assertIn('p.getProperty("hud.y")',store)
        self.assertIn('p.setProperty("hud.x"',store)
        self.assertIn('p.setProperty("hud.y"',store)
        self.assertIn("public void position(double x, double y)",state)

    def test_hud_preview_uses_screen_aspect_and_shared_drag_geometry(self):
        preview=content("src/main/java/dev/ghostfusion/ui/HudPreviewGeometry.java")
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        self.assertIn("Math.min((double)panelWidth/scaledScreenWidth",preview)
        self.assertIn("Math.floor(scaledScreenWidth*scale)",preview)
        self.assertIn("Math.floor(scaledScreenHeight*scale)",preview)
        self.assertIn("canvas.x+anchors.pixelX",preview)
        self.assertIn("canvas.y+anchors.pixelY",preview)
        self.assertIn("anchors.positionPixels(widget,mouseX-canvas.x-grabX",preview)
        self.assertIn("ui.positionPixels(mouseX-canvas.x-grabX",preview)
        self.assertIn("new HudPreviewGeometry(previewX,previewY,previewW,previewH,width,height)",gui)
        self.assertIn("Rect canvas=geometry.canvas",gui)
        self.assertIn("if (!geometry.canvas.contains(mx,my)) return;",gui)
        self.assertIn("geometry.moveStatus(ui,statusPreviewWidth(),statusPreviewHeight()",gui)
        self.assertIn("statusPreviewWidth()",gui)
        self.assertIn("dev.ghostfusion.tests.HudPreviewTests",
                      content(".github/workflows/core-ci.yml"))
        self.assertNotIn("ui.widgets().positionPixels(draggingWidget,",gui)

    def test_accessible_hud_editor_and_actual_feature_footprints(self):
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        driver=(FORGE/"ForgeFeatureDriver.java").read_text(encoding="utf-8")
        editor=content("src/main/java/dev/ghostfusion/ui/HudEditorControls.java")
        geometry=content("src/main/java/dev/ghostfusion/ui/HudPreviewGeometry.java")
        sizes=content("src/main/java/dev/ghostfusion/ui/HudFootprints.java")
        for item in ("STATUS","KEYS","CPS","TARGET"):
            self.assertIn(item,editor)
        for name in ("KEYSTROKES","TARGET"):
            self.assertIn("HudFootprints.height(Widget."+name,driver)
        self.assertIn("HudFootprints.height(widget,host.keysJump()",gui)
        self.assertIn("HudEditorControls.tabAt(",gui)
        self.assertIn("hudControls.nudge(ui,",gui)
        self.assertIn("hudControls.reset(ui)",gui)
        self.assertIn("hudControls.select(picked)",gui)
        self.assertIn("Keyboard.KEY_LEFT",gui)
        self.assertIn("Keyboard.KEY_RIGHT",gui)
        self.assertIn("Keyboard.KEY_UP",gui)
        self.assertIn("Keyboard.KEY_DOWN",gui)
        self.assertIn("Keyboard.KEY_LSHIFT",gui)
        self.assertIn("Keyboard.KEY_RSHIFT",gui)
        self.assertIn("geometry.moveWidget(ui.widgets(),draggingWidget",gui)
        self.assertIn("geometry.widget(ui.widgets(),widget,hudWidth(widget),hudHeight(widget))",gui)
        self.assertIn("widget(anchors,widget,actualWidth,actualHeight)",geometry)
        self.assertIn("moveWidget(anchors,widget,mouseX,mouseY,grabX,grabY,widget.width,widget.height)",geometry)
        self.assertIn("case KEYSTROKES: return 37+(jump?20:0)+(sneak?20:0);",sizes)
        self.assertIn("case TARGET: return targetHealth?43:29;",sizes)
        self.assertIn("dev.ghostfusion.tests.HudEditorControlsTests",
                      content(".github/workflows/core-ci.yml"))

    def test_status_metrics_layout_and_independent_fault_isolation(self):
        adapter=self.adapter
        gui=(FORGE/"GhostClickGui.java").read_text(encoding="utf-8")
        layout=content("src/main/java/dev/ghostfusion/ui/StatusHudLayout.java")
        self.assertIn("StatusHudLayout.calculate(host.ui(),sw,sh,",adapter)
        self.assertIn('host.runFeature("hud.status",new Runnable()',adapter)
        self.assertIn('host.runFeature("hud.metrics",new Runnable()',adapter)
        self.assertIn("layout.y+layout.fpsY",adapter)
        self.assertIn("layout.y+layout.coordinatesY",adapter)
        self.assertIn("trimStringToWidth(statusText,allowed)",adapter)
        self.assertIn("StatusHudLayout.calculate(ui,width,height",gui)
        self.assertIn("statusFootprint().measuredWidth",gui)
        self.assertIn("statusFootprint().measuredHeight",gui)
        self.assertIn("Math.max(w,(long)fpsWidth+2)",layout)
        self.assertIn("Math.max(w,(long)coordinatesWidth+2)",layout)
        self.assertIn("ui.pixelX(viewportWidth,visibleW)",layout)
        self.assertIn("ui.pixelY(viewportHeight,visibleH)",layout)
        self.assertIn("dev.ghostfusion.tests.StatusHudLayoutTests",
                      content(".github/workflows/core-ci.yml"))

    def test_config_integrity_and_startup_recovery_wired(self):
        profile=content("src/main/java/dev/ghostfusion/core/ProfileStore.java")
        ui=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        host=content("src/main/java/dev/ghostfusion/host/HostRuntime.java")
        integrity=content("src/main/java/dev/ghostfusion/core/ConfigIntegrity.java")
        self.assertIn('public static final String FIELD = "integrity.sha256"',integrity)
        self.assertIn("ConfigIntegrity.seal(data)",profile)
        self.assertIn("ConfigIntegrity.seal(p)",ui)
        self.assertIn("ConfigIntegrity.read(source)",profile)
        self.assertIn("ConfigIntegrity.read(file)",ui)
        self.assertIn("public boolean loadRecovering(String name)",profile)
        self.assertIn("public boolean loadRecovering()",ui)
        self.assertIn("ConfigIntegrity.restoreBackup(primary,",profile)
        self.assertIn("ConfigIntegrity.restoreBackup(primary,",ui)
        self.assertIn("if(uiStore.loadRecovering())",host)
        self.assertIn('if(profiles.loadRecovering("default"))',host)
        self.assertIn("dev.ghostfusion.tests.ConfigRecoveryTests",
                      content(".github/workflows/core-ci.yml"))
        self.assertIn("ConfigIntegrity.seal(lifecycle)",
                      content("src/test/java/dev/ghostfusion/tests/CoreTests.java"))

    def test_verified_staged_backup_rotation_replaces_unconditional_copy(self):
        core=content("src/main/java/dev/ghostfusion/core/ConfigIntegrity.java")
        profile=content("src/main/java/dev/ghostfusion/core/ProfileStore.java")
        ui=content("src/main/java/dev/ghostfusion/ui/UiSettingsStore.java")
        self.assertIn("public static boolean rotateVerifiedBackup(Path primary)",core)
        self.assertIn("Files.copy(primary,staged,StandardCopyOption.REPLACE_EXISTING)",core)
        self.assertIn("Properties p=read(staged)",core)
        self.assertIn("!verify(p)",core)
        self.assertIn("Files.move(staged,backup,StandardCopyOption.ATOMIC_MOVE",core)
        self.assertIn("ConfigIntegrity.rotateVerifiedBackup(destination,",profile)
        self.assertIn("ConfigIntegrity.rotateVerifiedBackup(file,",ui)
        self.assertNotIn("Files.copy(destination, destination.resolveSibling",profile)
        self.assertNotIn("Files.copy(file, directory.resolve(\"ui.properties.bak\")",ui)
        self.assertIn("dev.ghostfusion.tests.BackupRotationTests",
                      content(".github/workflows/core-ci.yml"))

    def test_forge_project_version_matches_mod_annotation(self):
        gradle=content("forge189/build.gradle")
        match=re.search(r"(?m)^version = '([0-9]+\.[0-9]+\.[0-9]+)'",gradle)
        self.assertIsNotNone(match)
        mod=(FORGE/"GhostCMod.java").read_text(encoding="utf-8")
        self.assertIn('public static final String CLIENT_VERSION = "'+match.group(1)+'"',mod)
        self.assertIn('version = GhostCMod.CLIENT_VERSION',mod)
        self.assertIn('GhostCMod.CLIENT_VERSION',self.adapter)
        self.assertIn('GhostCMod.CLIENT_VERSION',self.feature)
        info=content("forge189/src/main/resources/mcmod.info")
        self.assertIn('"version": "'+match.group(1)+'"',info)


if __name__ == "__main__":
    unittest.main(verbosity=2)
