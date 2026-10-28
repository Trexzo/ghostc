package dev.ghostfusion.features;

/**
 * Optional gate for the single chosen synthetic combat attack (AutoClicker or
 * TriggerBot). It never suppresses vanilla manual attacks and never mutates
 * Minecraft entities, packets or key states.
 *
 * Native combat uses preview -> successful key pulse -> commit. A rejected
 * or failed native action must not consume the shared Hit Select cooldown.
 */
public final class HitSelectPolicy {
    private final Thread thread = Thread.currentThread();
    private long generation = -1, lastTick = -1, lastAccepted = -1, pendingTick = -1;
    private String lastMode = "normal";
    private void owner() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("hit-select owner");
    }
    /** Legacy atomic admission remains for existing callers and tests. */
    public boolean accept(boolean active, boolean targetPresent, int hurtTime,
                          int allowedHurtTime, int minimumGapTicks, long tick, long epoch) {
        return accept(active,targetPresent,hurtTime,allowedHurtTime,
            minimumGapTicks,0,"normal",1,tick,epoch);
    }
    /** Legacy atomic admission for tests and pure-Java consumers. */
    public boolean accept(boolean active, boolean targetPresent, int hurtTime,
                          int allowedHurtTime, int minimumGapTicks,
                          int selfHurtTime, String mode, int tradeMinSelfHurt,
                          long tick, long epoch) {
        if(!preview(active,targetPresent,hurtTime,allowedHurtTime,
                    minimumGapTicks,selfHurtTime,mode,tradeMinSelfHurt,tick,epoch))
            return false;
        committed(tick,epoch);
        return true;
    }
    /**
     * Stage a single eligible synthetic winner without consuming cooldown.
     * "ready" requires target hurt-time zero; "trade" requires self-hurt
     * above its configured threshold. Every phase is owner-thread fenced.
     */
    public boolean preview(boolean active, boolean targetPresent, int hurtTime,
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
        if (lastTick >= 0 && tick < lastTick)
            throw new IllegalArgumentException("non-monotonic tick");
        lastTick = tick;
        pendingTick = -1;
        if (!active || !targetPresent || hurtTime > allowedHurtTime) return false;
        if ("ready".equals(mode) && hurtTime != 0) return false;
        if ("trade".equals(mode) && selfHurtTime < tradeMinSelfHurt) return false;
        if (lastAccepted >= 0 &&
            Long.compareUnsigned(tick-lastAccepted,minimumGapTicks)<0) return false;
        pendingTick=tick;
        return true;
    }
    /** Commit only after a real KeyBinding.onTick returned successfully. */
    public void committed(long tick,long epoch) {
        owner();
        if(tick<0||epoch<0||epoch!=generation||tick!=lastTick||
           tick!=pendingTick || lastAccepted==tick)
            throw new IllegalStateException("hit-select native dispatch not staged");
        lastAccepted=tick;
        pendingTick=-1;
    }
    /** An arbitrated candidate was denied by the final native safety gate. */
    public void cancelPrepared() {
        owner();
        pendingTick=-1;
    }
    public void reset() {
        owner();
        generation=-1;lastTick=-1;lastAccepted=-1;pendingTick=-1;
        lastMode="normal";
    }
}
