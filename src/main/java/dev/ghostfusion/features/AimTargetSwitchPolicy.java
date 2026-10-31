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
    // Exact native EntityPlayer and World references; never persisted.
    private Object appliedEntity,appliedWorld,candidateEntity,candidateWorld;
    private Object stagedEntity,stagedWorld;
    private boolean appliedNative,candidateNative,stagedNative;
    private int previousDelay = -1;

    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("aim switch owner thread");
    }
    private static boolean same(int leftId, UUID left, int rightId, UUID right) {
        return leftId == rightId && left != null && left.equals(right);
    }
    private static boolean sameTarget(int leftId,UUID left,Object entity,Object world,
                                      boolean nativeSample,int rightId,UUID right,
                                      Object savedEntity,Object savedWorld,boolean savedNative) {
        return same(leftId,left,rightId,right) && nativeSample==savedNative &&
            (!nativeSample || (entity==savedEntity && world==savedWorld));
    }
    private void clearCandidate() {
        candidateSince=-1L;candidateId=-1;candidateIdentity=null;
        candidateEntity=candidateWorld=null;candidateNative=false;
    }
    private void clearStaged() {
        stagedTick=-1L;stagedId=-1;stagedIdentity=null;
        stagedEntity=stagedWorld=null;stagedNative=false;
    }
    private void resetState() {
        appliedId=-1;appliedIdentity=null;
        appliedEntity=appliedWorld=null;appliedNative=false;
        clearCandidate();clearStaged();
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
        return considerInternal(epoch,tick,id,identity,null,null,false,switchDelayTicks);
    }
    /**
     * Genuine Minecraft-only extension: a retained UUID/entity ID does not
     * imply the SAME player if the native EntityPlayer object or World changed.
     * Zero delay remains the explicit legacy immediate-switch setting.
     */
    public boolean considerNative(long epoch,long tick,int id,UUID identity,
                                  Object entity,Object world,int switchDelayTicks) {
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native aim identity");
        return considerInternal(epoch,tick,id,identity,entity,world,true,switchDelayTicks);
    }
    private boolean considerInternal(long epoch,long tick,int id,UUID identity,
                                     Object entity,Object world,boolean nativeSample,
                                     int switchDelayTicks) {
        checkOwner();
        if (epoch < 0 || tick < 0 || id < 0 || identity == null ||
            switchDelayTicks < 0 || switchDelayTicks > 10)
            throw new IllegalArgumentException("invalid aim switch candidate");
        if (epoch != generation) {
            resetState();
            lastTick=-1L;
            generation=epoch;
        }
        if (lastTick >= 0 && tick < lastTick)
            throw new IllegalArgumentException("backward aim switch tick");
        lastTick=tick;
        clearStaged();

        // Live UI edits never borrow earlier dwell under different settings.
        if(switchDelayTicks!=previousDelay) {
            clearCandidate();
            previousDelay=switchDelayTicks;
        }
        if(sameTarget(id,identity,entity,world,nativeSample,
                appliedId,appliedIdentity,appliedEntity,appliedWorld,appliedNative) ||
           appliedIdentity==null || switchDelayTicks==0) {
            clearCandidate();
            return stage(tick,id,identity,entity,world,nativeSample);
        }
        if(!sameTarget(id,identity,entity,world,nativeSample,
                candidateId,candidateIdentity,candidateEntity,candidateWorld,candidateNative)) {
            candidateId=id;candidateIdentity=identity;candidateSince=tick;
            candidateEntity=entity;candidateWorld=world;candidateNative=nativeSample;
            return false;
        }
        if(tick-candidateSince<switchDelayTicks)return false;
        return stage(tick,id,identity,entity,world,nativeSample);
    }
    private boolean stage(long tick,int id,UUID identity,Object entity,Object world,
                          boolean nativeSample) {
        stagedTick=tick;stagedId=id;stagedIdentity=identity;
        stagedEntity=entity;stagedWorld=world;stagedNative=nativeSample;
        return true;
    }
    /** Legacy pure-core callers never claim native entity/world provenance. */
    public void applied(long epoch,long tick,int id,UUID identity) {
        commit(epoch,tick,id,identity,null,null,false);
    }
    /** A winning native CAMERA rotation alone commits exact entity retention. */
    public void appliedNative(long epoch,long tick,int id,UUID identity,
                              Object entity,Object world) {
        if(entity==null||world==null)
            throw new IllegalArgumentException("missing native aim commit");
        commit(epoch,tick,id,identity,entity,world,true);
    }
    private void commit(long epoch,long tick,int id,UUID identity,Object entity,
                        Object world,boolean nativeSample) {
        checkOwner();
        if(generation!=epoch||tick!=lastTick||stagedTick!=tick||
           !sameTarget(id,identity,entity,world,nativeSample,
                       stagedId,stagedIdentity,stagedEntity,stagedWorld,stagedNative))
            throw new IllegalStateException("unverified aim switch commit");
        appliedId=id;appliedIdentity=identity;
        appliedEntity=entity;appliedWorld=world;appliedNative=nativeSample;
        clearCandidate();clearStaged();
    }
    /** A rejected rotation never commits; candidate dwell is still pending. */
    public void cancelPrepared() {
        checkOwner();clearStaged();
    }
    /** Losing a live target, manual input grant or world resets all retention. */
    public void reset() {
        checkOwner();
        resetState();
        generation = lastTick = -1L;
    }
}
