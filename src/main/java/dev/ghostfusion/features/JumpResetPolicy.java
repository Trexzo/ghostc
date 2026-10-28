package dev.ghostfusion.features;

/**
 * One-shot local jump on a FRESH Minecraft damage/hurt-time rising edge.
 * Pure owner-thread admission: no native writes, packet edits or key spoofing.
 *
 * ARMING -> READY -> PREPARED -> COOLDOWN. On first observation in a world,
 * seed the hurt baseline without firing: login/reconnect with existing hurt
 * time must never cause a synthetic jump. All non-eligible frames still
 * advance the hurt baseline so delayed eligibility cannot replay old damage.
 */
public final class JumpResetPolicy {
    public enum State { ARMING, READY, PREPARED, COOLDOWN }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1, lastTick=-1, lastCommitted=-1, stagedTick=-1;
    private int previousHurt=-1, previousCooldown=-1, previousMinHurt=-1;
    private State state=State.ARMING;
    private void owner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("jump reset owner thread");
    }
    public State state() {owner();return state;}
    /**
     * Called once per original-game client tick, after velocity/WTap
     * admission. active means all native safety and manual-input checks
     * passed this exact tick. No jump is saved for a future eligible tick.
     */
    public boolean preview(long generation,long tick,int hurtTime,
                           boolean active,int minimumHurt,int cooldownTicks) {
        owner();
        if(generation<0||tick<0||hurtTime<0||hurtTime>20||
           minimumHurt<1||minimumHurt>10||
           cooldownTicks<2||cooldownTicks>40)
            throw new IllegalArgumentException("invalid jump reset sample");
        if(epoch!=generation) {
            resetState();
            epoch=generation;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("jump reset backward tick");
        if(lastTick==tick) return false; // duplicate END-tick callbacks never jump
        lastTick=tick;
        stagedTick=-1;
        if(previousMinHurt!=minimumHurt || previousCooldown!=cooldownTicks) {
            // A live settings edit cannot inherit prior damage/cooldown state.
            previousHurt=hurtTime;
            previousMinHurt=minimumHurt;
            previousCooldown=cooldownTicks;
            state=State.ARMING;
            return false;
        }
        if(previousHurt<0) {
            previousHurt=hurtTime;
            state=State.ARMING;
            return false;
        }
        boolean rising=hurtTime>previousHurt && hurtTime>=minimumHurt;
        previousHurt=hurtTime;
        if(!active) {
            state=State.READY;
            return false;
        }
        if(lastCommitted>=0 && tick-lastCommitted<cooldownTicks) {
            state=State.COOLDOWN;
            return false;
        }
        state=State.READY;
        if(!rising)return false;
        stagedTick=tick;
        state=State.PREPARED;
        return true;
    }
    /** Commit only AFTER a genuine EntityPlayerSP.jump() invocation. */
    public void committed(long generation,long tick) {
        owner();
        if(epoch!=generation || tick!=lastTick || stagedTick!=tick ||
           state!=State.PREPARED)
            throw new IllegalStateException("jump reset unprepared commit");
        lastCommitted=tick;
        stagedTick=-1;
        state=State.COOLDOWN;
    }
    /** Native jump denied/faulted; do not pretend that it succeeded. */
    public void cancelPrepared() {
        owner();
        stagedTick=-1;
        if(state==State.PREPARED)state=State.READY;
    }
    private void resetState() {
        lastTick=lastCommitted=stagedTick=-1;
        previousHurt=previousCooldown=previousMinHurt=-1;
        state=State.ARMING;
    }
    public void reset() {
        owner();
        resetState();
        epoch=-1;
    }
}
