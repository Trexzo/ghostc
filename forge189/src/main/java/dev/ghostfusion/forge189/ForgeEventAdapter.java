package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.services.WorldIdentityTracker;
import java.util.HashSet;
import java.util.Set;
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
            List<WorldSnapshot.Entity> players = new ArrayList<WorldSnapshot.Entity>();
            Set<Integer> seenEntityIds=new HashSet<Integer>();
            for (Object raw : minecraft.theWorld.playerEntities) {
                if (!(raw instanceof EntityPlayer)) continue;
                EntityPlayer p = (EntityPlayer) raw;
                if (p == minecraft.thePlayer || p.isDead) continue;
                if (!seenEntityIds.add(p.getEntityId())) continue;
                if (players.size() == 256) break;
                double health = Math.max(0.0, (double)p.getHealth());
                if (!WorldSnapshot.finitePublic(p.posX) || !WorldSnapshot.finitePublic(p.posY) ||
                    !WorldSnapshot.finitePublic(p.posZ) || !WorldSnapshot.finitePublic(health)) continue;
                players.add(new WorldSnapshot.Entity(p.getEntityId(),p.posX,p.posY,p.posZ,
                    health,!p.isDead,!p.isInvisible() && minecraft.thePlayer.canEntityBeSeen(p),true));
            }
            host.diagnostics().record(Counter.SNAPSHOT);
            host.services().publish(new WorldSnapshot(
                host.services().generation(),host.services().tick(),
                minecraft.thePlayer.posX,minecraft.thePlayer.posY,minecraft.thePlayer.posZ,
                minecraft.thePlayer.rotationYaw,minecraft.thePlayer.rotationPitch,players));
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
        if (!worldIdentity.matches(minecraft.theWorld,minecraft.thePlayer)) return;
        final boolean status = host.render2d();
        final boolean metrics = host.metricsFrameRequested();
        if (!status && !metrics) {
            features.render2d(event.resolution);
            return;
        }
        final ScaledResolution resolution = event.resolution;
        RenderStateGuard.draw(new Runnable() {
            public void run() {
                final float scale = host.statusScale();
                final String statusText = host.detailedStatus()
                    ? "GhostC " + GhostCMod.CLIENT_VERSION + " | F7 ClickGUI" : "GhostC";
                final int sw = resolution.getScaledWidth();
                final int sh = resolution.getScaledHeight();
                final int statusWidth = Math.max(1, Math.round((minecraft.fontRendererObj.getStringWidth(statusText) + 4) * scale));
                final int statusHeight = Math.max(1, Math.round((minecraft.fontRendererObj.FONT_HEIGHT + 5) * scale));
                final int x = host.ui().pixelX(sw, statusWidth);
                final int y = host.ui().pixelY(sh, statusHeight + (metrics ? 24 : 0));
                if (status) {
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslatef(x, y, 0);
                        GL11.glScalef(scale, scale, 1);
                        if (host.statusShadow()) minecraft.fontRendererObj.drawStringWithShadow(statusText, 2, 2, 0xF2F2FF);
                        else minecraft.fontRendererObj.drawString(statusText, 2, 2, 0xF2F2FF);
                    } finally { GL11.glPopMatrix(); }
                }
                if (metrics) {
                    int dy = status ? statusHeight : 0;
                    if (host.showFps()) {
                        minecraft.fontRendererObj.drawStringWithShadow(
                            "FPS " + Minecraft.getDebugFPS(), x + 2, y + dy, 0xFFB2CBFF);
                        dy += minecraft.fontRendererObj.FONT_HEIGHT + 2;
                    }
                    if (host.showCoordinates() && minecraft.thePlayer != null) {
                        String location = String.format(Locale.ROOT, "XYZ %.1f %.1f %.1f",
                            minecraft.thePlayer.posX, minecraft.thePlayer.posY, minecraft.thePlayer.posZ);
                        minecraft.fontRendererObj.drawStringWithShadow(
                            location, x + 2, y + dy, 0xFFB2CBFF);
                    }
                }

                String warning = !host.faultWarning().isEmpty() ? host.faultWarning() :
                    !host.uiWarning().isEmpty() ? host.uiWarning() :
                    !host.diagnosticsWarning().isEmpty() ? host.diagnosticsWarning() : host.profileWarning();
                if (!warning.isEmpty()) minecraft.fontRendererObj.drawStringWithShadow(warning, x + 2,
                    Math.min(sh - 12, y + statusHeight + 25), 0xFFFF9999);
            }
        });
        features.render2d(event.resolution);
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
