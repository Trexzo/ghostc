package dev.ghostfusion.features;

/**
 * Optional gate for EXTRA AutoClicker pulses only. It never suppresses vanilla
 * manual attacks and never mutates Minecraft entities, packets or key states.
 */
public final class HitSelectPolicy {
    private final Thread thread = Thread.currentThread();
    private long generation = -1, lastTick = -1, lastAccepted = -1;
    private void owner() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("hit-select owner");
    }
    public boolean accept(boolean active, boolean targetPresent, int hurtTime,
                          int allowedHurtTime, int minimumGapTicks, long tick, long epoch) {
        owner();
        if (tick < 0 || epoch < 0 || allowedHurtTime < 0 || allowedHurtTime > 10 ||
            minimumGapTicks < 1 || minimumGapTicks > 20 || hurtTime < 0)
            throw new IllegalArgumentException("invalid hit-select state");
        if (epoch != generation) {
            generation = epoch; lastTick = -1; lastAccepted = -1;
        }
        if (lastTick >= 0 && tick < lastTick) throw new IllegalArgumentException("non-monotonic tick");
        lastTick = tick;
        if (!active || !targetPresent || hurtTime > allowedHurtTime) return false;
        if (lastAccepted >= 0 && tick - lastAccepted < minimumGapTicks) return false;
        lastAccepted = tick;
        return true;
    }
    public void reset() {
        owner(); generation = -1; lastTick = -1; lastAccepted = -1;
    }
}
