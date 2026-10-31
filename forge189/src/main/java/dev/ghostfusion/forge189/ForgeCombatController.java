package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.features.AimCameraReturnFence;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.features.PatternClickScheduler;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.LiveCombatTargetGate;
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
    private final AimCameraReturnFence cameraReturn = new AimCameraReturnFence();
    private final FixedClickScheduler scheduler = new FixedClickScheduler();
    private final PatternClickScheduler patternScheduler = new PatternClickScheduler();
    private final HitSelectPolicy hitSelect = new HitSelectPolicy();
    private final TriggerBotPolicy triggerBot = new TriggerBotPolicy();
    private final TriggerNativeIdentityFence triggerIdentity =
        new TriggerNativeIdentityFence();
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
            triggerTargetValid(player,target,sword);
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
            cameraReturn.reset();
        }
        final WorldSnapshot snapshot = session.snapshot();
        final boolean[] aimAdjusted = {false};
        if (snapshot != null && host.featureEnabled(AIM_ID)) {
            boolean healthy = host.runFeature(AIM_ID, new Runnable() {
                public void run() {
                    if (yieldToTrigger) {
                        cameraReturn.yieldCamera(session.generation(),session.tick());
                        if(host.diagnosticsEnabled())
                            host.diagnostics().record(Counter.COMBAT_AIM_TRIGGER_YIELD);
                        aimLock.clear();
                        aimSwitch.reset();
                        // Physical mouse motion during TriggerBot camera
                        // ownership must NOT disappear from its opt-in
                        // manual-input pause just because Aim yielded.
                        if(host.aimRespectMouse() && mc.mouseHelper!=null) {
                            manualAim.suppressed(session.generation(),session.tick(),
                                true,mc.mouseHelper.deltaX,mc.mouseHelper.deltaY,
                                host.aimMouseThreshold(),host.aimMousePauseTicks());
                        } else if(!host.aimRespectMouse()) manualAim.reset();
                        return;
                    }
                    if (host.aimRequireClick() && !held) {
                        cameraReturn.interrupt();
                        aimLock.clear();
                        aimSwitch.reset();
                        manualAim.reset();
                        return;
                    }
                    // Only native Minecraft's sampled mouse-helper deltas can
                    // veto our optional camera write. Legacy behavior is
                    // unchanged unless the physical-input option is enabled.
                    if (host.aimRespectMouse()) {
                        if (mc.mouseHelper == null ||
                            manualAim.suppressed(session.generation(),session.tick(),
                                true,mc.mouseHelper.deltaX,mc.mouseHelper.deltaY,
                                host.aimMouseThreshold(),host.aimMousePauseTicks())) {
                            if(host.diagnosticsEnabled())
                                host.diagnostics().record(Counter.COMBAT_AIM_MOUSE_YIELD);
                            cameraReturn.interrupt();
                            aimLock.clear();
                            aimSwitch.reset();
                            return;
                        }
                    } else manualAim.reset();
                    final boolean locking = host.aimLockTarget();
                    int preferred = aimLock.preferred(
                        session.generation(), session.tick(), locking);
                    if (preferred >= 0) {
                        Entity previous = mc.theWorld.getEntityByID(preferred);
                        if (!(previous instanceof EntityPlayer) ||
                            !aimLock.sameNativeIdentity(
                                ((EntityPlayer)previous).getUniqueID(),previous,mc.theWorld)) {
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
                        cameraReturn.interrupt();
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
                        cameraReturn.interrupt();
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
                        cameraReturn.interrupt();
                        aimLock.clear();
                        aimSwitch.reset();
                        return;
                    }
                    // Returning from native TriggerBot ownership must not
                    // bypass an explicitly configured Aim switch delay just
                    // because the earlier Aim switch policy was reset.
                    // The exact live entity/world must stay eligible across
                    // the handback dwell; zero delay is the legacy behavior.
                    if(!cameraReturn.consider(
                            session.generation(),session.tick(),
                            candidate.getEntityId(),candidate.getUniqueID(),
                            candidate,mc.theWorld,host.aimSwitchDelayTicks())) {
                        return;
                    }
                    // Only live-verified exact native EntityPlayer/World
                    // identities may request camera output. A respawned or
                    // replaced instance with identical ID and UUID must
                    // never borrow another object's prior switch dwell.
                    // Switching to another player optionally requires
                    // a stable consecutive candidate window; while waiting,
                    // manual camera control stays completely untouched.
                    // Hard UUID lock retains its original immediate fallback.
                    if (!aimSwitch.considerNative(session.generation(), session.tick(),
                            suggestion.entityId,candidate.getUniqueID(),
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
                        player.rotationYaw = rotation.yaw;
                        player.rotationPitch = rotation.pitch;
                        aimSwitch.appliedNative(session.generation(),session.tick(),
                            suggestion.entityId,candidate.getUniqueID(),candidate,mc.theWorld);
                        if(cameraReturn.pending())
                            cameraReturn.applied(session.generation(),session.tick());
                        aimAdjusted[0] = true;
                        if (locking) aimLock.appliedNative(
                            session.generation(),session.tick(),
                            suggestion.entityId,candidate.getUniqueID(),
                            candidate,mc.theWorld);
                        host.diagnostics().record(Counter.AIM_APPLIED);
                    } else {
                        aimLock.clear();
                        aimSwitch.cancelPrepared();
                    }
                }
            });
            if (!healthy) {
                cameraReturn.interrupt();
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
                        triggerTargetValid(player,target,sword);
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
        if (hitGateUsed) {
            final boolean[] allowed = {false};
            boolean healthy = host.runFeature(HIT_ID, new Runnable() {
                public void run() {
                    allowed[0] = hitSelect.preview(true,
                        !host.hitRequireTarget() || validTarget,
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
            held && !useHeld && !player.isUsingItem() &&
            (!host.clickEntityOnly() || validTarget)) {
            host.runFeature(CLICK_ID, new Runnable() {
                public void run() {
                    KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                    if (hitGateUsed) hitSelect.committed(session.tick(),session.generation());
                    extraAttackQueued[0] = true;
                    host.diagnostics().record(Counter.EXTRA_ATTACK);
                    host.nativeAction(ModuleActionTelemetry.Lane.CLICKER,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if (selectedAttack != null && TRIGGER_ID.equals(selectedAttack.owner) &&
            attackApproved && host.featureEnabled(TRIGGER_ID) &&
            triggerBindingValid() &&
            !mc.gameSettings.keyBindAttack.isKeyDown() &&
            !mc.gameSettings.keyBindUseItem.isKeyDown() &&
            !player.isUsingItem() && !aimAdjusted[0] &&
            target instanceof EntityPlayer &&
            triggerIdentity.permits(session.generation(),session.tick(),
                ((EntityPlayer)target).getUniqueID(),target.getEntityId(),
                target,mc.theWorld) &&
            triggerTargetValid(player,target,sword)) {
            host.runFeature(TRIGGER_ID, new Runnable() {
                public void run() {
                    KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
                    if (hitGateUsed) hitSelect.committed(session.tick(),session.generation());
                    triggerBot.dispatched(session.generation(),session.tick(),
                        target.getUniqueID());
                    host.diagnostics().record(Counter.EXTRA_ATTACK);
                    // Only this actual local native key pulse enters the
                    // TriggerBot lane. It is not a hit or server ACK.
                    host.nativeAction(ModuleActionTelemetry.Lane.TRIGGER,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if (selectedAttack != null && CLICK_ID.equals(selectedAttack.owner)) {
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
                    if (!blockHit.due(true, held, useHeld || player.isUsingItem(),
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
            host.featureEnabled(BLOCK_ID) && held && sword &&
            !useHeld && !player.isUsingItem() &&
            (!host.blockRequireTarget() || validTarget)) {
            host.runFeature(BLOCK_ID, new Runnable() {
                public void run() {
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                    host.diagnostics().record(Counter.EXTRA_USE);
                    host.nativeAction(ModuleActionTelemetry.Lane.BLOCKHIT,
                        ModuleActionTelemetry.Event.DISPATCHED);
                }
            });
        } else if(use!=null && BLOCK_ID.equals(use.owner)) {
            host.nativeAction(ModuleActionTelemetry.Lane.BLOCKHIT,
                ModuleActionTelemetry.Event.REJECTED);
        }
    }

    /** Synthetic key pulses must not bind the GUI shortcut, unbound key or use key. */
    private boolean triggerBindingValid() {
        int attack=mc.gameSettings.keyBindAttack.getKeyCode();
        int use=mc.gameSettings.keyBindUseItem.getKeyCode();
        return attack!=0 && attack!=use &&
            attack!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI;
    }

    /** Fresh crosshair, world, reach and team validation at native boundary. */
    private boolean triggerTargetValid(EntityPlayerSP player,
                                       EntityLivingBase target,boolean sword) {
        if (!(target instanceof EntityPlayer) || mc.theWorld==null ||
            mc.currentScreen!=null || !mc.inGameHasFocus ||
            mc.objectMouseOver==null ||
            mc.objectMouseOver.typeOfHit!=MovingObjectPosition.MovingObjectType.ENTITY ||
            mc.objectMouseOver.entityHit!=target ||
            mc.theWorld.getEntityByID(target.getEntityId())!=target ||
            target==player || target.isDead || target.getHealth()<=0 ||
            target.isInvisible() || !player.canEntityBeSeen(target) ||
            player.getDistanceToEntity(target)>host.triggerRange() ||
            (host.triggerRequireSword() && !sword))
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

    /** Clears timers and all pending owned intents on world or focus change. */
    void reset() {
        aimLock.reset();
        aimSwitch.reset();
        manualAim.reset();
        cameraReturn.reset();
        scheduler.reset();
        patternScheduler.reset();
        physicalInput.reset();
        hitSelect.reset();
        triggerBot.reset();
        triggerIdentity.reset();
        blockHit.reset();
        SessionServices session = host.services();
        if (!session.connected()) return;
        session.rotations().cancel(AIM_ID);
        session.actions().cancelOwner(CLICK_ID);
        session.actions().cancelOwner(TRIGGER_ID);
        session.actions().cancelOwner(BLOCK_ID);
    }
}
