package dev.ghostfusion.forge189;

import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
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
    void reset() {
        if(policy.pending())
            host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                ModuleActionTelemetry.Event.CANCELLED);
        policy.reset();
    }
    boolean sprintReserved() {return policy.pending();}
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
                if(policy.attack(true,eligible(p),p.isSprinting(),
                    host.wtapDelayTicks(),host.services().tick(),
                    host.services().generation()))
                    host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                        ModuleActionTelemetry.Event.ATTEMPT);
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
                boolean hadReservation=policy.pending();
                WTapPolicy.Decision result=policy.tick(
                    true,eligible(p),host.services().tick(),
                    host.services().generation());
                // Losing forward movement/ground eligibility cancels the
                // pending sprint reset *inside* policy.tick(). Record it
                // before another native attack can acquire that lane.
                if(hadReservation && !policy.pending() &&
                   result==WTapPolicy.Decision.NONE)
                    host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                        ModuleActionTelemetry.Event.CANCELLED);
                if(result==WTapPolicy.Decision.RESET ||
                   result==WTapPolicy.Decision.HOLD) {
                    p.setSprinting(false);
                    if(result==WTapPolicy.Decision.RESET)
                        host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                            ModuleActionTelemetry.Event.DISPATCHED);
                } else if(result==WTapPolicy.Decision.RESTORE) {
                    p.setSprinting(true);
                    host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                        ModuleActionTelemetry.Event.DISPATCHED);
                    host.nativeAction(ModuleActionTelemetry.Lane.WTAP,
                        ModuleActionTelemetry.Event.LOCAL_COMPLETE);
                }
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
