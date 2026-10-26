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

    ForgeVelocityController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null velocity dependencies");
        mc=game;host=runtime;
    }
    void reset(){policy.reset();}
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
