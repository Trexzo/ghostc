package dev.ghostfusion.forge189;

import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import java.io.File;

/**
 * Real Forge 1.8.9 entrypoint. No injection, external process or network connection.
 * All game objects stay within this adapter package.
 */
@Mod(modid = "ghostc", name = "GhostC", version = GhostCMod.CLIENT_VERSION,
     acceptedMinecraftVersions = "[1.8.9]", clientSideOnly = true)
public final class GhostCMod {
    /** A single Forge-side display/version source for the status and diagnostics HUDs. */
    public static final String CLIENT_VERSION = "2.77.0";
    private ForgeEventAdapter adapter;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        File config = new File(mc.mcDataDir, "config/ghostc");
        // FML initialization should only occur once, but keep registration idempotent.
        if (adapter != null) return;
        HostRuntime host = new HostRuntime(config.toPath());
        adapter = new ForgeEventAdapter(mc, host);
        MinecraftForge.EVENT_BUS.register(adapter);
        FMLCommonHandler.instance().bus().register(adapter);
    }
}
