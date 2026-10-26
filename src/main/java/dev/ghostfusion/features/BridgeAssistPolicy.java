package dev.ghostfusion.features;

/**
 * Tick-based, owner-thread edge sneak policy; Minecraft collision queries and
 * keyboard state are supplied by the native adapter. Never mutates input
 * directly, never places blocks and never touches network packets.
 */
public final class BridgeAssistPolicy {
    private final Thread owner = Thread.currentThread();
    private long generation = -1, lastTick = -1, lastEdgeTick = -1;
    private boolean synthetic;
    private void checkOwner() {
        if(Thread.currentThread()!=owner)throw new IllegalStateException("bridge policy owner thread");
    }
    public boolean wantsSneak(boolean enabled,boolean manualSneak,
                              boolean onGround,boolean supportedNow,
                              boolean moving,boolean gapAhead,
                              int releaseDelayTicks,long tick,long epoch) {
        checkOwner();
        if(tick<0 || epoch<0 || releaseDelayTicks<0 || releaseDelayTicks>8)
            throw new IllegalArgumentException("bridge timing");
        if(generation!=epoch) {
            generation=epoch;
            lastTick=-1;lastEdgeTick=-1;synthetic=false;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("bridge tick went backward");
        lastTick=tick;
        if(!enabled || manualSneak || !onGround || !supportedNow || !moving) {
            synthetic=false;lastEdgeTick=-1;
            return false;
        }
        if(gapAhead) {
            synthetic=true;
            lastEdgeTick=tick;
            return true;
        }
        // A finite unsneak delay reduces edge flicker while the user
        // continues forward, without keeping synthetic sneak through
        // menu/focus/release/world changes.
        if(synthetic && lastEdgeTick>=0 &&
            tick-lastEdgeTick<=releaseDelayTicks) return true;
        synthetic=false;
        lastEdgeTick=-1;
        return false;
    }
    public void reset() {
        checkOwner();
        generation=-1;lastTick=-1;lastEdgeTick=-1;synthetic=false;
    }
}
