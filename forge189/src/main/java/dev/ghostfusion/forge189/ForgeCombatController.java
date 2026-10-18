package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;

/**
 * Single Forge owner of optional local camera and attack-key pulse writes.
 * Both feature producers submit intent through G3 coordinators before this
 * bridge consumes it. No packet injection, hooks or bypass routines.
 */
public final class ForgeCombatController {
    private static final String AIM_ID = "combat.aimassist";
    private static final String CLICK_ID = "combat.clicker";
    private static final String HIT_ID = "combat.hitselect";
    private static final String BLOCK_ID = "combat.blockhit";

    private final Minecraft mc;
    private final HostRuntime host;
    private final AimAssistPolicy aim = new AimAssistPolicy();
    private final FixedClickScheduler scheduler = new FixedClickScheduler();
    private final HitSelectPolicy hitSelect = new HitSelectPolicy();
    private final BlockHitPolicy blockHit = new BlockHitPolicy();

    ForgeCombatController(Minecraft mc, HostRuntime host) {
        if (mc == null || host == null) throw new IllegalArgumentException("null bridge dependency");
        this.mc = mc;
        this.host = host;
    }

    void tick() {
        SessionServices session = host.services();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || !session.connected() ||
            mc.currentScreen != null || !mc.inGameHasFocus) {
            reset();
            return;
        }

        boolean held = mc.gameSettings.keyBindAttack.isKeyDown();
        WorldSnapshot snapshot = session.snapshot();

        if (host.featureEnabled(AIM_ID) && snapshot != null &&
            (!host.aimRequireClick() || held)) {
            // Aim at player upper torso; policy never writes rotation itself.
            AimAssistPolicy.Suggestion suggestion = aim.suggest(
                snapshot, player.getEyeHeight(), 1.25,
                host.aimRange(), host.aimFov(), host.aimDegreesPerTick(),
                host.aimVisibleOnly());
            if (suggestion != null) {
                session.rotations().request(AIM_ID, RotationCoordinator.Scope.CAMERA,
                    suggestion.yaw, suggestion.pitch, 10,
                    session.tick(), 1, session.generation());
            } else {
                session.rotations().cancel(AIM_ID);
            }
        } else session.rotations().cancel(AIM_ID);

        RotationCoordinator.Decision rotation = session.rotations().decide(
            RotationCoordinator.Scope.CAMERA, session.tick(), session.generation(),
            player.rotationYaw, player.rotationPitch);
        if (AIM_ID.equals(rotation.owner) && host.featureEnabled(AIM_ID) &&
            (!host.aimRequireClick() || held)) {
            player.rotationYaw = rotation.yaw;
            player.rotationPitch = rotation.pitch;
        }

        // Snapshot the crosshair once for the current tick. Both policies and
        // the final bridge authorization use exactly the same target identity.
        EntityLivingBase target = null;
        if (mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
            mc.objectMouseOver.entityHit instanceof EntityLivingBase &&
            mc.objectMouseOver.entityHit != player &&
            !mc.objectMouseOver.entityHit.isDead) {
            target = (EntityLivingBase)mc.objectMouseOver.entityHit;
        }
        boolean validTarget = target != null;
        boolean clickEnabled = host.featureEnabled(CLICK_ID);
        boolean hitEnabled = host.featureEnabled(HIT_ID);
        boolean blockEnabled = host.featureEnabled(BLOCK_ID);
        boolean useHeld = mc.gameSettings.keyBindUseItem.isKeyDown();
        ItemStack equipped = player.getHeldItem();
        boolean sword = equipped != null && equipped.getItem() instanceof ItemSword;

        boolean clickEligible = clickEnabled && !useHeld && !player.isUsingItem() &&
            (!host.clickEntityOnly() || validTarget);
        // An enabled Hit Select applies ONLY to our additional AutoClicker
        // presses; it cannot cancel Minecraft's normal physical mouse clicks.
        if (!hitEnabled) hitSelect.reset();
        boolean dueClick = scheduler.due(System.nanoTime(), clickEligible, held, host.clickCps());
        boolean hitSelectWasAccepted = false;
        if (dueClick) {
            boolean approve = !hitEnabled || hitSelect.accept(
                true, !host.hitRequireTarget() || validTarget,
                target == null ? 0 : target.hurtTime,
                host.hitMaxHurtTime(), host.hitGapTicks(), session.tick(), session.generation());
            hitSelectWasAccepted = approve;
            if (approve) {
                session.actions().request(CLICK_ID, ActionCoordinator.Type.ATTACK,
                    ActionCoordinator.Phase.OUTPUT, 0, 10,
                    session.tick(), 1, session.generation());
            }
        }

        // Block Hit is the only module allowed to request extra USE pulses.
        // A sword, attack hold and no competing vanilla use action are mandatory.
        if (blockHit.due(blockEnabled, held, useHeld || player.isUsingItem(),
                sword, validTarget, host.blockRequireTarget(),
                dueClick && (!hitEnabled || hitSelectWasAccepted),
                host.blockIntervalTicks(), session.tick(), session.generation())) {
            session.actions().request(BLOCK_ID, ActionCoordinator.Type.USE,
                ActionCoordinator.Phase.OUTPUT, 0, 10,
                session.tick(), 1, session.generation());
        }

        ActionCoordinator.Decision attack = session.actions().consume(
            ActionCoordinator.Type.ATTACK, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (attack != null && CLICK_ID.equals(attack.owner) && clickEnabled && held &&
            !useHeld && !player.isUsingItem() &&
            (!host.clickEntityOnly() || validTarget) &&
            (!hitEnabled || (!host.hitRequireTarget() || validTarget))) {
            // Enqueue one normal vanilla key press; no direct attackEntity or
            // packet send. The action coordinator prevents double consumption.
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }

        ActionCoordinator.Decision use = session.actions().consume(
            ActionCoordinator.Type.USE, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (use != null && BLOCK_ID.equals(use.owner) && blockEnabled && held &&
            sword && !useHeld && !player.isUsingItem() &&
            (!host.blockRequireTarget() || validTarget)) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
    }

    /** Clears held-timer state and both owned requests on world/focus transition. */
    void reset() {
        scheduler.reset();
        hitSelect.reset();
        blockHit.reset();
        SessionServices session = host.services();
        if (!session.connected()) return;
        session.rotations().cancel(AIM_ID);
        session.actions().cancelOwner(CLICK_ID);
        session.actions().cancelOwner(BLOCK_ID);
    }
}
