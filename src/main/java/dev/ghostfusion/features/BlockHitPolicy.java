package dev.ghostfusion.features;

/**
 * Conservative cadence gate for sword-use pulses while attack is held.
 * An extra attack pulse has priority; Block Hit retries on a later tick.
 */
public final class BlockHitPolicy {
    private final Thread thread=Thread.currentThread();
    private long generation=-1, lastTick=-1, lastPulse=-1;
    private void owner() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("block-hit owner");
    }
    public boolean due(boolean active, boolean attackHeld, boolean vanillaUseHeld,
                       boolean swordEquipped, boolean targetPresent, boolean requireTarget,
                       boolean attackPulseQueued, int intervalTicks, long tick, long epoch) {
        owner();
        if(tick<0 || epoch<0 || intervalTicks<2 || intervalTicks>20)
            throw new IllegalArgumentException("invalid block-hit timing");
        if(epoch!=generation) {
            generation=epoch; lastTick=-1; lastPulse=-1;
        }
        if(lastTick>=0 && tick<lastTick) throw new IllegalArgumentException("non-monotonic tick");
        lastTick=tick;
        if(!active || !attackHeld || vanillaUseHeld || !swordEquipped ||
            (requireTarget && !targetPresent)) {
            lastPulse=-1;
            return false;
        }
        if(lastPulse<0) {
            lastPulse=tick; // first press handled by vanilla
            return false;
        }
        if(tick-lastPulse<intervalTicks) return false;
        if(attackPulseQueued) return false; // delay, never double-pulse the same tick
        lastPulse=tick;
        return true;
    }
    public void reset() {
        owner(); generation=-1; lastTick=-1; lastPulse=-1;
    }
}
