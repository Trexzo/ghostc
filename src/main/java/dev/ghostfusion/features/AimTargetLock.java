package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread target retention for opt-in Aim Assist. A retained ID is a
 * hint, never permission to bypass live world/FOV/range/visibility checks.
 */
public final class AimTargetLock {
    private final Thread owner=Thread.currentThread();
    private long generation=-1L,lastTick=-1L;
    private int entityId=-1;
    private UUID identity;
    private Object nativeEntity,nativeWorld;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("aim lock owner thread");
    }
    public int preferred(long epoch,long tick,boolean enabled) {
        checkOwner();
        if(epoch<0||tick<0)
            throw new IllegalArgumentException("aim lock clock");
        if(generation!=epoch||(lastTick>=0&&tick<lastTick)) {
            clear();generation=epoch;
        }
        lastTick=tick;
        if(!enabled) {clear();return -1;}
        return entityId;
    }
    public boolean sameIdentity(UUID current) {
        checkOwner();
        return identity!=null && identity.equals(current);
    }
    /** Native hard lock rejects replaced objects even when UUID+ID match. */
    public boolean sameNativeIdentity(UUID current,Object entity,Object world) {
        checkOwner();
        return sameIdentity(current) && entity!=null && world!=null &&
            nativeEntity==entity && nativeWorld==world;
    }
    /** Backward-compatible UUID/ID-only commit for legacy policy callers. */
    public void applied(long epoch,long tick,int id,UUID uuid) {
        checkOwner();
        if(epoch<0||tick<0||id<0||uuid==null||
           generation!=epoch||lastTick!=tick)
            throw new IllegalArgumentException("unverified aim lock commit");
        entityId=id;identity=uuid;
        nativeEntity=nativeWorld=null;
    }
    public void appliedNative(long epoch,long tick,int id,UUID uuid,
                              Object entity,Object world) {
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native aim lock");
        applied(epoch,tick,id,uuid);
        nativeEntity=entity;nativeWorld=world;
    }
    public void clear() {
        checkOwner();
        entityId=-1;identity=null;nativeEntity=nativeWorld=null;
    }
    public void reset() {
        checkOwner();
        generation=lastTick=-1L;
        clear();
    }
}
