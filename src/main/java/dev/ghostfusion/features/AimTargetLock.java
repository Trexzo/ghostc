package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread target retention for the opt-in Aim Assist. The lock is always
 * a hint, never permission to bypass FOV/range, visibility or live identity
 * checks. Reusing an entity ID with a different UUID invalidates the hint.
 */
public final class AimTargetLock {
    private final Thread owner = Thread.currentThread();
    private long generation = -1L, lastTick = -1L;
    private int entityId = -1;
    private UUID identity;

    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("aim lock owner thread");
    }

    /** Return the retained entity ID or -1 when disabled/expired. */
    public int preferred(long epoch, long tick, boolean enabled) {
        checkOwner();
        if (epoch < 0 || tick < 0)
            throw new IllegalArgumentException("aim lock clock");
        if (generation != epoch || (lastTick >= 0 && tick < lastTick)) {
            entityId = -1;
            identity = null;
            generation = epoch;
        }
        lastTick = tick;
        if (!enabled) {
            entityId = -1;
            identity = null;
            return -1;
        }
        return entityId;
    }

    public boolean sameIdentity(UUID current) {
        checkOwner();
        return identity != null && identity.equals(current);
    }

    /**
     * Commit only after the Forge live-identity/freshness gate and rotation
     * owner arbitration actually apply a camera movement this tick.
     */
    public void applied(long epoch, long tick, int id, UUID uuid) {
        checkOwner();
        if (epoch < 0 || tick < 0 || id < 0 || uuid == null ||
            generation != epoch || lastTick != tick)
            throw new IllegalArgumentException("unverified aim lock commit");
        entityId = id;
        identity = uuid;
    }

    /** No valid target / no owning camera decision / disabled module. */
    public void clear() {
        checkOwner();
        entityId = -1;
        identity = null;
    }

    /** World, focus, input, or module reset. */
    public void reset() {
        checkOwner();
        generation = -1L;
        lastTick = -1L;
        entityId = -1;
        identity = null;
    }
}
