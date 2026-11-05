package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.features.PatternClickScheduler;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.LiveCombatTargetGate;
import dev.ghostfusion.features.NativeAttackFrameGate;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.TriggerNativeIdentityFence;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.AxisAlignedBB;

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
    private static final String TRIGGER_ID = "combat.triggerbot";
    private static final String HIT_ID = "combat.hitselect";
    private static final String BLOCK_ID = "combat.blockhit";

    private final Minecraft mc;
    private final HostRuntime host;
    private final AimAssistPolicy aim = new AimAssistPolicy();
    private final AimTargetLock aimLock = new AimTargetLock();
    private final AimTargetSwitchPolicy aimSwitch = new AimTargetSwitchPolicy();
    private final AimManualInputPolicy manualAim = new AimManualInputPolicy();
    private final FixedClickScheduler scheduler = new FixedClickScheduler();
    private final PatternClickScheduler patternScheduler = new PatternClickScheduler();
    private final HitSelectPolicy hitSelect = new HitSelectPolicy();
    private final TriggerBotPolicy triggerBot = new TriggerBotPolicy();
    private final TriggerNativeIdentityFence triggerIdentity =
        new TriggerNativeIdentityFence();
    private final BlockHitPolicy blockHit = new BlockHitPolicy();
    private final PhysicalInputGate physicalInput = new PhysicalInputGate();
    private final NativePulseSettlement pulseSettlement = new NativePulseSettlement();

    ForgeCombatController(Minecraft mc, HostRuntime host) {
        if (mc == null || host == null) throw new IllegalArgumentException("null bridge dependency");
        this.mc = mc;
        this.host = host;
    }

    void tick() {
        final SessionServices session = host.services();
        final EntityPlayerSP player = mc.thePlayer;
        final Object tickWorld = mc.theWorld;
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

        final int tickAttackBinding=mc.gameSettings.keyBindAttack.getKeyCode();
        final int tickUseBinding=mc.gameSettings.keyBindUseItem.getKeyCode();
        final boolean physicalDown=mc.gameSettings.keyBindAttack.isKeyDown();
        final boolean held=physicalInput.allowed(session.generation(),true,true,false,
            physicalDown,tickAttackBinding,tickUseBinding);
        if (physicalDown && !held) host.diagnostics().record(Counter.INPUT_SUPPRESSED);

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

        // A native, live player already under the physical crosshair owns
        // the current camera tick when unheld TriggerBot is active. This
        // avoids perpetual Aim Assist re-rotation starving acquisition.
        // TriggerBot must STILL earn its own UUID-based dwell and attack grant.
        final boolean triggerEnabled = host.featureEnabled(TRIGGER_ID);
        final boolean safeAttackBinding = triggerEnabled && triggerBindingValid();
        final boolean nativeCrosshair = safeAttackBinding && !physicalDown &&
            !useHeld && !player.isUsingItem() &&
            triggerTargetValid(player,target);
        final boolean yieldToTrigger = AimTriggerHandoff.yieldCamera(
            triggerEnabled,physicalDown,useHeld,player.isUsingItem(),
            safeAttackBinding,nativeCrosshair);

        // Cancel any previous owned request before scheduling the new one.
        // This also makes disabled, focus-lost and failing modules fail closed.
        session.rotations().cancel(AIM_ID);
        if (!host.featureEnabled(AIM_ID)) {
            aimLock.clear();
            aimSwitch.reset();
            manualAim.reset();
        }
        final WorldSnapshot snapshot = session.snapshot();
        final boolean[] aimAdjusted = {false};
        if (snapshot != null && host.featureEnabled(AIM_ID)) {
            boolean healthy = host.runFeature(AIM_ID, new Runnable() {
                public void run() {
                    // Observe physical mouse movement before *any* other
                    // camera handoff. Previously TriggerBot yielding and
                    // require-click denial reset the manual pause, allowing
                    // Aim Assist to resume immediately after crosshair loss.
                    // A disabled option still preserves the old camera path.
                    final boolean mouseSuppressed;
                    if (host.aimRespectMouse()) {
                        // Missing mouse helper fails closed: do not rotate.
                        mouseSuppressed = mc.mouseHelper == null ||
                            manualAim.suppressed(session.generation(),session.tick(),
                                true,mc.mouseHelper.deltaX,mc.mouseHelper.deltaY,
                                host.aimMouseThreshold(),host.aimMousePauseTicks());
                    } else {
                        manualAim.reset();
                        mouseSuppressed = false;
                    }
                    if (mouseSuppressed) {
                        if(host.diagnosticsEnabled())
                            host.diagnostics().record(Counter.COMBAT_AIM_MOUSE_YIELD);
                        aimLock.clear();
                        aimSwitch.reset();
                        return;
                    }
                    if (yieldToTrigger) {
                        if(host.diagnosticsEnabled())
                            host.diagnostics().record(Counter.COMBAT_AIM_TRIGGER_YIELD);
                        aimLock.clear();
                        aimSwitch.reset();
                        // Preserve elapsed physical mouse pause across the
                        // TriggerBot handoff; TriggerBot never owns the camera.
                        return;
                    }
                    if (host.aimRequireClick() && !held) {
                        aimLock.clear();
                        aimSwitch.reset();
                        // A missing required attack hold is not a reason to
                        // discard a physical mouse movement in this same tick.
                        return;
                    }
                    final boolean locking = host.aimLockTarget();
                    int preferred = aimLock.preferred(
                        session.generation(), session.tick(), locking);
                    if (preferred >= 0) {
                        Entity previous = mc.theWorld.getEntityByID(preferred);
                        if (!(previous instanceof EntityPlayer) ||
                            !aimLock.sameNativeIdentity(((EntityPlayer)previous).getUniqueID(),
                                previous,mc.theWorld)) {
                            // Entity IDs can be reused without changing the
                            // current world epoch. UUID must still match.
                            aimLock.clear();
                            preferred = -1;
                        }
                    }
                    AimAssistPolicy.Suggestion suggestion = aim.suggest(
                        snapshot, player.getEyeHeight(), host.aimTargetHeight(),
                        host.aimRange(), host.aimFov(), host.aimDegreesPerTick(),
                        host.aimVisibleOnly(), host.aimHorizontalFraction(),
                        host.aimVerticalFraction(), preferred,
                        host.aimTargetPriority());
                    if (suggestion == null) {
                        aimLock.clear();
                        aimSwitch.reset();
                        return;
                    }
                    // A snapshot is immutable, but its player can despawn,
                    // die, become invisible or move before the camera write.
                    // Validate the current-world entity and same-tick pose.
                    Entity live=mc.theWorld.getEntityByID(suggestion.entityId);
                    // Missing live target is a strict no-write veto. The
                    // retained hint is independently UUID-checked next tick.
                    if(!(live instanceof EntityPlayer)) {
                        aimLock.clear();
                        aimSwitch.reset();
                        return;
                    }
                    EntityPlayer candidate=(EntityPlayer)live;
                    if(!LiveCombatTargetGate.eligible(candidate,
                            mc.theWorld.getEntityByID(candidate.getEntityId()),player,
                            candidate.isDead,candidate.getHealth(),
                            candidate.isInvisible(),host.aimVisibleOnly()) ||
                        !suggestion.sameTargetPosition(candidate.posX,candidate.posY,candidate.posZ) ||
                        !suggestion.sameSourcePose(player.posX,player.posY,player.posZ,
                            player.rotationYaw,player.rotationPitch,player.getEyeHeight())) {
                        aimLock.clear();
                        aimSwitch.reset();
                        return;
                    }
                    // Only live-verified UUID candidates may request a camera
                    // write. Switching to another player optionally requires
                    // a stable consecutive candidate window; while waiting,
                    // manual camera control stays completely untouched.
                    // Hard UUID lock retains its original immediate fallback.
                    if (!aimSwitch.considerNative(session.generation(), session.tick(),
                            suggestion.entityId, candidate.getUniqueID(),
                            candidate,mc.theWorld,
                            locking ? 0 : host.aimSwitchDelayTicks())) {
                        return;
                    }
                    session.rotations().request(AIM_ID, RotationCoordinator.Scope.CAMERA,
                        suggestion.yaw, suggestion.pitch, 10,
                        session.tick(), 1, session.generation());
                    RotationCoordinator.Decision rotation = session.rotations().decide(
                        RotationCoordinator.Scope.CAMERA, session.tick(), session.generation(),
                        player.rotationYaw, player.rotationPitch);
                    if (AIM_ID.equals(rotation.owner)) {
                        // No staged camera decision survives a same-UUID
                        // replacement, world swap or newer manual viewpoint.
                        if(mc.thePlayer!=player || mc.theWorld!=tickWorld ||
                           mc.theWorld.getEntityByID(suggestion.entityId)!=candidate ||
                           !LiveCombatTargetGate.eligible(candidate,
                               mc.theWorld.getEntityByID(candidate.getEntityId()),player,
                               candidate.isDead,candidate.getHealth(),
                               candidate.isInvisible(),host.aimVisibleOnly()) ||
                           !suggestion.sameTargetPosition(candidate.posX,candidate.posY,candidate.posZ) ||
                           !suggestion.sameSourcePose(player.posX,player.posY,player.posZ,
                               player.rotationYaw,player.rotationPitch,player.getEyeHeight())) {
                            aimLock.clear();
                            aimSwitch.cancelPrepared();
                            session.rotations().cancel(AIM_ID);
                            return;
                        }
                        player.rotationYaw = rotation.yaw;
                        player.rotationPitch = rotation.pitch;
                        aimSwitch.appliedNative(session.generation(),session.tick(),
                            suggestion.entityId,candidate.getUniqueID(),candidate,mc.theWorld);
                        aimAdjusted[0] = true;
                        if (locking) aimLock.appliedNative(
                            session.generation(), session.tick(),
                            suggestion.entityId, candidate.getUniqueID(),candidate,mc.theWorld);
                        host.diagnostics().record(Counter.AIM_APPLIED);
                    } else {
                        aimLock.clear();
                        aimSwitch.cancelPrepared();
                    }
                }
            });
            if (!healthy) {
                aimLock.clear();
                aimSwitch.reset();
                manualAim.reset();
                session.rotations().cancel(AIM_ID);
            }
        }

        if (!host.featureEnabled(HIT_ID)) hitSelect.reset();
        // Both sources propose to the SAME exclusive action coordinator.
        // Physical attack hold owns the ordinary AutoClicker; TriggerBot
        // does not synthesize extra attacks while a manual click is held.
        session.actions().cancelOwner(CLICK_ID);
        session.actions().cancelOwner(TRIGGER_ID);
        if (host.featureEnabled(CLICK_ID)) {
            boolean healthy = host.runFeature(CLICK_ID, new Runnable() {
                public void run() {
                    boolean eligible = !useHeld && !player.isUsingItem() &&
                        (!host.clickEntityOnly() || validTarget);
                    final long now = System.nanoTime();
                    final boolean due;
                    if ("fixed".equals(host.clickPattern())) {
                        patternScheduler.reset();
                        due = scheduler.due(now, eligible, held, host.clickCps());
                    } else {
                        scheduler.reset();
                        double first = host.clickMinCps(), second = host.clickMaxCps();
                        due = patternScheduler.due(now, eligible, held,
                            Math.min(first, second), Math.max(first, second),
                            host.clickPattern());
                    }
                    if (due) session.actions().request(CLICK_ID,
                        ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,
                        0,20,session.tick(),1,session.generation());
                }
            });
            if (!healthy) {
                scheduler.reset();
                patternScheduler.reset();
                session.actions().cancelOwner(CLICK_ID);
            }
        } else {
            scheduler.reset();
            patternScheduler.reset();
        }

        if (host.featureEnabled(TRIGGER_ID)) {
            boolean healthy = host.runFeature(TRIGGER_ID, new Runnable() {
                public void run() {
                    boolean eligible = triggerBindingValid() && !physicalDown && !held && !useHeld &&
                        !player.isUsingItem() && !aimAdjusted[0] &&
                        triggerTargetValid(player,target);
                    java.util.UUID uuid = eligible ? target.getUniqueID() : null;
                    // A player may respawn / be re-instantiated with the
                    // SAME UUID or numeric ID while still under crosshair.
                    // In that case the earlier entity's dwell cannot count.
                    boolean replaced=triggerIdentity.observe(
                        session.generation(),session.tick(),eligible,uuid,
                        eligible ? target.getEntityId() : -1,
                        eligible ? target : null,
                        eligible ? mc.theWorld : null);
                    if(replaced)triggerBot.invalidateCandidate();
                    if (triggerBot.due(session.generation(),session.tick(),
                            eligible,uuid,host.triggerAcquireTicks(),
                            host.triggerCooldownTicks()))
                        session.actions().request(TRIGGER_ID,
                            ActionCoordinator.Type.ATTACK,
                            ActionCoordinator.Phase.OUTPUT,0,10,
                            session.tick(),1,session.generation());
                }
            });
            if (!healthy) {
                triggerBot.reset();
                triggerIdentity.reset();
                session.actions().cancelOwner(TRIGGER_ID);
            }
        } else {
            triggerBot.reset();
            triggerIdentity.reset();
        }

        // Hit Select must evaluate the WINNING request only, never both
        // candidate cooldowns. An arbitration loss is not a dispatched hit.
        final ActionCoordinator.Decision selectedAttack = session.actions().consume(
            ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,
            session.tick(),session.generation());
        boolean approved = selectedAttack != null;
        final boolean hitGateUsed = approved && host.featureEnabled(HIT_ID);
        // An option changed after preview cannot weaken its original proof.
        final boolean hitTargetRequiredAtPreview=host.hitRequireTarget();
        if (hitGateUsed) {
            final boolean[] allowed = {false};
            boolean healthy = host.runFeature(HIT_ID, new Runnable() {
                public void run() {
                    allowed[0] = hitSelect.preview(true,
                        !hitTargetRequiredAtPreview || validTarget,
                        target == null ? 0 : target.hurtTime,
                        host.hitMaxHurtTime(),host.hitGapTicks(),player.hurtTime,
                        host.hitMode(),host.hitTradeMinSelfHurt(),
                        session.tick(),session.generation());
                }
            });
            approved = healthy && allowed[0];
            if (!approved) hitSelect.cancelPrepared();
        }
        final boolean attackApproved = approved;
        // Final native guard executes only the chosen, still-valid owner.
        // The Block Hit post-attack transition below observes a real pulse,
        // not merely a selected ActionCoordinator request.
        // The Forge key pulse uses Minecraft's normal key-input path; there
        // are no packets, attackEntity calls or bypass controllers here.
        if (selectedAttack != null && CLICK_ID.equals(selectedAttack.owner) &&
            attackApproved && host.featureEnabled(CLICK_ID) &&
            pulseSettlement.admitNative(NativePulseSettlement.Lane.ATTACK,
                session.generation(),tickWorld,player) &&
            held && NativeAttackFrameGate.sameFrame(
                player,mc.thePlayer,tickWorld,mc.theWorld,
                tickAttackBinding,tickUseBinding,
                mc.gameSettings.keyBindAttack.getKeyCode(),
                mc.gameSettings.keyBindUseItem.getKeyCode(),
                mc.inGameHasFocus,mc.currentScreen!=null,
                mc.theWorld!=null &&
                    mc.theWorld.getEntityByID(player.getEntityId())==player) &&
            NativeAttackFrameGate.attackState(false,physicalDown,
                mc.gameSettings.keyBindAttack.isKeyDown(),
                mc.gameSettings.keyBindUseItem.isKeyDown(),
                player.isUsingItem()) &&
            (!NativeAttackFrameGate.requireExactClickTarget(
                host.clickEntityOnly(),hitGateUsed,
                hitTargetRequiredAtPreview || host.hitRequireTarget()) ||
                NativeAttackFrameGate.target(
                target,mc.objectMouseOver!=null &&
                    mc.objectMouseOver.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY
                    ? mc.objectMouseOver.entityHit : null,
                mc.theWorld==null||target==null?null:
                    mc.theWorld.getEntityByID(target.getEntityId()),
                player,target!=null&&target.isDead,
                target==null?0:target.getHealth())) &&
            (!hitGateUsed || confirmHitSelectNative(
                target,player,hitTargetRequiredAtPreview,session))) {
            host.runFeature(CLICK_ID, new Runnable() {
                public void run() {
                    pulseSettlement.begin(NativePulseSettlement.Lane.ATTACK,
                        session.generation(),session.tick());
                    try {
                        KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                        pulseSettlement.nativeReturned(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                        if (hitGateUsed) hitSelect.committed(session.tick(),session.generation());
                        pulseSettlement.committed(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                    } finally {
                        pulseSettlement.abandon(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                    }
                    extraAttackQueued[0] = true;
                    host.diagnostics().record(Counter.EXTRA_ATTACK);
                    host.nativeAction(ModuleActionTelemetry.Lane.CLICKER,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if (selectedAttack != null && TRIGGER_ID.equals(selectedAttack.owner) &&
            attackApproved && host.featureEnabled(TRIGGER_ID) &&
            pulseSettlement.admitNative(NativePulseSettlement.Lane.ATTACK,
                session.generation(),tickWorld,player) &&
            triggerBindingValid() &&
            NativeAttackFrameGate.sameFrame(
                player,mc.thePlayer,tickWorld,mc.theWorld,
                tickAttackBinding,tickUseBinding,
                mc.gameSettings.keyBindAttack.getKeyCode(),
                mc.gameSettings.keyBindUseItem.getKeyCode(),
                mc.inGameHasFocus,mc.currentScreen!=null,
                mc.theWorld!=null &&
                    mc.theWorld.getEntityByID(player.getEntityId())==player) &&
            NativeAttackFrameGate.attackState(true,physicalDown,
                mc.gameSettings.keyBindAttack.isKeyDown(),
                mc.gameSettings.keyBindUseItem.isKeyDown(),
                player.isUsingItem()) && !aimAdjusted[0] &&
            target instanceof EntityPlayer &&
            triggerIdentity.permits(session.generation(),session.tick(),
                ((EntityPlayer)target).getUniqueID(),target.getEntityId(),
                target,mc.theWorld) &&
            triggerTargetValid(player,target) &&
            (!hitGateUsed || confirmHitSelectNative(
                target,player,hitTargetRequiredAtPreview,session))) {
            host.runFeature(TRIGGER_ID, new Runnable() {
                public void run() {
                    pulseSettlement.begin(NativePulseSettlement.Lane.ATTACK,
                        session.generation(),session.tick());
                    try {
                        KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                        pulseSettlement.nativeReturned(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                        if (hitGateUsed) hitSelect.committed(session.tick(),session.generation());
                        triggerBot.dispatched(session.generation(),session.tick(),
                            target.getUniqueID());
                        pulseSettlement.committed(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                    } finally {
                        // A partially committed native pulse is uncertain, not
                        // another READY candidate. Suppress both attack owners
                        // until a new verified game generation is established.
                        pulseSettlement.abandon(NativePulseSettlement.Lane.ATTACK,
                            session.generation(),session.tick());
                    }
                    host.diagnostics().record(Counter.EXTRA_ATTACK);
                    // Only this actual local native key pulse enters the
                    // TriggerBot lane. It is not a hit or server ACK.
                    host.nativeAction(ModuleActionTelemetry.Lane.TRIGGER,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if (selectedAttack != null && CLICK_ID.equals(selectedAttack.owner)) {
            // The cadence scheduler tracks ATTEMPTS. Keep its regular
            // Hit Select-denied pacing, but force a fresh physical-input
            // arming if an otherwise approved pulse failed its native gate.
            if (attackApproved) {
                scheduler.reset();
                patternScheduler.reset();
            }
            host.nativeAction(ModuleActionTelemetry.Lane.CLICKER,
                ModuleActionTelemetry.Event.REJECTED);
        } else if (selectedAttack != null && TRIGGER_ID.equals(selectedAttack.owner)) {
            // A final native target, LOS, crosshair or Hit Select veto must
            // not leave previously accumulated dwell ready for next tick.
            // Preserve last genuine dispatch cooldown across this denial.
            triggerBot.invalidateCandidate();
            triggerIdentity.invalidate();
            host.nativeAction(ModuleActionTelemetry.Lane.TRIGGER,
                ModuleActionTelemetry.Event.REJECTED);
        }
        // A prepared Hit Select candidate not dispatched must never
        // consume a cooldown or survive into the next frame.
        if (hitGateUsed) hitSelect.cancelPrepared();
        session.actions().cancelOwner(BLOCK_ID);
        if (host.featureEnabled(BLOCK_ID)) {
            boolean healthy = host.runFeature(BLOCK_ID, new Runnable() {
                public void run() {
                    if (!blockHit.preview(true, held, useHeld || player.isUsingItem(),
                        sword, validTarget, host.blockRequireTarget(),
                        extraAttackQueued[0], host.blockIntervalTicks(),
                        player.hurtTime, host.blockMode(),
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


        ActionCoordinator.Decision use = session.actions().consume(
            ActionCoordinator.Type.USE, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (use != null && BLOCK_ID.equals(use.owner) &&
            host.featureEnabled(BLOCK_ID) &&
            pulseSettlement.admitNative(NativePulseSettlement.Lane.USE,
                session.generation(),tickWorld,player) &&
            // ATTACK may have reached Minecraft even if its policy receipt
            // failed. The absence of extraAttackQueued is NOT proof of no
            // native pulse; forbid USE on this frame or while unresolved.
            !pulseSettlement.attackConflictsWithBlockHit(
                session.generation(),session.tick()) && held && sword &&
            NativeAttackFrameGate.sameFrame(
                player,mc.thePlayer,tickWorld,mc.theWorld,
                tickAttackBinding,tickUseBinding,
                mc.gameSettings.keyBindAttack.getKeyCode(),
                mc.gameSettings.keyBindUseItem.getKeyCode(),
                mc.inGameHasFocus,mc.currentScreen!=null,
                mc.theWorld!=null &&
                    mc.theWorld.getEntityByID(player.getEntityId())==player) &&
            NativeAttackFrameGate.safeUseBinding(tickUseBinding) &&
            NativeAttackFrameGate.attackState(false,physicalDown,
                mc.gameSettings.keyBindAttack.isKeyDown(),
                mc.gameSettings.keyBindUseItem.isKeyDown(),
                player.isUsingItem()) &&
            player.getHeldItem()!=null &&
            player.getHeldItem().getItem() instanceof ItemSword &&
            (!host.blockRequireTarget() || NativeAttackFrameGate.target(
                target,mc.objectMouseOver!=null &&
                    mc.objectMouseOver.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY
                    ? mc.objectMouseOver.entityHit : null,
                mc.theWorld==null||target==null?null:
                    mc.theWorld.getEntityByID(target.getEntityId()),
                player,target!=null&&target.isDead,
                target==null?0:target.getHealth()))) {
            host.runFeature(BLOCK_ID, new Runnable() {
                public void run() {
                    pulseSettlement.begin(NativePulseSettlement.Lane.USE,
                        session.generation(),session.tick());
                    try {
                        KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                        pulseSettlement.nativeReturned(NativePulseSettlement.Lane.USE,
                            session.generation(),session.tick());
                        blockHit.committed(session.tick(),session.generation());
                        pulseSettlement.committed(NativePulseSettlement.Lane.USE,
                            session.generation(),session.tick());
                    } finally {
                        pulseSettlement.abandon(NativePulseSettlement.Lane.USE,
                            session.generation(),session.tick());
                    }
                    host.diagnostics().record(Counter.EXTRA_USE);
                    host.nativeAction(ModuleActionTelemetry.Lane.BLOCKHIT,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if(use!=null && BLOCK_ID.equals(use.owner)) {
            host.nativeAction(ModuleActionTelemetry.Lane.BLOCKHIT,
                ModuleActionTelemetry.Event.REJECTED);
        }
        // A failed final native guard, arbitration loss, or quarantined
        // Block Hit module cannot consume an interval/event trigger.
        blockHit.cancelPrepared();
    }

    /**
     * After one owner has won ATTACK arbitration and the native input/target
     * gates have passed, bind the exact Hit Select preview to live damage
     * and unchanged settings before Minecraft's sole KeyBinding pulse.
     * This observer never queues an attack or commits a cooldown.
     */
    private boolean confirmHitSelectNative(final EntityLivingBase sampledTarget,
                                           final EntityPlayerSP player,
                                           final boolean requiredAtPreview,
                                           final SessionServices session) {
        if(!host.featureEnabled(HIT_ID)) {
            hitSelect.cancelPrepared();
            return false;
        }
        final boolean required=requiredAtPreview || host.hitRequireTarget();
        final boolean sameTarget=sampledTarget!=null && mc.theWorld!=null &&
            NativeAttackFrameGate.target(sampledTarget,
                mc.objectMouseOver!=null &&
                mc.objectMouseOver.typeOfHit==MovingObjectPosition.MovingObjectType.ENTITY
                    ? mc.objectMouseOver.entityHit : null,
                mc.theWorld.getEntityByID(sampledTarget.getEntityId()),
                player,sampledTarget.isDead,sampledTarget.getHealth());
        // A target sampled at preview cannot be silently replaced by a
        // different entity or disappear before final damage verification.
        if((sampledTarget!=null && !sameTarget) || (required && !sameTarget)) {
            hitSelect.cancelPrepared();
            return false;
        }
        final int currentTargetHurt=sameTarget?sampledTarget.hurtTime:0;
        final boolean[] ready={false};
        boolean healthy=host.runFeature(HIT_ID,new Runnable() {
            public void run() {
                ready[0]=hitSelect.confirmPrepared(true,true,
                    currentTargetHurt,host.hitMaxHurtTime(),host.hitGapTicks(),
                    player.hurtTime,host.hitMode(),host.hitTradeMinSelfHurt(),
                    session.tick(),session.generation());
            }
        });
        if(!healthy || !ready[0])hitSelect.cancelPrepared();
        return healthy && ready[0];
    }

    /** Synthetic key pulses must not bind the GUI shortcut, unbound key or use key. */
    private boolean triggerBindingValid() {
        int attack=mc.gameSettings.keyBindAttack.getKeyCode();
        int use=mc.gameSettings.keyBindUseItem.getKeyCode();
        return attack!=0 && attack!=use &&
            attack!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI;
    }

    /** Fresh crosshair, world, reach and team validation at native boundary. */
    /** Read the current native hotbar item, not the tick-start sword snapshot. */
    private static boolean nativeSword(EntityPlayerSP player) {
        ItemStack held=player.getHeldItem();
        return held!=null && held.getItem() instanceof ItemSword;
    }

    private boolean triggerTargetValid(EntityPlayerSP player,
                                       EntityLivingBase target) {
        if (!(target instanceof EntityPlayer) || mc.theWorld==null ||
            mc.currentScreen!=null || !mc.inGameHasFocus ||
            mc.objectMouseOver==null ||
            mc.objectMouseOver.typeOfHit!=MovingObjectPosition.MovingObjectType.ENTITY ||
            mc.objectMouseOver.entityHit!=target ||
            mc.theWorld.getEntityByID(target.getEntityId())!=target ||
            target==player || target.isDead || target.getHealth()<=0 ||
            target.isInvisible() || !player.canEntityBeSeen(target) ||
            player.getDistanceToEntity(target)>host.triggerRange() ||
            !NativeAttackFrameGate.triggerSwordAtOutput(
                host.triggerRequireSword(),nativeSword(player)))
            return false;
        // Rendering's objectMouseOver is a cache. Revalidate the actual
        // current camera ray against the target's live native bounding box;
        // stale render/camera observations cannot authorize a click.
        if(!Float.isFinite(player.rotationYaw) ||
           !Float.isFinite(player.rotationPitch)) return false;
        Vec3 origin=player.getPositionEyes(1.0F);
        Vec3 direction=player.getLook(1.0F);
        double range=host.triggerRange();
        Vec3 end=origin.addVector(direction.xCoord*range,
            direction.yCoord*range,direction.zCoord*range);
        float border=target.getCollisionBorderSize();
        AxisAlignedBB box=target.getEntityBoundingBox();
        if(box==null || !Float.isFinite(border) || border<0 || border>1.0F)
            return false;
        AxisAlignedBB bounds=box.expand(border,border,border);
        if(bounds.calculateIntercept(origin,end)==null)return false;
        return !host.triggerIgnoreTeammates() || player.getTeam()==null ||
            player.getTeam()!=target.getTeam();
    }

    /**
     * While an exclusive projectile hotbar transaction is active, preserve
     * bounded physical mouse history without ever submitting a camera write.
     * All attack, camera-target, key-rearm and rotation intents are still
     * reset on every exclusive tick. This only runs in the adapter's live,
     * focused, GUI-free projectile frame.
     */
    void suspendForProjectile() {
        final SessionServices session=host.services();
        if(host.featureEnabled(AIM_ID) && host.aimRespectMouse() &&
           session.connected() && mc.thePlayer!=null && mc.theWorld!=null &&
           mc.currentScreen==null && mc.inGameHasFocus &&
           mc.mouseHelper!=null) {
            try {
                manualAim.suppressed(session.generation(),session.tick(),true,
                    mc.mouseHelper.deltaX,mc.mouseHelper.deltaY,
                    host.aimMouseThreshold(),host.aimMousePauseTicks());
            } catch(RuntimeException badMouseSample) {
                // No incomplete or stale physical input history may escape
                // a faulty sampling transition into a new camera grant.
                manualAim.reset();
            }
        } else manualAim.reset();
        resetNonMouse(true);
    }

    /**
     * A genuine manual hotbar key/wheel event takes priority over every
     * optional combat write. Keep only already committed cooldown receipts;
     * pending Block Hit events, USE stages, attack dwell and camera/keys
     * are discarded. A separate native ownership conflict still hard resets.
     */
    void suspendForManualHotbar() {
        manualAim.reset();
        resetNonMouse(true);
    }

    /** Observe actual world identity even while native combat is disabled. */
    int reconcileNativePulseStatus() {
        SessionServices session=host.services();
        if(session.connected() && mc.theWorld!=null && mc.thePlayer!=null)
            pulseSettlement.reconcileNative(session.generation(),
                mc.theWorld,mc.thePlayer);
        return pulseSettlement.uncertainMask();
    }
    int nativePulseUncertainMask() {
        return pulseSettlement.uncertainMask();
    }

    /** Clears timers and all pending owned intents on world or focus change. */
    void reset() {
        manualAim.reset();
        resetNonMouse(false);
    }

    /**
     * Full teardown is necessary for world, focus, Panic, or disabled combat.
     * A LIVE exclusive projectile frame instead discards only pending combat
     * intent while retaining previous native attack cooldown receipts.
     * No target dwell, key hold or pending Hit Select preview survives.
     */
    private void resetNonMouse(boolean preserveCooldowns) {
        aimLock.reset();
        aimSwitch.reset();
        scheduler.reset();
        patternScheduler.reset();
        physicalInput.reset();
        if(preserveCooldowns) {
            hitSelect.cancelPrepared();
            triggerBot.invalidateCandidate();
        } else {
            hitSelect.reset();
            triggerBot.reset();
        }
        triggerIdentity.reset();
        // Live, same-world hotbar ownership is not a full world/Panic reset.
        // Do not lose the clock of a completed native Block Hit USE, but never
        // retain a queued event or pending USE when returning from a rod,
        // snowball, or manual slot change.
        if(preserveCooldowns && host.featureEnabled(BLOCK_ID))
            blockHit.suspendTransient();
        else blockHit.reset();
        SessionServices session = host.services();
        if (!session.connected()) return;
        session.rotations().cancel(AIM_ID);
        session.actions().cancelOwner(CLICK_ID);
        session.actions().cancelOwner(TRIGGER_ID);
        session.actions().cancelOwner(BLOCK_ID);
    }
}
