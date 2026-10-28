package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Read-only, owner-thread target acquisition state machine for TriggerBot.
 * This policy NEVER sends Minecraft input. A successful candidate still
 * requires ActionCoordinator ownership, Hit Select and fresh native validation.
 *
 * IDLE -> ACQUIRING -> READY -> COOLDOWN. Target loss, identity reuse, world
 * change and physical attack hold discard acquisition rather than retaining
 * a delayed click. Cooldown is committed only after a native key pulse.
 */
public final class TriggerBotPolicy {
    public enum State { IDLE, ACQUIRING, READY, COOLDOWN }
    private final Thread owner=Thread.currentThread();
    private long generation=-1, lastTick=-1, firstSeen=-1, lastDispatched=-1;
    private long readyTick=-1;
    private UUID identity;
    private int previousAcquire=-1;
    private State state=State.IDLE;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("triggerbot owner thread");
    }

    public State state() { check();return state; }

    /**
     * Called once per native combat tick with a live, UUID-verified target.
     * active=false also covers a physical attack hold or unsafe native state.
     * Never borrows acquisition time from another player or another world.
     */
    public boolean due(long epoch,long tick,boolean active,UUID target,
                       int acquireTicks,int cooldownTicks) {
        check();
        if(epoch<0||tick<0||acquireTicks<1||acquireTicks>20||
           cooldownTicks<1||cooldownTicks>40)
            throw new IllegalArgumentException("triggerbot timing/epoch");
        if(generation!=epoch) {
            resetState();generation=epoch;
        }
        if(lastTick>=0&&tick<lastTick)
            throw new IllegalArgumentException("backward triggerbot tick");
        lastTick=tick;
        readyTick=-1;
        if(!active||target==null) {
            identity=null;firstSeen=-1;state=State.IDLE;
            return false;
        }
        if(!target.equals(identity)||previousAcquire!=acquireTicks) {
            identity=target;firstSeen=tick;previousAcquire=acquireTicks;
            state=State.ACQUIRING;
            return false;
        }
        if(tick-firstSeen<acquireTicks) {
            state=State.ACQUIRING;
            return false;
        }
        if(lastDispatched>=0 && tick-lastDispatched<cooldownTicks) {
            state=State.COOLDOWN;
            return false;
        }
        if(lastDispatched==tick) {
            state=State.COOLDOWN;
            return false;
        }
        readyTick=tick;
        state=State.READY;
        return true;
    }

    /** Commit exactly one native dispatch, never an intent or denied hit. */
    public void dispatched(long epoch,long tick,UUID target) {
        check();
        if(target==null||generation!=epoch||lastTick!=tick||
           readyTick!=tick||!target.equals(identity)||state!=State.READY)
            throw new IllegalStateException("triggerbot dispatch not authorized");
        lastDispatched=tick;
        readyTick=-1;
        state=State.COOLDOWN;
    }

    private void resetState() {
        identity=null;firstSeen=-1;lastDispatched=-1;lastTick=-1;
        readyTick=-1;previousAcquire=-1;state=State.IDLE;
    }
    public void reset() {
        check();resetState();generation=-1;
    }
}
