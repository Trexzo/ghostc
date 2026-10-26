package dev.ghostfusion.features;

/**
 * Conservative cadence gate for sword-use pulses while attack is held.
 * An extra attack pulse has priority; Block Hit retries on a later tick.
 */
public final class BlockHitPolicy {
    private final Thread thread=Thread.currentThread();
    private long generation=-1, lastTick=-1, lastPulse=-1;
    private long pendingSince=-1;
    private int previousSelfHurt;
    private boolean armed;
    private String lastMode="interval";
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
    /**
     * Optional event-triggered modes; vanilla interval mode retains the exact
     * original scheduler. "post_attack" observes only a QUEUED extra click
     * pulse, not an unobserved manual attack. "on_damage" uses the local
     * player's rising hurt-time edge, never attempts prediction.
     *
     * A pending pulse can be emitted only from a subsequent tick, expires
     * after three ticks, and never shares a tick with an extra attack.
     */
    public boolean due(boolean active, boolean attackHeld, boolean vanillaUseHeld,
                       boolean swordEquipped, boolean targetPresent, boolean requireTarget,
                       boolean attackPulseQueued, int intervalTicks,
                       int selfHurtTime, String mode, long tick, long epoch) {
        owner();
        if(mode==null || (!"interval".equals(mode) && !"post_attack".equals(mode) &&
            !"on_damage".equals(mode)) || selfHurtTime<0 || selfHurtTime>20 ||
            tick<0 || epoch<0 || intervalTicks<2 || intervalTicks>20)
            throw new IllegalArgumentException("invalid block-hit mode/timing");
        if(!mode.equals(lastMode)) {
            lastMode=mode;
            generation=-1;
            lastTick=-1;
            lastPulse=-1;
            pendingSince=-1;
            previousSelfHurt=0;
            armed=false;
        }
        if("interval".equals(mode)) {
            return due(active,attackHeld,vanillaUseHeld,swordEquipped,
                targetPresent,requireTarget,attackPulseQueued,intervalTicks,tick,epoch);
        }
        if(epoch!=generation) {
            generation=epoch;
            lastTick=-1; lastPulse=-1; pendingSince=-1;
            previousSelfHurt=0; armed=false;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("non-monotonic tick");
        lastTick=tick;
        boolean eligible=active && attackHeld && !vanillaUseHeld &&
            swordEquipped && (!requireTarget || targetPresent);
        if(!eligible) {
            armed=false;
            pendingSince=-1;
            lastPulse=-1;
            previousSelfHurt=selfHurtTime;
            return false;
        }
        if(!armed) {
            armed=true;
            previousSelfHurt=selfHurtTime;
            // The first held press remains owned by Minecraft, but a
            // newly queued extra attack may arm a future post-attack pulse.
            if("post_attack".equals(mode) && attackPulseQueued)
                pendingSince=tick;
            return false;
        }
        if("post_attack".equals(mode) && attackPulseQueued)
            pendingSince=tick;
        else if("on_damage".equals(mode) && selfHurtTime>previousSelfHurt &&
                selfHurtTime>0)
            pendingSince=tick;
        previousSelfHurt=selfHurtTime;
        if(pendingSince<0) return false;
        long age=tick-pendingSince;
        if(age>3) { pendingSince=-1; return false; }
        if(age==0 || attackPulseQueued) return false;
        if(lastPulse>=0 && tick-lastPulse<intervalTicks) return false;
        pendingSince=-1;
        lastPulse=tick;
        return true;
    }
    public void reset() {
        owner(); generation=-1; lastTick=-1; lastPulse=-1;
        pendingSince=-1; previousSelfHurt=0; armed=false; lastMode="interval";
    }
}
