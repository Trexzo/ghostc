package dev.ghostfusion.features;

/**
 * Owner-thread sprint reset which begins ONLY AFTER the original attack
 * transaction tick. Never clears sprint before vanilla computes sprint-hit
 * knockback; never injects packets or synthetic movement key presses.
 */
public final class WTapPolicy {
    public enum Decision { NONE, RESET, HOLD, RESTORE }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1,lastAttack=-1,resetAt=-1,restoreAt=-1;
    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("wtap owner thread");
    }
    private void clock(long tick,long generation) {
        checkOwner();
        if(tick<0 || generation<0)
            throw new IllegalArgumentException("wtap clock");
        if(epoch!=generation) {
            epoch=generation;lastTick=-1;lastAttack=-1;resetAt=-1;restoreAt=-1;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("nonmonotonic wtap tick");
        lastTick=tick;
    }
    /** Observe a real attack, but DO NOT write sprint during this callback.
     * Reset begins on the following client tick and lasts 1–3 ticks. */
    public boolean attack(boolean active,boolean eligible,boolean sprinting,
                          int delayTicks,long tick,long generation) {
        if(delayTicks<1 || delayTicks>3)
            throw new IllegalArgumentException("wtap sprint delay");
        clock(tick,generation);
        if(!active || !eligible || !sprinting || restoreAt>=0 ||
           lastAttack==tick) return false;
        if(tick>Long.MAX_VALUE-delayTicks-1)
            throw new IllegalArgumentException("wtap tick exhaustion");
        lastAttack=tick;
        resetAt=tick+1;
        restoreAt=resetAt+delayTicks;
        return true;
    }
    /** Only the native adapter mutates player sprint state on a returned
     * RESET/HOLD/RESTORE. Skipped reset ticks never cause delayed resets. */
    public Decision tick(boolean active,boolean eligible,long tick,long generation) {
        clock(tick,generation);
        if(!active || !eligible) {
            resetAt=-1;restoreAt=-1;
            return Decision.NONE;
        }
        if(resetAt>=0) {
            if(tick<resetAt)return Decision.NONE; // original attack still running
            if(tick>resetAt) {
                // A skipped native tick invalidates delayed sprint writes.
                resetAt=-1;restoreAt=-1;
                return Decision.NONE;
            }
            resetAt=-1;
            return Decision.RESET;
        }
        if(restoreAt<0)return Decision.NONE;
        if(tick<restoreAt)return Decision.HOLD;
        restoreAt=-1;
        return Decision.RESTORE;
    }
    public void reset() {
        checkOwner();
        epoch=-1;lastTick=-1;lastAttack=-1;resetAt=-1;restoreAt=-1;
    }
}
