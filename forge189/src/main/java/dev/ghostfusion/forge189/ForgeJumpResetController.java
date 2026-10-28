package dev.ghostfusion.forge189;

import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Sole owner of opt-in Jump Reset. Invoked after Velocity and WTap in the
 * same admitted END client-tick. Calls the original 1.8.9 public
 * EntityPlayerSP.jump(), NOT raw motionY, keybind override or packet edits.
 */
final class ForgeJumpResetController {
    private static final String MODULE="combat.jumpreset";
    private final Minecraft mc;
    private final HostRuntime host;
    private final JumpResetPolicy policy=new JumpResetPolicy();

    ForgeJumpResetController(Minecraft game,HostRuntime runtime) {
        if(game==null||runtime==null)
            throw new IllegalArgumentException("null jump reset dependency");
        mc=game;host=runtime;
    }
    void reset() { policy.reset(); }

    void tick() {
        if(!host.featureEnabled(MODULE) || mc.thePlayer==null ||
           mc.theWorld==null || mc.currentScreen!=null ||
           !mc.inGameHasFocus || !host.services().connected()) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() {evaluate();}
        });
        if(!healthy) reset();
    }
    private void evaluate() {
        final EntityPlayerSP p=mc.thePlayer;
        final long tick=host.services().tick();
        final long epoch=host.services().generation();
        final boolean nativeValid=
            mc.theWorld.getEntityByID(p.getEntityId())==p &&
            !p.isDead && Double.isFinite(p.getHealth()) && p.getHealth()>0 &&
            p.onGround && p.movementInput!=null &&
            p.movementInput.moveForward>0.80f &&
            !p.movementInput.jump &&
            !mc.gameSettings.keyBindJump.isKeyDown() &&
            !mc.gameSettings.keyBindSneak.isKeyDown() &&
            !p.isSneaking() && !p.isUsingItem() &&
            !p.isInWater() && !p.isInLava() && !p.isOnLadder() &&
            !p.isRiding() && !p.isCollidedHorizontally &&
            !p.capabilities.allowFlying && !p.capabilities.isFlying;
        final boolean attackPermission =
            !host.jumpResetRequireAttackHeld() ||
            mc.gameSettings.keyBindAttack.isKeyDown();
        if(!policy.preview(epoch,tick,p.hurtTime,nativeValid&&attackPermission,
                           host.jumpResetMinHurtTime(),
                           host.jumpResetCooldownTicks()))return;
        // The pure policy grants no native write on its own. Revalidate the
        // exact original-game entity and grounded state at the output edge.
        if(mc.thePlayer!=p ||
           mc.theWorld.getEntityByID(p.getEntityId())!=p ||
           !p.onGround || mc.currentScreen!=null || !mc.inGameHasFocus ||
           !host.services().connected() ||
           !host.featureEnabled(MODULE) ||
           mc.gameSettings.keyBindJump.isKeyDown() ||
           mc.gameSettings.keyBindSneak.isKeyDown()) {
            policy.cancelPrepared();
            return;
        }
        // Minecraft owns jumping physics, potion modifiers, sprint and
        // server position reporting. Never force motion or spoof an input.
        p.jump();
        policy.committed(epoch,tick);
        host.nativeAction(ModuleActionTelemetry.Lane.JUMPRESET,
                          ModuleActionTelemetry.Event.DISPATCHED);
    }
}
