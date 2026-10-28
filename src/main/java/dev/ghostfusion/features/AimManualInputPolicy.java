package dev.ghostfusion.features;

/**
 * Optional owner-thread Aim Assist manual mouse precedence.
 *
 * Uses the already-sampled native Minecraft mouse-helper deltas; performs no
 * mouse polling, synthesis or camera writes. A real physical mouse movement
 * at/above the configured threshold suppresses optional Aim Assist camera
 * output for this tick and a bounded consecutive-tick hold. Disabled by
 * default so legacy aim behavior remains unchanged.
 */
public final class AimManualInputPolicy {
    private final Thread owner = Thread.currentThread();
    private long generation=-1, lastTick=-1, lastManualTick=-1;
    private int oldThreshold=-1, oldPause=-1;
    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("manual aim owner thread");
    }
    public boolean suppressed(long epoch,long tick,boolean enabled,
                              int deltaX,int deltaY,int threshold,
                              int pauseTicks) {
        checkOwner();
        if(epoch<0||tick<0||threshold<1||threshold>64||
           pauseTicks<1||pauseTicks>20)
            throw new IllegalArgumentException("manual aim settings/time");
        if(generation!=epoch) {
            lastTick=lastManualTick=-1;
            oldThreshold=oldPause=-1;
            generation=epoch;
        }
        if(lastTick>=0&&tick<lastTick)
            throw new IllegalArgumentException("backward manual mouse tick");
        lastTick=tick;
        if(!enabled) {
            lastManualTick=-1;
            oldThreshold=oldPause=-1;
            return false;
        }
        if(threshold!=oldThreshold||pauseTicks!=oldPause) {
            lastManualTick=-1;
            oldThreshold=threshold;
            oldPause=pauseTicks;
        }
        // Cast before abs: Integer.MIN_VALUE must not wrap to a negative.
        boolean physical=Math.abs((long)deltaX)>=threshold ||
                         Math.abs((long)deltaY)>=threshold;
        if(physical)lastManualTick=tick;
        return lastManualTick>=0 && tick-lastManualTick<pauseTicks;
    }
    /** World, GUI, Panic, focus or disabled module transition. */
    public void reset() {
        checkOwner();
        generation=lastTick=lastManualTick=-1;
        oldThreshold=oldPause=-1;
    }
}
