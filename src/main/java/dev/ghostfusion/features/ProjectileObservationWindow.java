package dev.ghostfusion.features;

/**
 * Read-only, owner-thread, bounded observations AFTER a native projectile-use
 * method has returned. This does not extend hotbar ownership or authorize
 * retry, inventory mutation, target selection or any game/network action.
 *
 * A local hook or inventory change is CLIENT-VISIBLE EVIDENCE ONLY. It is
 * not a server ACK, confirmed projectile spawn, collision or PvP success.
 */
public final class ProjectileObservationWindow {
    public enum Signal { NONE, ROD_HOOK_VISIBLE, SNOWBALL_COUNT_DECREASED,
                         INVENTORY_REPLACED }
    public enum Outcome { NONE, PENDING, LOCAL_HOOK, LOCAL_DECREASE,
                          AMBIGUOUS_REPLACEMENT, EXPIRED, INVALIDATED }
    public static final int MAX_TICKS=8;
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,castTick=-1,lastTick=-1;
    private boolean active;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("projectile observation owner thread");
    }
    public boolean pending() {check();return active;}
    public void arm(long generation,long tick) {
        check();
        if(generation<0||tick<0)
            throw new IllegalArgumentException("invalid observation generation/tick");
        // One bounded window per projectile module. Later legitimate uses
        // supersede old inconclusive observations without retrying anything.
        epoch=generation;castTick=lastTick=tick;active=true;
    }
    public Outcome observe(long generation,long tick,boolean sameWorldPlayer,
                           Signal signal) {
        check();
        if(signal==null)throw new IllegalArgumentException("null observation");
        if(!active)return Outcome.NONE;
        if(!sameWorldPlayer||generation!=epoch) {
            active=false;return Outcome.INVALIDATED;
        }
        if(tick<lastTick)throw new IllegalArgumentException("backward observation tick");
        lastTick=tick;
        // Never infer a confirmation from the same tick as sendUseItem.
        if(tick<=castTick)return Outcome.PENDING;
        if(tick-castTick>MAX_TICKS) {
            active=false;return Outcome.EXPIRED;
        }
        switch(signal) {
            case ROD_HOOK_VISIBLE:active=false;return Outcome.LOCAL_HOOK;
            case SNOWBALL_COUNT_DECREASED:active=false;return Outcome.LOCAL_DECREASE;
            case INVENTORY_REPLACED:active=false;return Outcome.AMBIGUOUS_REPLACEMENT;
            default:return Outcome.PENDING;
        }
    }
    public void reset() {check();active=false;epoch=castTick=lastTick=-1;}
}
