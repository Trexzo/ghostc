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
        self.assertIn("Set<Integer> seenEntityIds",from_source)
        self.assertIn("snapshot=null;",services)
        self.assertIn("rotations.prune(tick,generation)",services)
        self.assertIn("snapshot()==null",combat)
        self.assertIn("session.snapshot().tick!=session.tick()",combat)
        self.assertIn("pitch>=-90f && pitch<=90f",snap)

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
        self.assertEqual(self.adapter.count(
            "if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;"),3)
        self.assertIn("features.render3d(event.partialTicks)",self.adapter)
        self.assertIn("features.mouseInput()",self.adapter)
        self.assertIn("final boolean status = host.render2d();",self.adapter)

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
