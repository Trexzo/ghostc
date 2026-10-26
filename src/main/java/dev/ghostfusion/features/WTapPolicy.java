package dev.ghostfusion.features;

/**
 * Owner-thread one-shot sprint reset after an actual native attack event.
 * No packet rewriting and no synthetic movement key presses.
 */
public final class WTapPolicy {
    public enum Decision { NONE, HOLD, RESTORE }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1,lastAttack=-1,restoreAt=-1;
    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("wtap owner thread");
    }
    private void clock(long tick,long generation) {
        checkOwner();
        if(tick<0 || generation<0)
            throw new IllegalArgumentException("wtap clock");
        if(epoch!=generation) {
            epoch=generation;lastTick=-1;lastAttack=-1;restoreAt=-1;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("nonmonotonic wtap tick");
        lastTick=tick;
    }
    /** Returns true only for a distinct, eligible real attack event. */
    public boolean attack(boolean active,boolean eligible,boolean sprinting,
                          int delayTicks,long tick,long generation) {
        if(delayTicks<1 || delayTicks>3)
            throw new IllegalArgumentException("wtap sprint delay");
        clock(tick,generation);
        if(!active || !eligible || !sprinting || restoreAt>=0 ||
           lastAttack==tick) return false;
        if(tick>Long.MAX_VALUE-delayTicks)
            throw new IllegalArgumentException("wtap tick exhaustion");
        lastAttack=tick;restoreAt=tick+delayTicks;
        return true;
    }
    /** The native adapter evaluates movement eligibility each tick. */
    public Decision tick(boolean active,boolean eligible,long tick,long generation) {
        clock(tick,generation);
        if(!active || !eligible) {
            restoreAt=-1;
            return Decision.NONE;
        }
        if(restoreAt<0)return Decision.NONE;
        if(tick<restoreAt)return Decision.HOLD;
        restoreAt=-1;
        return Decision.RESTORE;
    }
    public void reset() {
        checkOwner();
        epoch=-1;lastTick=-1;lastAttack=-1;restoreAt=-1;
    }
}
