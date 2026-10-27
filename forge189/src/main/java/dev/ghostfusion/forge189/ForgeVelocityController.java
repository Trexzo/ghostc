package dev.ghostfusion.forge189;

import dev.ghostfusion.features.VelocityPolicy;
import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Forge owner-thread integration: adjusts local motion once per native hurt
 * edge. No packet cancellation, tampering, knockback prediction or scheduled
 * delayed motion writes. Server-side movement remains authoritative.
 */
final class ForgeVelocityController {
    private static final String MODULE="combat.velocity";
    private final Minecraft mc;
    private final HostRuntime host;
    private final VelocityPolicy policy=new VelocityPolicy();
    private final ForgeVelocityPacketBridge inbound=new ForgeVelocityPacketBridge();

    ForgeVelocityController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null velocity dependencies");
        mc=game;host=runtime;
    }
    void reset() {
        inbound.reset();
        policy.reset();
    }
    /** Read-only immutable aggregate hook health; never exposes the
     * Netty channel, player identifier, server or packet contents. */
    ForgeVelocityPacketBridge.Health health() {
        return inbound.health();
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null || mc.currentScreen!=null ||
           !mc.inGameHasFocus || !host.services().connected() ||
           !host.featureEnabled(MODULE)) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run(){evaluate();}
        });
        if(!healthy) reset();
    }
    private void evaluate() {
        EntityPlayerSP player=mc.thePlayer;
        boolean safe=!player.isDead && !player.capabilities.isFlying &&
            !player.isRiding() && !player.isInWater() &&
            !player.isInLava() && !player.isOnLadder();
        if("packet".equals(host.velocityMode())) {
            // One immutable player-id/percentage snapshot is published from
            // the owner tick; the Netty listener never reads game objects.
            policy.reset();
            if(!safe || mc.getNetHandler()==null) {
                inbound.reset();
                return;
            }
            inbound.update(mc.getNetHandler().getNetworkManager(),
                player.getEntityId(),
                host.velocityHorizontalPercent(),host.velocityVerticalPercent(),
                host.diagnosticsEnabled());
            return;
        }
        // Legacy hurt-mode performs no packet interception.
        inbound.reset();
        VelocityPolicy.Adjustment adjustment=policy.sample(
            true,safe,player.hurtTime,
            player.motionX,player.motionY,player.motionZ,
            host.velocityHorizontalPercent(),host.velocityVerticalPercent(),
            host.services().tick(),host.services().generation());
        if(adjustment==null)return;
        // No direct attack, outgoing/incoming packets, or other entities
        // are ever manipulated. Only the local player's live motion axes.
        player.motionX=adjustment.x;
        player.motionY=adjustment.y;
        player.motionZ=adjustment.z;
    }
}
