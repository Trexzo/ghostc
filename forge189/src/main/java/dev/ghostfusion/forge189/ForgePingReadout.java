package dev.ghostfusion.forge189;

import dev.ghostfusion.features.PingHudText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.network.NetHandlerPlayClient;

/** Shared, read-only local tab-list query for overlay and editor preview. */
final class ForgePingReadout {
    private ForgePingReadout(){}
    static String read(Minecraft mc) {
        if(mc==null || mc.thePlayer==null)return null;
        NetHandlerPlayClient handler=mc.getNetHandler();
        if(handler==null)return null;
        NetworkPlayerInfo player=handler.getPlayerInfo(mc.thePlayer.getUniqueID());
        if(player==null)return null;
        String label=PingHudText.format(player.getResponseTime());
        return label.isEmpty()?null:label;
    }
}
