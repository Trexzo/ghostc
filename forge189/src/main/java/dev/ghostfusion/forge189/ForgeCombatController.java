package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.LiveCombatTargetGate;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
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
 * Sole Forge owner of optional local combat writes.
 *
 * Each module's Java callback goes through HostRuntime.runFeature (which
 * quarantines only that module on RuntimeException). Requests are canceled
 * BEFORE scheduling and again when a callback fails; no prior-tick attack,
 * use, or camera request may survive a module failure. There is no packet
 * interception, reflection or anticheat evasion.
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
    private final PhysicalInputGate physicalInput = new PhysicalInputGate();

    ForgeCombatController(Minecraft mc, HostRuntime host) {
        if (mc == null || host == null) throw new IllegalArgumentException("null bridge dependency");
        this.mc = mc;
        this.host = host;
    }

    void tick() {
        final SessionServices session = host.services();
        final EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || !session.connected() ||
            player.isDead || !Double.isFinite(player.getHealth()) ||
            player.getHealth()<=0 ||
            mc.currentScreen != null || !mc.inGameHasFocus ||
            session.snapshot()==null || session.snapshot().tick!=session.tick() ||
            session.snapshot().generation!=session.generation()) {
            // Missing/bad local player pose or skipped snapshot: no synthetic
            // attack, use or camera actions on old-world data.
            reset();
            return;
        }

        boolean physicalDown = mc.gameSettings.keyBindAttack.isKeyDown();
        final boolean held = physicalInput.allowed(session.generation(), true, true, false,
            physicalDown, mc.gameSettings.keyBindAttack.getKeyCode(),
            mc.gameSettings.keyBindUseItem.getKeyCode());
        if (physicalDown && !held) host.diagnostics().record(Counter.INPUT_SUPPRESSED);

        // Cancel any previous owned request before scheduling the new one.
        // This also makes disabled, focus-lost and failing modules fail closed.
        session.rotations().cancel(AIM_ID);
        final WorldSnapshot snapshot = session.snapshot();
        if (snapshot != null && host.featureEnabled(AIM_ID)) {
            boolean healthy = host.runFeature(AIM_ID, new Runnable() {
                public void run() {
                    if (host.aimRequireClick() && !held) return;
                    AimAssistPolicy.Suggestion suggestion = aim.suggest(
                        snapshot, player.getEyeHeight(), 1.25,
                        host.aimRange(), host.aimFov(), host.aimDegreesPerTick(),
                        host.aimVisibleOnly());
                    if (suggestion == null) return;
                    session.rotations().request(AIM_ID, RotationCoordinator.Scope.CAMERA,
                        suggestion.yaw, suggestion.pitch, 10,
                        session.tick(), 1, session.generation());
                    RotationCoordinator.Decision rotation = session.rotations().decide(
                        RotationCoordinator.Scope.CAMERA, session.tick(), session.generation(),
                        player.rotationYaw, player.rotationPitch);
                    if (AIM_ID.equals(rotation.owner)) {
                        player.rotationYaw = rotation.yaw;
                        player.rotationPitch = rotation.pitch;
                        host.diagnostics().record(Counter.AIM_APPLIED);
                    }
                }
            });
            if (!healthy) session.rotations().cancel(AIM_ID);
        }

        // Snapshot the current crosshair exactly once for all combat policies.
        EntityLivingBase found = null;
        if (mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
            mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            EntityLivingBase candidate=(EntityLivingBase)mc.objectMouseOver.entityHit;
            // objectMouseOver is produced during rendering and may still
            // reference a despawned entity. Require exact current-world
            // object identity, not merely matching entity IDs.
            if(LiveCombatTargetGate.eligible(candidate,
                    mc.theWorld.getEntityByID(candidate.getEntityId()),player,
                    candidate.isDead,candidate.getHealth()))
                found=candidate;
        }
        final EntityLivingBase target = found;
        final boolean validTarget = target != null;
        final boolean useHeld = mc.gameSettings.keyBindUseItem.isKeyDown();
        ItemStack equipped = player.getHeldItem();
        final boolean sword = equipped != null && equipped.getItem() instanceof ItemSword;
        final boolean[] extraAttackQueued = {false};

        if (!host.featureEnabled(HIT_ID)) hitSelect.reset();
        session.actions().cancelOwner(CLICK_ID);
        if (host.featureEnabled(CLICK_ID)) {
            boolean healthy = host.runFeature(CLICK_ID, new Runnable() {
                public void run() {
                    boolean eligible = !useHeld && !player.isUsingItem() &&
                        (!host.clickEntityOnly() || validTarget);
                    if (!scheduler.due(System.nanoTime(), eligible, held, host.clickCps()))
                        return;
                    final boolean[] allowed = {true};
                    if (host.featureEnabled(HIT_ID)) {
                        allowed[0] = false;
                        // A faulty Hit Select disables only itself and rejects
                        // this extra pulse; the clicker stays enabled.
                        host.runFeature(HIT_ID, new Runnable() {
                            public void run() {
                                allowed[0] = hitSelect.accept(true,
                                    !host.hitRequireTarget() || validTarget,
                                    target == null ? 0 : target.hurtTime,
                                    host.hitMaxHurtTime(), host.hitGapTicks(),
                                    session.tick(), session.generation());
                            }
                        });
                    }
                    if (!allowed[0]) return;
                    session.actions().request(CLICK_ID, ActionCoordinator.Type.ATTACK,
                        ActionCoordinator.Phase.OUTPUT, 0, 10,
                        session.tick(), 1, session.generation());
                    extraAttackQueued[0] = true;
                }
            });
            if (!healthy) {
                scheduler.reset();
                hitSelect.reset();
                extraAttackQueued[0] = false;
                session.actions().cancelOwner(CLICK_ID);
            }
        } else {
            scheduler.reset();
            session.actions().cancelOwner(CLICK_ID);
        }

        session.actions().cancelOwner(BLOCK_ID);
        if (host.featureEnabled(BLOCK_ID)) {
            boolean healthy = host.runFeature(BLOCK_ID, new Runnable() {
                public void run() {
                    if (!blockHit.due(true, held, useHeld || player.isUsingItem(),
                        sword, validTarget, host.blockRequireTarget(),
                        extraAttackQueued[0], host.blockIntervalTicks(),
                        session.tick(), session.generation())) return;
                    session.actions().request(BLOCK_ID, ActionCoordinator.Type.USE,
                        ActionCoordinator.Phase.OUTPUT, 0, 10,
                        session.tick(), 1, session.generation());
                }
            });
            if (!healthy) {
                blockHit.reset();
                session.actions().cancelOwner(BLOCK_ID);
            }
        } else {
            blockHit.reset();
        }

        ActionCoordinator.Decision attack = session.actions().consume(
            ActionCoordinator.Type.ATTACK, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (attack != null && CLICK_ID.equals(attack.owner) &&
            host.featureEnabled(CLICK_ID) && held && !useHeld && !player.isUsingItem() &&
            (!host.clickEntityOnly() || validTarget) &&
            (!host.featureEnabled(HIT_ID) || (!host.hitRequireTarget() || validTarget))) {
            host.runFeature(CLICK_ID, new Runnable() {
                public void run() {
                    // Normal vanilla key path, not direct attackEntity/packets.
                    KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                    host.diagnostics().record(Counter.EXTRA_ATTACK);
                }
            });
        }

        ActionCoordinator.Decision use = session.actions().consume(
            ActionCoordinator.Type.USE, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (use != null && BLOCK_ID.equals(use.owner) &&
            host.featureEnabled(BLOCK_ID) && held && sword &&
            !useHeld && !player.isUsingItem() &&
            (!host.blockRequireTarget() || validTarget)) {
            host.runFeature(BLOCK_ID, new Runnable() {
                public void run() {
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                    host.diagnostics().record(Counter.EXTRA_USE);
                }
            });
        }
    }

    /** Clears timers and all pending owned intents on world or focus change. */
    void reset() {
        scheduler.reset();
        physicalInput.reset();
        hitSelect.reset();
        blockHit.reset();
        SessionServices session = host.services();
        if (!session.connected()) return;
        session.rotations().cancel(AIM_ID);
        session.actions().cancelOwner(CLICK_ID);
        session.actions().cancelOwner(BLOCK_ID);
    }
}
