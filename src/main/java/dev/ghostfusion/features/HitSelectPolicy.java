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
    // Bind final-output validation to the exact previewed configuration.
    // A settings edit inside a tick may veto but cannot silently relax a
    // staged attack or erase a prior completed native cooldown.
    private int pendingHurtLimit=-1,pendingGap=-1,pendingTradeMin=-1;
    private String pendingMode;
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
        cancelPrepared();
        if (!active || !targetPresent || hurtTime > allowedHurtTime) return false;
        if ("ready".equals(mode) && hurtTime != 0) return false;
        if ("trade".equals(mode) && selfHurtTime < tradeMinSelfHurt) return false;
        if (lastAccepted >= 0 &&
            Long.compareUnsigned(tick-lastAccepted,minimumGapTicks)<0) return false;
        pendingTick=tick;
        pendingHurtLimit=allowedHurtTime;
        pendingGap=minimumGapTicks;
        pendingTradeMin=tradeMinSelfHurt;
        pendingMode=mode;
        return true;
    }
    /**
     * Second, FINAL validation immediately before native KeyBinding output.
     * It never starts a new preview or commits an attack; only the exact
     * approved session/tick/configuration can pass. Fresh target hurtTime,
     * self hurtTime and target presence must STILL satisfy the original
     * damage-mode constraints. Failure revokes the pending token, while
     * preserving lastAccepted and the prior real native cooldown.
     */
    public boolean confirmPrepared(boolean active,boolean targetPresent,
                                   int hurtTime,int allowedHurtTime,
                                   int minimumGapTicks,int selfHurtTime,
                                   String mode,int tradeMinSelfHurt,
                                   long tick,long epoch) {
        owner();
        boolean valid=pendingTick>=0 && tick>=0 &&
            tick==pendingTick && tick==lastTick &&
            epoch>=0 && epoch==generation && pendingMode!=null &&
            pendingMode.equals(mode) && lastMode.equals(mode) &&
            allowedHurtTime==pendingHurtLimit &&
            minimumGapTicks==pendingGap &&
            tradeMinSelfHurt==pendingTradeMin &&
            active && targetPresent && hurtTime>=0 &&
            selfHurtTime>=0 && selfHurtTime<=20 &&
            hurtTime<=pendingHurtLimit &&
            (!"ready".equals(pendingMode) || hurtTime==0) &&
            (!"trade".equals(pendingMode) || selfHurtTime>=pendingTradeMin) &&
            (lastAccepted<0 ||
                Long.compareUnsigned(tick-lastAccepted,pendingGap)>=0);
        if(!valid)cancelPrepared();
        return valid;
    }

    /** Commit only after a real KeyBinding.onTick returned successfully. */
    public void committed(long tick,long epoch) {
        owner();
        if(tick<0||epoch<0||epoch!=generation||tick!=lastTick||
           tick!=pendingTick || lastAccepted==tick)
            throw new IllegalStateException("hit-select native dispatch not staged");
        lastAccepted=tick;
        cancelPrepared();
    }
    /** An arbitrated candidate was denied by the final native safety gate. */
    public void cancelPrepared() {
        owner();
        pendingTick=-1;
        pendingMode=null;
        pendingHurtLimit=pendingGap=pendingTradeMin=-1;
    }
    public void reset() {
        owner();
        generation=-1;lastTick=-1;lastAccepted=-1;
        cancelPrepared();
        lastMode="normal";
    }
}
