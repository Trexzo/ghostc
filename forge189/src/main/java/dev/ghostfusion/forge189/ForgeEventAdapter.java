package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionReadout;
import dev.ghostfusion.features.LiveAcceptanceCoverage;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiChest;
import org.lwjgl.opengl.Display;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.services.WorldIdentityTracker;
import dev.ghostfusion.services.WorldSnapshotAdmission;
import dev.ghostfusion.services.SnapshotDemand;
import dev.ghostfusion.features.EspTeamPolicy;
import dev.ghostfusion.features.TargetHudTeamPin;
import dev.ghostfusion.forge189.ForgePingReadout;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.host.SnapshotTelemetry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.ScaledResolution;
import java.util.Locale;

/**
 * All Forge/LWJGL concerns terminate here; no Minecraft or obfuscated types in the core.
 * Must be registered on the Forge event bus and FML common bus.
 */
public final class ForgeEventAdapter {
    private final Minecraft minecraft;
    private final HostRuntime host;
    private final ForgeFeatureDriver features;
    private final ForgeCombatController combat;
    private final ForgeBridgeAssistController bridge;
    private final ForgeVelocityController velocity;
    private final ForgeWTapController wtap;
    private final ForgeChestStealerController chestStealer;
    private final ForgeInventoryManagerController inventoryManager;
    private final ForgeAutoArmorController autoArmor;
    // The single owner-thread arbiter decides which modules may submit
    // native actions. Render-only modules remain independent.
    private final ModuleInteractionStateMachine moduleState =
        new ModuleInteractionStateMachine();
    private ModuleInteractionStateMachine.Frame lastArbitration;
    // Passive evidence is recorded ONLY with explicit F7 Diagnostics opt-in.
    // No game entity, packet object or channel reference is retained.
    private final LiveAcceptanceCoverage liveAcceptance =
        new LiveAcceptanceCoverage();
    private final WorldIdentityTracker worldIdentity = new WorldIdentityTracker();
    // Two capped, owner-thread groups are reused every tick. Every raw
    // EntityPlayer reference is released before publishing an immutable
    // WorldSnapshot, including when snapshot construction fails.
    private final WorldSnapshotAdmission<EntityPlayer> nearestSnapshot =
        new WorldSnapshotAdmission<EntityPlayer>();
    // Holds only a primitive distance/ID: no game entity references survive
    // snapshot admission, even on early exit or exception.
    private final TargetHudTeamPin teamHudPin = new TargetHudTeamPin();

    public ForgeEventAdapter(Minecraft minecraft, HostRuntime host) {
        if (minecraft == null || host == null) throw new IllegalArgumentException("null dependency");
        this.minecraft = minecraft;
        this.host = host;
        this.features = new ForgeFeatureDriver(minecraft,host);
        this.combat = new ForgeCombatController(minecraft,host);
        this.bridge = new ForgeBridgeAssistController(minecraft,host);
        this.velocity = new ForgeVelocityController(minecraft,host);
        this.wtap = new ForgeWTapController(minecraft,host);
        this.chestStealer = new ForgeChestStealerController(minecraft,host);
        this.inventoryManager = new ForgeInventoryManagerController(minecraft,host);
        this.autoArmor = new ForgeAutoArmorController(minecraft,host);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WorldIdentityTracker.Transition transition =
            worldIdentity.observe(minecraft.theWorld,minecraft.thePlayer);
        final boolean worldReady = worldIdentity.ready();
        final boolean focusBlocked = minecraft.currentScreen != null || !minecraft.inGameHasFocus;
        host.inputFocus(focusBlocked);
        // A world/dimension swap or player recreation can happen without a
        // null frame. Explicitly close the old generation before new admission.
        if (transition == WorldIdentityTracker.Transition.SWITCH ||
            transition == WorldIdentityTracker.Transition.LEAVE) {
            host.worldPresence(false);
            moduleState.reset();
            lastArbitration=null;
            features.reset();
            combat.reset();
            bridge.reset();
            velocity.reset();
            wtap.reset();
            chestStealer.reset();
            inventoryManager.reset();
            autoArmor.reset();
        }
        if (transition == WorldIdentityTracker.Transition.ENTER ||
            transition == WorldIdentityTracker.Transition.SWITCH) {
            host.worldPresence(true);
            moduleState.reset();
            lastArbitration=null;
            features.reset();
            combat.reset();
            bridge.reset();
            velocity.reset();
            wtap.reset();
            chestStealer.reset();
            inventoryManager.reset();
            autoArmor.reset();
        }
        // Keep the standard presence reconciliation for starting menus.
        host.worldPresence(worldReady);
        host.tick();
        if (!worldReady || focusBlocked) {
            features.reset();
            bridge.reset();
            velocity.reset();
            wtap.reset();
            chestStealer.reset();
            inventoryManager.reset();
            autoArmor.reset();
        }
        // Read-only player snapshot published before the single combat intent bridge runs.
        if (host.services().connected() && minecraft.theWorld != null && minecraft.thePlayer != null &&
            WorldSnapshot.validPlayerPose(minecraft.thePlayer.posX,minecraft.thePlayer.posY,
                minecraft.thePlayer.posZ,minecraft.thePlayer.rotationYaw,minecraft.thePlayer.rotationPitch)) {
            final SnapshotDemand demand=SnapshotDemand.of(
                host.featureEnabled("hud.target"),host.targetVisible(),host.targetRange(),
                host.featureEnabled("combat.aimassist"),host.aimVisibleOnly(),host.aimRange());
            // Only an enabled crosshair HUD may reserve one optional slot.
            // Reject stale reticle references before scanning playerEntities.
            EntityPlayer crosshairPlayer=null;
            if(host.featureEnabled("hud.target") &&
               "crosshair".equals(host.targetSelection())) {
                MovingObjectPosition hit=minecraft.objectMouseOver;
                if(hit!=null &&
                   hit.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY &&
                   hit.entityHit instanceof EntityPlayer &&
                   hit.entityHit!=minecraft.thePlayer) {
                    EntityPlayer candidate=(EntityPlayer)hit.entityHit;
                    if(minecraft.theWorld.getEntityByID(candidate.getEntityId())==candidate)
                        crosshairPlayer=candidate;
                }
            }
            final EntityPlayer pinnedCrosshair=crosshairPlayer;
            // Crosshair and filtered-nearest are mutually exclusive modes.
            // Reuse the one existing optional snapshot pin, never a 514th slot.
            final boolean pinNearestEnemy=host.featureEnabled("hud.target") &&
                host.targetIgnoreTeammates() && "nearest".equals(host.targetSelection());
            final boolean pinVisibleOnly=pinNearestEnemy && host.targetVisible();
            final double pinRangeSq=pinNearestEnemy ?
                host.targetRange()*host.targetRange() : 0.0;
            final boolean profileSnapshot=host.snapshotProfilingEnabled();
            final long snapshotStart=profileSnapshot?System.nanoTime():0L;
            int scanned=0,passed=0,rays=0;
            nearestSnapshot.clear();
            teamHudPin.clear();
            try {
                // No targets: publish an empty valid tick snapshot so unrelated
                // click/hit/block policies keep their exact session admission.
                // Any active target range is applied BEFORE expensive LOS rays.
                if(demand.needed()) for (Object raw : minecraft.theWorld.playerEntities) {
                    if (!(raw instanceof EntityPlayer)) continue;
                    EntityPlayer p = (EntityPlayer)raw;
                    if(profileSnapshot)scanned++;
                    if (p == minecraft.thePlayer || p.isDead) continue;
                    double health = p.getHealth();
                    if (!WorldSnapshot.finitePublic(health) || health<=0 ||
                        !WorldSnapshot.finitePublic(p.posX) ||
                        !WorldSnapshot.finitePublic(p.posY) ||
                        !WorldSnapshot.finitePublic(p.posZ)) continue;
                    double dx=p.posX-minecraft.thePlayer.posX;
                    double dy=p.posY-minecraft.thePlayer.posY;
                    double dz=p.posZ-minecraft.thePlayer.posZ;
                    double distanceSq=dx*dx+dy*dy+dz*dz;
                    if(!demand.accepts(distanceSq))continue;
                    if(profileSnapshot)passed++;
                    // With unfiltered-only targeting, avoid every LOS ray and
                    // use the bounded hidden partition as a generic priority
                    // list; consumers explicitly ignore the visible flag.
                    boolean visible=false;
                    if(demand.testVisibility(distanceSq) && !p.isInvisible()) {
                        if(profileSnapshot)rays++;
                        visible=minecraft.thePlayer.canEntityBeSeen(p);
                    }
                    if(!demand.admit(visible,distanceSq))continue;
                    if(p==pinnedCrosshair)
                        nearestSnapshot.pin(p,p.getEntityId(),distanceSq,visible);
                    // Without a pin, 256 nearer allied players can fill the
                    // visible snapshot partition and hide an enemy from the
                    // opt-in Target HUD nearest non-teammate selection.
                    // Choose one closest live enemy independently of the
                    // shared nearest-256 budgets, after demand/LOS admission.
                    if(pinNearestEnemy &&
                        minecraft.theWorld.getEntityByID(p.getEntityId())==p &&
                        !EspTeamPolicy.exclude(minecraft.thePlayer.getTeam(),p.getTeam()) &&
                        teamHudPin.offer(p.getEntityId(),distanceSq,visible,
                            pinVisibleOnly,pinRangeSq)) {
                        nearestSnapshot.pin(p,p.getEntityId(),distanceSq,visible);
                    }
                    nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible);
                }
                final List<WorldSnapshot.Entity> players =
                    new ArrayList<WorldSnapshot.Entity>(nearestSnapshot.sizeWithPinned());
                nearestSnapshot.forEachWithPinned(new WorldSnapshotAdmission.Visitor<EntityPlayer>() {
                    public void visit(EntityPlayer p,boolean visible) {
                        // Reject a candidate that changed between admission and
                        // publication, instead of failing the whole client tick.
                        double health=p.getHealth();
                        if(p.isDead || !WorldSnapshot.finitePublic(health) || health<=0 ||
                           !WorldSnapshot.finitePublic(p.posX) ||
                           !WorldSnapshot.finitePublic(p.posY) ||
                           !WorldSnapshot.finitePublic(p.posZ) ||
                           minecraft.theWorld.getEntityByID(p.getEntityId())!=p)return;
                        double dx=p.posX-minecraft.thePlayer.posX;
                        double dy=p.posY-minecraft.thePlayer.posY;
                        double dz=p.posZ-minecraft.thePlayer.posZ;
                        double currentDistanceSq=dx*dx+dy*dy+dz*dz;
                        boolean stillVisible=visible && !p.isInvisible();
                        if(!demand.admit(stillVisible,currentDistanceSq))return;
                        players.add(new WorldSnapshot.Entity(
                            p.getEntityId(),p.posX,p.posY,p.posZ,health,true,stillVisible,true));
                    }
                });
                host.diagnostics().record(Counter.SNAPSHOT);
                host.services().publish(new WorldSnapshot(
                    host.services().generation(),host.services().tick(),
                    minecraft.thePlayer.posX,minecraft.thePlayer.posY,minecraft.thePlayer.posZ,
                    minecraft.thePlayer.rotationYaw,minecraft.thePlayer.rotationPitch,players));
                // A clock anomaly must not invalidate an already published
                // client-tick snapshot or stop normal gameplay callbacks.
                if(profileSnapshot) {
                    long elapsed=SnapshotTelemetry.elapsedNanos(snapshotStart,System.nanoTime());
                    if(elapsed>=0)host.recordSnapshotWork(
                        elapsed,scanned,passed,rays,players.size(),
                        demand.needVisible,demand.needUnfiltered);
                }
            } finally {
                nearestSnapshot.clear();
                teamHudPin.clear();
            }
        }
        // A single session-fenced, immutable plan arbitrates native
        // actions before any module makes a write. Controllers preserve
        // their own physical input/entity/container and safety checks.
        ModuleInteractionStateMachine.Scene scene =
            !worldReady ? ModuleInteractionStateMachine.Scene.NO_WORLD :
            minecraft.currentScreen==null ?
                ModuleInteractionStateMachine.Scene.GAME :
            minecraft.currentScreen instanceof GuiInventory ?
                ModuleInteractionStateMachine.Scene.PLAYER_INVENTORY :
            minecraft.currentScreen instanceof GuiChest ?
                ModuleInteractionStateMachine.Scene.CHEST :
                ModuleInteractionStateMachine.Scene.OTHER_GUI;
        boolean combatEnabled =
            host.featureEnabled("combat.aimassist") ||
            host.featureEnabled("combat.clicker") ||
            host.featureEnabled("combat.blockhit") ||
            host.featureEnabled("combat.hitselect");
        ModuleInteractionStateMachine.Enabled enabled =
            new ModuleInteractionStateMachine.Enabled(
                combatEnabled,host.featureEnabled("movement.bridgeassist"),
                host.featureEnabled("combat.velocity"),host.featureEnabled("combat.wtap"),
                host.featureEnabled("movement.sprint"),
                host.featureEnabled("inventory.autoarmor"),
                host.featureEnabled("inventory.manager"),
                host.featureEnabled("inventory.cheststealer"));
        ModuleInteractionStateMachine.Frame plan=moduleState.advance(
            host.services().generation(),host.services().tick(),
            worldReady && host.services().connected(),
            scene,Display.isActive() &&
                (scene!=ModuleInteractionStateMachine.Scene.GAME ||
                 minecraft.inGameHasFocus),
            enabled,wtap.sprintReserved());

        // Diagnostic counters are opt-in grant-frame counts, not claimed
        // native actions or server transaction confirmations.
        if(host.diagnosticsEnabled()) {
            lastArbitration=plan;
            if(plan.denial(ModuleInteractionStateMachine.Resource.AUTO_SPRINT)==
                ModuleInteractionStateMachine.Denial.WTAP_RESERVED)
                host.diagnostics().record(Counter.MODULE_WTAP_PREEMPTED_SPRINT);
            if(plan.denial(ModuleInteractionStateMachine.Resource.INVENTORY_MANAGER)==
                ModuleInteractionStateMachine.Denial.ARMOR_PRIORITY)
                host.diagnostics().record(Counter.MODULE_ARMOR_PREEMPTED_SORTER);
            if(plan.autoArmor)host.diagnostics().record(Counter.MODULE_ARMOR_GRANT);
            if(plan.inventoryManager)host.diagnostics().record(Counter.MODULE_SORTER_GRANT);
            if(plan.chestStealer)host.diagnostics().record(Counter.MODULE_CHEST_GRANT);
            boolean gameplayRequested=enabled.combat||enabled.bridge||
                enabled.velocity||enabled.wtap||enabled.sprint;
            if(gameplayRequested && worldReady &&
               scene!=ModuleInteractionStateMachine.Scene.GAME)
                host.diagnostics().record(Counter.MODULE_GAMEPLAY_GUI_DENIED);
            if(gameplayRequested && worldReady &&
               scene==ModuleInteractionStateMachine.Scene.GAME &&
               (!Display.isActive() || !minecraft.inGameHasFocus))
                host.diagnostics().record(Counter.MODULE_GAMEPLAY_WINDOW_DENIED);
        } else lastArbitration=null;

        // AutoSprint is intentionally silent while WTap holds the sprint
        // resource after a real attack. WTap alone can restore sprint.
        features.tick(plan.autoSprint);
        if(plan.combat)combat.tick();else combat.reset();
        if(plan.bridge)bridge.tick();else bridge.reset();
        if(plan.velocity)velocity.tick();else velocity.reset();
        if(plan.wtap)wtap.tick();else wtap.reset();

        // Exactly one inventory controller may observe/write per GUI tick.
        // Releasing a controller resets its pending transfer; no hidden
        // sorter click may interrupt a staged AutoArmor upgrade.
        if(plan.chestStealer)chestStealer.tick();else chestStealer.reset();
        if(plan.inventoryManager)inventoryManager.tick();else inventoryManager.reset();
        if(plan.autoArmor)autoArmor.tick();else autoArmor.reset();

        // Sample AFTER native controllers execute, so hook attachment and
        // packet counters reflect this actual session's client-side state.
        if(host.diagnosticsEnabled()) {
            ForgeVelocityPacketBridge.Health health=velocity.health();
            int newly=liveAcceptance.observe(
                plan,health.status,health.localS12,health.explosionS27);
            for(LiveAcceptanceCoverage.Check check:
                LiveAcceptanceCoverage.Check.values())
                if((newly & LiveAcceptanceCoverage.bit(check))!=0)
                    host.diagnostics().record(Counter.valueOf(
                        "ACCEPT_"+check.name()));
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        // This is a native combat event, not merely a queued click intent.
        // World/player identity is checked at the native event boundary.
        if(event.entityPlayer!=minecraft.thePlayer ||
           !worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;
        wtap.attack(event.target);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        host.diagnostics().record(Counter.KEY_CALLBACK);
        int code = Keyboard.getEventKey();
        // Event code 0 means no keyboard key; no keybind should fire.
        if (code == 0) return;

        // F6 Panic/Disable All, with immediate release of owned Bridge Assist
        // Sneak state. The same physical key can remain pressed by the player.
        if (code == Keyboard.KEY_F6) {
            if (Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent() &&
                minecraft.currentScreen == null && minecraft.inGameHasFocus &&
                worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) {
                PanicController.disableAll(host);
                moduleState.reset();
                lastArbitration=null;
                bridge.reset();
                velocity.reset();
                wtap.reset();
                chestStealer.reset();
                inventoryManager.reset();
                autoArmor.reset();
                features.reset();
                combat.reset();
            }
            return;
        }
        if (code == Keyboard.KEY_F7 && Keyboard.getEventKeyState() &&
            !Keyboard.isRepeatEvent() && minecraft.currentScreen == null && minecraft.inGameHasFocus) {
            host.inputFocus(true);
            host.diagnostics().record(Counter.GUI_OPEN);
            minecraft.displayGuiScreen(new GhostClickGui(host));
            return;
        }
        // Suppress module hotkeys when chat, text entry or any GUI has focus.
        host.key(code, Keyboard.getEventKeyState(), minecraft.currentScreen != null || !minecraft.inGameHasFocus);
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        host.diagnostics().record(Counter.MOUSE_CALLBACK);
        if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;
        features.mouseInput();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        host.diagnostics().record(Counter.WORLD_RENDER_FRAME);
        if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;
        features.render3d(event.partialTicks);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        host.diagnostics().record(Counter.OVERLAY_FRAME);
        if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) {
            host.performancePause();
            return;
        }
        if(host.diagnosticsEnabled()) {
            final Runtime memory=Runtime.getRuntime();
            host.performanceFrame(System.nanoTime(),memory.totalMemory()-memory.freeMemory(),
                minecraft.currentScreen==null && minecraft.inGameHasFocus);
        } else {
            host.performancePause();
        }
        final boolean status=host.render2d();
        final boolean metrics=host.metricsFrameRequested();
        final ScaledResolution resolution=event.resolution;
        final int sw=resolution.getScaledWidth(),sh=resolution.getScaledHeight();
        final int fontHeight=minecraft.fontRendererObj.FONT_HEIGHT;
        final float scale=host.statusScale();
        final String statusText=host.detailedStatus()
            ? "GhostC " + GhostCMod.CLIENT_VERSION + " | F7 ClickGUI" : "GhostC";
        final String fpsText=metrics && host.showFps()
            ? "FPS " + Minecraft.getDebugFPS() : null;
        final String coordsText=metrics && host.showCoordinates() && minecraft.thePlayer!=null
            ? String.format(Locale.ROOT,"XYZ %.1f %.1f %.1f",
                minecraft.thePlayer.posX,minecraft.thePlayer.posY,minecraft.thePlayer.posZ) : null;
        // Read the existing local tab-list response time only if opted in.
        final String pingText=metrics && host.showPing()
            ? ForgePingReadout.read(minecraft) : null;
        int warningStatusX=0,warningStatusBottom=2;
        if(status || fpsText!=null || coordsText!=null || pingText!=null) {
            final int statusWidth=status ? Math.max(1,
                Math.round((minecraft.fontRendererObj.getStringWidth(statusText)+4)*scale)) : 0;
            final int statusHeight=status ? Math.max(1,
                Math.round((fontHeight+5)*scale)) : 0;
            final StatusHudLayout layout=StatusHudLayout.calculate(host.ui(),sw,sh,
                status,fpsText!=null,coordsText!=null,pingText!=null,
                statusWidth,statusHeight,
                fpsText==null?0:minecraft.fontRendererObj.getStringWidth(fpsText),
                coordsText==null?0:minecraft.fontRendererObj.getStringWidth(coordsText),
                pingText==null?0:minecraft.fontRendererObj.getStringWidth(pingText),
                fontHeight);
            warningStatusX=layout.x;
            warningStatusBottom=layout.y+layout.height+3;

            // Independent fault domains: a bad Status draw cannot prevent FPS
            // or coordinates from rendering in the same overlay frame.
            if(status) host.runFeature("hud.status",new Runnable() {
                public void run() {
                    RenderStateGuard.draw(new Runnable() {
                        public void run() {
                            int allowed=Math.max(0,(int)((sw-layout.x-4)/scale));
                            String shown=minecraft.fontRendererObj.trimStringToWidth(statusText,allowed);
                            if(shown.isEmpty()) return;
                            GL11.glTranslatef(layout.x,layout.y,0);
                            GL11.glScalef(scale,scale,1);
                            if(host.statusShadow())
                                minecraft.fontRendererObj.drawStringWithShadow(shown,2,2,0xF2F2FF);
                            else minecraft.fontRendererObj.drawString(shown,2,2,0xF2F2FF);
                        }
                    });
                }
            });
            if(metrics && (fpsText!=null || coordsText!=null || pingText!=null))
                host.runFeature("hud.metrics",new Runnable() {
                    public void run() {
                        RenderStateGuard.draw(new Runnable() {
                            public void run() {
                                int available=Math.max(0,sw-layout.x-2);
                                if(fpsText!=null) {
                                    int py=layout.y+layout.fpsY;
                                    if(py>=0 && py+fontHeight<=sh) {
                                        String shown=minecraft.fontRendererObj.trimStringToWidth(
                                            fpsText,available);
                                        minecraft.fontRendererObj.drawStringWithShadow(
                                            shown,layout.x+2,py,0xFFB2CBFF);
                                    }
                                }
                                if(coordsText!=null) {
                                    int py=layout.y+layout.coordinatesY;
                                    if(py>=0 && py+fontHeight<=sh) {
                                        String shown=minecraft.fontRendererObj.trimStringToWidth(
                                            coordsText,available);
                                        minecraft.fontRendererObj.drawStringWithShadow(
                                            shown,layout.x+2,py,0xFFB2CBFF);
                                    }
                                }
                                if(pingText!=null) {
                                    int py=layout.y+layout.pingY;
                                    if(py>=0 && py+fontHeight<=sh) {
                                        String shown=minecraft.fontRendererObj.trimStringToWidth(
                                            pingText,available);
                                        if(!shown.isEmpty())
                                            minecraft.fontRendererObj.drawStringWithShadow(
                                                shown,layout.x+2,py,0xFFB2CBFF);
                                    }
                                }
                            }
                        });
                    }
                });
        }
        // Fault and recovery warnings remain visible when Status and Metrics
        // are disabled, using a screen-safe independent fallback anchor.
        String warning=!host.faultWarning().isEmpty() ? host.faultWarning() :
            !host.uiWarning().isEmpty() ? host.uiWarning() :
            !host.diagnosticsWarning().isEmpty() ? host.diagnosticsWarning() :
            host.profileWarning();
        if(!warning.isEmpty()) {
            final String notice=warning;
            final int nx=warningStatusX;
            final int ny=dev.ghostfusion.ui.HudWarningPlacement.y(
                warningStatusBottom,sh,fontHeight);
            RenderStateGuard.draw(new Runnable() {
                public void run() {
                    minecraft.fontRendererObj.drawStringWithShadow(
                        minecraft.fontRendererObj.trimStringToWidth(notice,
                            Math.max(0,sw-nx-2)),
                        nx+2,ny,0xFFFF9999);
                }
            });
        }
        // Only explicitly opted-in diagnostics display nonidentifying
        // immutable phase, ownership and suppression reason labels.
        if(host.diagnosticsEnabled() && lastArbitration!=null &&
           lastArbitration.generation==host.services().generation()) {
            final String grants=ModuleInteractionReadout.ownership(lastArbitration);
            final String held=ModuleInteractionReadout.suppression(lastArbitration);
            // Last client-side action outcome is not a server ACK or hit.
            final String outcome=host.nativeActionLatest();
            final String coverage=liveAcceptance.readout();
            final int bottomY=Math.max(2,sh-4*(fontHeight+3)-4);
            RenderStateGuard.draw(new Runnable() {
                public void run() {
                    int available=Math.max(0,sw-6);
                    minecraft.fontRendererObj.drawStringWithShadow(
                        minecraft.fontRendererObj.trimStringToWidth(grants,available),
                        3,bottomY,0xFFC2C9D4);
                    minecraft.fontRendererObj.drawStringWithShadow(
                        minecraft.fontRendererObj.trimStringToWidth(held,available),
                        3,bottomY+fontHeight+3,0xFFFFD08A);
                    minecraft.fontRendererObj.drawStringWithShadow(
                        minecraft.fontRendererObj.trimStringToWidth(outcome,available),
                        3,bottomY+2*(fontHeight+3),0xFFB0E4CA);
                    minecraft.fontRendererObj.drawStringWithShadow(
                        minecraft.fontRendererObj.trimStringToWidth(coverage,available),
                        3,bottomY+3*(fontHeight+3),0xFFC8D6EE);
                }
            });
        }
        // Other HUD modules retain their own independent fault guards.
        features.render2d(resolution);
    }

    /**
     * Network disconnect notifications may run off the Minecraft owner thread.
     * Never mutate HostRuntime or feature state from the FML network callback.
     * END client tick already handles world transitions; this notification only
     * finalizes the disconnect and optionally exports nonidentifying counters.
     */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        minecraft.addScheduledTask(new Runnable() {
            public void run() {
                if (host.isClosed()) return;
                // Ignore a stale queued disconnect after joining another world.
                if (minecraft.theWorld != null && minecraft.thePlayer != null) return;
                host.disconnect();
                moduleState.reset();
                lastArbitration=null;
                worldIdentity.reset();
                features.reset();
                combat.reset();
                bridge.reset();
                velocity.reset();
                wtap.reset();
                chestStealer.reset();
                inventoryManager.reset();
                autoArmor.reset();
            }
        });
    }
}
