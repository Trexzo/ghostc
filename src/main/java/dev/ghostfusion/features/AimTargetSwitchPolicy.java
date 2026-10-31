package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread, generation-fenced Aim Assist target-switch admission.
 * A native candidate is keyed by UUID, numeric ID, exact entity object and
 * world object; a same-UUID respawn must not inherit previous camera dwell.
 * Legacy callers may continue to use UUID+ID-only methods.
 */
public final class AimTargetSwitchPolicy {
    private final Thread owner = Thread.currentThread();
    private long generation=-1L,lastTick=-1L,candidateSince=-1L,stagedTick=-1L;
    private int appliedId=-1,candidateId=-1,stagedId=-1,previousDelay=-1;
    private UUID appliedIdentity,candidateIdentity,stagedIdentity;
    private Object appliedEntity,appliedWorld,candidateEntity,candidateWorld;
    private Object stagedEntity,stagedWorld;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("aim switch owner thread");
    }
    private static boolean same(int a,UUID au,Object ae,Object aw,
                                int b,UUID bu,Object be,Object bw) {
        return a==b && au!=null && au.equals(bu) && ae==be && aw==bw;
    }
    private void resetState() {
        appliedId=candidateId=stagedId=-1;
        appliedIdentity=candidateIdentity=stagedIdentity=null;
        appliedEntity=appliedWorld=candidateEntity=candidateWorld=null;
        stagedEntity=stagedWorld=null;
        candidateSince=stagedTick=-1L;
        previousDelay=-1;
    }

    /** Backward-compatible UUID/ID-only admission for older callers. */
    public boolean consider(long epoch,long tick,int id,UUID identity,int delay) {
        return considerInternal(epoch,tick,id,identity,null,null,delay);
    }
    /** Live Forge caller supplies the exact native entity and world objects. */
    public boolean considerNative(long epoch,long tick,int id,UUID identity,
                                  Object entity,Object world,int delay) {
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native aim identity");
        return considerInternal(epoch,tick,id,identity,entity,world,delay);
    }
    private boolean considerInternal(long epoch,long tick,int id,UUID identity,
                                     Object entity,Object world,int delay) {
        checkOwner();
        if(epoch<0||tick<0||id<0||identity==null||delay<0||delay>10)
            throw new IllegalArgumentException("invalid aim switch candidate");
        if(epoch!=generation) {
            resetState();
            lastTick=-1L;
            generation=epoch;
        }
        if(lastTick>=0&&tick<lastTick)
            throw new IllegalArgumentException("backward aim switch tick");
        lastTick=tick;
        cancelPrepared();

        // Live setting edits must invalidate previously accumulated dwell.
        if(delay!=previousDelay) {
            candidateSince=-1L;
            candidateIdentity=null;
            candidateId=-1;
            candidateEntity=candidateWorld=null;
            previousDelay=delay;
        }
        if(same(id,identity,entity,world,appliedId,appliedIdentity,appliedEntity,appliedWorld) ||
           appliedIdentity==null || delay==0) {
            candidateSince=-1L;
            candidateId=-1;
            candidateIdentity=null;
            candidateEntity=candidateWorld=null;
            return stage(tick,id,identity,entity,world);
        }
        if(!same(id,identity,entity,world,
                 candidateId,candidateIdentity,candidateEntity,candidateWorld)) {
            candidateId=id;candidateIdentity=identity;
            candidateEntity=entity;candidateWorld=world;
            candidateSince=tick;
            return false;
        }
        if(tick-candidateSince<delay)return false;
        return stage(tick,id,identity,entity,world);
    }
    private boolean stage(long tick,int id,UUID identity,Object entity,Object world) {
        stagedTick=tick;stagedId=id;stagedIdentity=identity;
        stagedEntity=entity;stagedWorld=world;
        return true;
    }
    public void applied(long epoch,long tick,int id,UUID identity) {
        appliedInternal(epoch,tick,id,identity,null,null);
    }
    public void appliedNative(long epoch,long tick,int id,UUID identity,
                              Object entity,Object world) {
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native aim commit");
        appliedInternal(epoch,tick,id,identity,entity,world);
    }
    private void appliedInternal(long epoch,long tick,int id,UUID identity,
                                 Object entity,Object world) {
        checkOwner();
        if(generation!=epoch||lastTick!=tick||stagedTick!=tick||
           !same(id,identity,entity,world,
                 stagedId,stagedIdentity,stagedEntity,stagedWorld))
            throw new IllegalStateException("unverified aim switch commit");
        appliedId=id;appliedIdentity=identity;
        appliedEntity=entity;appliedWorld=world;
        stagedTick=candidateSince=-1L;
        stagedId=candidateId=-1;
        stagedIdentity=candidateIdentity=null;
        stagedEntity=stagedWorld=candidateEntity=candidateWorld=null;
    }
    public void cancelPrepared() {
        checkOwner();
        stagedTick=-1L;stagedId=-1;stagedIdentity=null;
        stagedEntity=stagedWorld=null;
    }
    public void reset() {
        checkOwner();
        resetState();generation=lastTick=-1L;
    }
}
