package dev.ghostfusion.features;

/**
 * Optional gate for EXTRA AutoClicker pulses only. It never suppresses vanilla
 * manual attacks and never mutates Minecraft entities, packets or key states.
 */
public final class HitSelectPolicy {
    private final Thread thread = Thread.currentThread();
    private long generation = -1, lastTick = -1, lastAccepted = -1;
    private String lastMode = "normal";
    private void owner() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("hit-select owner");
    }
    public boolean accept(boolean active, boolean targetPresent, int hurtTime,
                          int allowedHurtTime, int minimumGapTicks, long tick, long epoch) {
        return accept(active,targetPresent,hurtTime,allowedHurtTime,
            minimumGapTicks,0,"normal",1,tick,epoch);
    }
    /**
     * Event-aware filters for extra AutoClicker pulses ONLY.
     * Normal retains legacy target-hurt window semantics; "ready" requires
     * target hurt-time zero; "trade" requires the local player to be in
     * their configured hurt-time window. The native manual click path is never
     * suppressed by this policy.
     */
    public boolean accept(boolean active, boolean targetPresent, int hurtTime,
                          int allowedHurtTime, int minimumGapTicks,
                          int selfHurtTime, String mode, int tradeMinSelfHurt,
                          long tick, long epoch) {
        owner();
        if(mode==null || (!"normal".equals(mode) && !"ready".equals(mode) &&
            !"trade".equals(mode)) || selfHurtTime<0 || selfHurtTime>20 ||
            tradeMinSelfHurt<1 || tradeMinSelfHurt>10)
            throw new IllegalArgumentException("invalid hit-select mode");
        if (tick < 0 || epoch < 0 || allowedHurtTime < 0 || allowedHurtTime > 10 ||
            minimumGapTicks < 1 || minimumGapTicks > 20 || hurtTime < 0)
            throw new IllegalArgumentException("invalid hit-select state");
        if (epoch != generation || !mode.equals(lastMode)) {
            generation = epoch; lastTick = -1; lastAccepted = -1;
            lastMode = mode;
        }
        if (lastTick >= 0 && tick < lastTick) throw new IllegalArgumentException("non-monotonic tick");
        lastTick = tick;
        if (!active || !targetPresent || hurtTime > allowedHurtTime) return false;
        if ("ready".equals(mode) && hurtTime != 0) return false;
        if ("trade".equals(mode) && selfHurtTime < tradeMinSelfHurt) return false;
        if (lastAccepted >= 0 && tick - lastAccepted < minimumGapTicks) return false;
        lastAccepted = tick;
        return true;
    }
    public void reset() {
        owner(); generation = -1; lastTick = -1; lastAccepted = -1; lastMode = "normal";
    }
}
