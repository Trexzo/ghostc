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
    // Never serialized. Vanilla native player and world object identities
    // must remain exact for a hard target lock to retain its camera hint.
    private Object nativeEntity,nativeWorld;

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
            clear();
            generation = epoch;
        }
        lastTick = tick;
        if (!enabled) {
            clear();
            return -1;
        }
        return entityId;
    }

    public boolean sameIdentity(UUID current) {
        checkOwner();
        return identity != null && identity.equals(current);
    }
    /** A matching UUID/ID alone does NOT prove an unchanged native player. */
    public boolean sameNativeIdentity(UUID current,Object entity,Object world) {
        checkOwner();
        return entity!=null&&world!=null&&sameIdentity(current)&&
            entity==nativeEntity&&world==nativeWorld;
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
        nativeEntity=nativeWorld=null;
    }

    /** Only a completed native camera rotation may retain the real entity. */
    public void appliedNative(long epoch,long tick,int id,UUID uuid,
                              Object entity,Object world) {
        checkOwner();
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native lock identity");
        applied(epoch,tick,id,uuid);
        nativeEntity=entity;nativeWorld=world;
    }

    /** No valid target / no owning camera decision / disabled module. */
    public void clear() {
        checkOwner();
        entityId = -1;
        identity = null;
        nativeEntity=nativeWorld=null;
    }

    /** World, focus, input, or module reset. */
    public void reset() {
        checkOwner();
        generation = -1L;
        lastTick = -1L;
        clear();
    }
}
