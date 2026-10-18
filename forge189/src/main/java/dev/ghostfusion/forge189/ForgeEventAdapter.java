package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

/**
 * All Forge/LWJGL concerns terminate here; no Minecraft or obfuscated types in the core.
 * Must be registered on the Forge event bus and FML common bus.
 */
public final class ForgeEventAdapter {
    private final Minecraft minecraft;
    private final HostRuntime host;

    public ForgeEventAdapter(Minecraft minecraft, HostRuntime host) {
        if (minecraft == null || host == null) throw new IllegalArgumentException("null dependency");
        this.minecraft = minecraft;
        this.host = host;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        host.inputFocus(minecraft.currentScreen != null || !minecraft.inGameHasFocus);
        host.worldPresence(minecraft.theWorld != null);
        host.tick();
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        int code = Keyboard.getEventKey();
        // Event code 0 means no keyboard key; no keybind should fire.
        if (code == 0) return;
        // Suppress module hotkeys when chat, text entry or any GUI has focus.
        host.key(code, Keyboard.getEventKeyState(), minecraft.currentScreen != null || !minecraft.inGameHasFocus);
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (host.render2d()) {
            String label = "GhostC R0.2 | F8: Status";
            minecraft.fontRendererObj.drawStringWithShadow(label, 5, 5, 0xF5F5FF);
            if (!host.profileWarning().isEmpty()) {
                minecraft.fontRendererObj.drawStringWithShadow(
                    host.profileWarning(), 5, 17, 0xFF9999);
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote && event.world == minecraft.theWorld) {
            host.worldPresence(true);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) host.worldPresence(false);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        host.disconnect();
    }
}
