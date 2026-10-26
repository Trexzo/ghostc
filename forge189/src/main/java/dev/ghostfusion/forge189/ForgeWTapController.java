package dev.ghostfusion.forge189;

import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;

/**
 * Native attack-event sprint reset. Minecraft remains responsible for all
 * attack packets, damage and the player's movement/input bindings.
 */
final class ForgeWTapController {
    private static final String MODULE="combat.wtap";
    private final Minecraft mc;
    private final HostRuntime host;
    private final WTapPolicy policy=new WTapPolicy();
    ForgeWTapController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null WTap dependencies");
        mc=game;host=runtime;
    }
    void reset() {policy.reset();}
    /** Invoked exclusively on native Forge AttackEntityEvent with local player. */
    void attack(final Entity rawTarget) {
        if(mc.theWorld==null || mc.thePlayer==null ||
           mc.currentScreen!=null || !mc.inGameHasFocus ||
           !host.featureEnabled(MODULE) || !host.services().connected()) {
            reset();return;
        }
        if(!(rawTarget instanceof EntityLivingBase) ||
           rawTarget==mc.thePlayer ||
           mc.theWorld.getEntityByID(rawTarget.getEntityId())!=rawTarget ||
           rawTarget.isDead ||
           ((EntityLivingBase)rawTarget).getHealth()<=0) return;
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() {
                final EntityPlayerSP p=mc.thePlayer;
                // Deliberately only RECORD native attack. The original
                // sprint-hit bonus must be computed before any sprint write.
                policy.attack(true,eligible(p),p.isSprinting(),
                    host.wtapDelayTicks(),host.services().tick(),
                    host.services().generation());
            }
        });
        if(!healthy)reset();
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null ||
           mc.currentScreen!=null || !mc.inGameHasFocus ||
           !host.featureEnabled(MODULE) || !host.services().connected()) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() {
                EntityPlayerSP p=mc.thePlayer;
                WTapPolicy.Decision result=policy.tick(
                    true,eligible(p),host.services().tick(),
                    host.services().generation());
                if(result==WTapPolicy.Decision.RESET ||
                   result==WTapPolicy.Decision.HOLD)
                    p.setSprinting(false);
                else if(result==WTapPolicy.Decision.RESTORE)
                    p.setSprinting(true);
            }
        });
        if(!healthy)reset();
    }
    private static boolean eligible(EntityPlayerSP p) {
        return !p.isDead && p.movementInput!=null &&
            p.movementInput.moveForward>0.80f &&
            p.onGround && !p.isCollidedHorizontally &&
            !p.isSneaking() && !p.isUsingItem() &&
            !p.capabilities.isFlying && !p.isRiding() &&
            !p.isInWater() && !p.isInLava() && !p.isOnLadder() &&
            !p.isPotionActive(Potion.blindness) &&
            (p.capabilities.allowFlying || p.getFoodStats().getFoodLevel()>6);
    }
}
