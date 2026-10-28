package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread, generation-fenced Aim Assist target-switch admission.
 *
 * This policy does not select entities, rotate Minecraft, or retain a stale
 * target for output. A *new*, already live-validated UUID must remain the
 * selected candidate for the configured number of ticks before a camera write
 * can change targets. During that interval it grants NO camera write.
 *
 * A first target is immediate; 0 delay is strict legacy behavior. Existing
 * hard UUID target lock continues independently and takes precedence.
 */
public final class AimTargetSwitchPolicy {
    private final Thread owner = Thread.currentThread();
    private long generation = -1L, lastTick = -1L, candidateSince = -1L;
    private long stagedTick = -1L;
    private int appliedId = -1, candidateId = -1, stagedId = -1;
    private UUID appliedIdentity, candidateIdentity, stagedIdentity;
    private int previousDelay = -1;

    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("aim switch owner thread");
    }
    private static boolean same(int leftId, UUID left, int rightId, UUID right) {
        return leftId == rightId && left != null && left.equals(right);
    }
    private void resetState() {
        appliedId = candidateId = stagedId = -1;
        appliedIdentity = candidateIdentity = stagedIdentity = null;
        candidateSince = stagedTick = -1L;
        previousDelay = -1;
    }

    /**
     * Called only after SAME-TICK native source/target position, UUID and
     * world-identity validation. Return true to stage a *possible* camera
     * write; it becomes authoritative only when applied() is called after
     * the real RotationCoordinator camera output.
     */
    public boolean consider(long epoch, long tick, int id, UUID identity,
                            int switchDelayTicks) {
        checkOwner();
        if (epoch < 0 || tick < 0 || id < 0 || identity == null ||
            switchDelayTicks < 0 || switchDelayTicks > 10)
            throw new IllegalArgumentException("invalid aim switch candidate");
        if (epoch != generation) {
            resetState();
            lastTick = -1L;
            generation = epoch;
        }
        if (lastTick >= 0 && tick < lastTick)
            throw new IllegalArgumentException("backward aim switch tick");
        lastTick = tick;
        stagedTick = -1L;
        stagedIdentity = null;
        stagedId = -1;

        // A live UI edit must not inherit elapsed dwell from older settings.
        if (switchDelayTicks != previousDelay) {
            candidateSince = -1L;
            candidateIdentity = null;
            candidateId = -1;
            previousDelay = switchDelayTicks;
        }
        if (same(id, identity, appliedId, appliedIdentity) ||
            appliedIdentity == null || switchDelayTicks == 0) {
            candidateSince = -1L;
            candidateIdentity = null;
            candidateId = -1;
            return stage(tick,id,identity);
        }
        if (!same(id,identity,candidateId,candidateIdentity)) {
            candidateId = id;
            candidateIdentity = identity;
            candidateSince = tick;
            return false;
        }
        if (tick-candidateSince < switchDelayTicks) return false;
        return stage(tick,id,identity);
    }
    private boolean stage(long tick,int id,UUID identity) {
        stagedTick = tick;
        stagedId = id;
        stagedIdentity = identity;
        return true;
    }
    /** Only the completed native camera write may update the retained UUID. */
    public void applied(long epoch,long tick,int id,UUID identity) {
        checkOwner();
        if (generation != epoch || tick != lastTick ||
            stagedTick != tick || !same(id,identity,stagedId,stagedIdentity))
            throw new IllegalStateException("unverified aim switch commit");
        appliedId = id;
        appliedIdentity = identity;
        stagedTick = candidateSince = -1L;
        stagedId = candidateId = -1;
        stagedIdentity = candidateIdentity = null;
    }
    /** A rotation request was rejected: keep no stale prepared commit. */
    public void cancelPrepared() {
        checkOwner();
        stagedTick = -1L;
        stagedIdentity = null;
        stagedId = -1;
    }
    /** Losing a live target, manual input grant or world resets all retention. */
    public void reset() {
        checkOwner();
        resetState();
        generation = lastTick = -1L;
    }
}
