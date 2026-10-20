package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.services.WorldIdentityTracker;
import dev.ghostfusion.services.WorldSnapshotAdmission;
import dev.ghostfusion.services.SnapshotDemand;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import net.minecraft.entity.player.EntityPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
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
    private final WorldIdentityTracker worldIdentity = new WorldIdentityTracker();
    // Two capped, owner-thread groups are reused every tick. Every raw
    // EntityPlayer reference is released before publishing an immutable
    // WorldSnapshot, including when snapshot construction fails.
    private final WorldSnapshotAdmission<EntityPlayer> nearestSnapshot =
        new WorldSnapshotAdmission<EntityPlayer>();

    public ForgeEventAdapter(Minecraft minecraft, HostRuntime host) {
        if (minecraft == null || host == null) throw new IllegalArgumentException("null dependency");
        this.minecraft = minecraft;
        this.host = host;
        this.features = new ForgeFeatureDriver(minecraft,host);
        this.combat = new ForgeCombatController(minecraft,host);
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
            features.reset();
            combat.reset();
        }
        if (transition == WorldIdentityTracker.Transition.ENTER ||
            transition == WorldIdentityTracker.Transition.SWITCH) {
            host.worldPresence(true);
            features.reset();
            combat.reset();
        }
        // Keep the standard presence reconciliation for starting menus.
        host.worldPresence(worldReady);
        host.tick();
        if (!worldReady || focusBlocked) features.reset();
        // Read-only player snapshot published before the single combat intent bridge runs.
        if (host.services().connected() && minecraft.theWorld != null && minecraft.thePlayer != null &&
            WorldSnapshot.validPlayerPose(minecraft.thePlayer.posX,minecraft.thePlayer.posY,
                minecraft.thePlayer.posZ,minecraft.thePlayer.rotationYaw,minecraft.thePlayer.rotationPitch)) {
            final SnapshotDemand demand=SnapshotDemand.of(
                host.featureEnabled("hud.target"),host.targetVisible(),host.targetRange(),
                host.featureEnabled("combat.aimassist"),host.aimVisibleOnly(),host.aimRange());
            nearestSnapshot.clear();
            try {
                // No targets: publish an empty valid tick snapshot so unrelated
                // click/hit/block policies keep their exact session admission.
                // Any active target range is applied BEFORE expensive LOS rays.
                if(demand.needed()) for (Object raw : minecraft.theWorld.playerEntities) {
                    if (!(raw instanceof EntityPlayer)) continue;
                    EntityPlayer p = (EntityPlayer)raw;
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
                    // With unfiltered-only targeting, avoid every LOS ray and
                    // use the bounded hidden partition as a generic priority
                    // list; consumers explicitly ignore the visible flag.
                    boolean visible=demand.testVisibility(distanceSq) &&
                        !p.isInvisible() && minecraft.thePlayer.canEntityBeSeen(p);
                    if(!demand.admit(visible,distanceSq))continue;
                    nearestSnapshot.offer(p,p.getEntityId(),distanceSq,visible);
                }
                final List<WorldSnapshot.Entity> players =
                    new ArrayList<WorldSnapshot.Entity>(nearestSnapshot.size());
                nearestSnapshot.forEach(new WorldSnapshotAdmission.Visitor<EntityPlayer>() {
                    public void visit(EntityPlayer p,boolean visible) {
                        // Reject a candidate that changed between admission and
                        // publication, instead of failing the whole client tick.
                        double health=p.getHealth();
                        if(p.isDead || !WorldSnapshot.finitePublic(health) || health<=0 ||
                           !WorldSnapshot.finitePublic(p.posX) ||
                           !WorldSnapshot.finitePublic(p.posY) ||
                           !WorldSnapshot.finitePublic(p.posZ))return;
                        players.add(new WorldSnapshot.Entity(
                            p.getEntityId(),p.posX,p.posY,p.posZ,health,true,visible,true));
                    }
                });
                host.diagnostics().record(Counter.SNAPSHOT);
                host.services().publish(new WorldSnapshot(
                    host.services().generation(),host.services().tick(),
                    minecraft.thePlayer.posX,minecraft.thePlayer.posY,minecraft.thePlayer.posZ,
                    minecraft.thePlayer.rotationYaw,minecraft.thePlayer.rotationPitch,players));
            } finally {
                nearestSnapshot.clear();
            }
        }
        features.tick();
        combat.tick();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        host.diagnostics().record(Counter.KEY_CALLBACK);
        int code = Keyboard.getEventKey();
        // Event code 0 means no keyboard key; no keybind should fire.
        if (code == 0) return;

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
        int warningStatusX=0,warningStatusBottom=2;
        if(status || fpsText!=null || coordsText!=null) {
            final int statusWidth=status ? Math.max(1,
                Math.round((minecraft.fontRendererObj.getStringWidth(statusText)+4)*scale)) : 0;
            final int statusHeight=status ? Math.max(1,
                Math.round((fontHeight+5)*scale)) : 0;
            final StatusHudLayout layout=StatusHudLayout.calculate(host.ui(),sw,sh,
                status,fpsText!=null,coordsText!=null,
                statusWidth,statusHeight,
                fpsText==null?0:minecraft.fontRendererObj.getStringWidth(fpsText),
                coordsText==null?0:minecraft.fontRendererObj.getStringWidth(coordsText),
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
            if(metrics && (fpsText!=null || coordsText!=null))
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
                worldIdentity.reset();
                features.reset();
                combat.reset();
            }
        });
    }
}
