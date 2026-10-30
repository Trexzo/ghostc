package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread camera handback admission after Aim Assist yielded to a
 * genuine TriggerBot native-crosshair candidate. A handback does not gain
 * authority to aim at a new player merely because AimTargetSwitchPolicy
 * was reset during the yield.
 *
 * Only the Forge adapter performs camera writes after rechecking native
 * entity/world identity, current pose, range, visibility and rotation
 * ownership. This policy never reads/writes Minecraft state or packets.
 */
public final class AimCameraReturnFence {
    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1,candidateSince=-1,stagedTick=-1;
    private int candidateId=-1,previousDelay=-1;
    private UUID candidateUuid;
    private Object candidateEntity,candidateWorld;
    private boolean awaitingReturn;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("Aim return owner thread");
    }
    private void changeGeneration(long epoch) {
        if(generation!=epoch) {
            resetState();generation=epoch;
        }
    }
    private void clock(long epoch,long tick) {
        if(epoch<0||tick<0)
            throw new IllegalArgumentException("Aim return epoch/tick");
        changeGeneration(epoch);
        if(lastTick>=0&&tick<=lastTick)
            throw new IllegalArgumentException("Aim return duplicate/backward tick");
        lastTick=tick;
        stagedTick=-1;
    }
    private void clearCandidate() {
        candidateSince=-1;
        candidateId=-1;
        candidateUuid=null;
        candidateEntity=candidateWorld=null;
        stagedTick=-1;
    }
    private void resetState() {
        awaitingReturn=false;lastTick=-1;previousDelay=-1;
        clearCandidate();
    }
    /** Called once per camera-yield END frame; no writes are permitted. */
    public void yieldCamera(long epoch,long tick) {
        check();clock(epoch,tick);
        awaitingReturn=true;
        clearCandidate();
    }
    /**
     * Native target is already fully validated, including exact object and
     * world identity. A zero switch delay preserves legacy instant return.
     * For an opt-in positive delay, consecutive eligible frames must name
     * the SAME exact Minecraft entity object and world.
     */
    public boolean consider(long epoch,long tick,int id,UUID uuid,
                            Object entity,Object world,int delay) {
        check();clock(epoch,tick);
        if(id<0||uuid==null||entity==null||world==null||
            delay<0||delay>10)
            throw new IllegalArgumentException("Aim return native candidate");
        if(!awaitingReturn)return true;
        if(previousDelay!=delay) {
            clearCandidate();
            previousDelay=delay;
        }
        if(delay==0) {
            stagedTick=tick;
            return true;
        }
        if(candidateSince<0||candidateId!=id||!uuid.equals(candidateUuid)||
           candidateEntity!=entity||candidateWorld!=world) {
            candidateSince=tick;
            candidateId=id;candidateUuid=uuid;
            candidateEntity=entity;candidateWorld=world;
            return false;
        }
        if(tick-candidateSince<delay)return false;
        stagedTick=tick;
        return true;
    }
    /**
     * Candidate disappeared or manual camera input took priority. Do not
     * inherit any previous handback dwell; still require reacquisition.
     */
    public void interrupt() {
        check();clearCandidate();
    }
    /** A genuine winning native camera rotation is the only commit point. */
    public void applied(long epoch,long tick) {
        check();
        if(generation!=epoch||lastTick!=tick||stagedTick!=tick||
           !awaitingReturn)
            throw new IllegalStateException("Aim return not prepared");
        awaitingReturn=false;
        clearCandidate();
    }
    public boolean pending() {check();return awaitingReturn;}
    public void reset() {
        check();resetState();generation=-1;
    }
}
