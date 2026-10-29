package dev.ghostfusion.features;

/**
 * A one-tick, owner-thread latch for actual player hotbar inputs observed by
 * the Forge input-event adapter, not an inference from currentItem alone.
 *
 * A number-key tap or wheel movement can leave currentItem unchanged (or
 * move away and back) before the following client END tick. That event must
 * still revoke synthetic rod/snowball hotbar ownership. A held key snapshot
 * is independently checked by each native controller.
 */
public final class ProjectileManualInputGate {
    private final Thread owner=Thread.currentThread();
    private boolean pending;
    private long signals,consumed;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("projectile input owner thread");
    }
    /** Observe a genuine manual number-key or mouse-wheel event. */
    public void mark() {
        check();
        pending=true;
        if(signals<Long.MAX_VALUE)signals++;
    }
    /** Exactly one END client-tick consumes all intervening manual events. */
    public boolean take() {
        check();
        boolean result=pending;
        pending=false;
        if(result && consumed<Long.MAX_VALUE)consumed++;
        return result;
    }
    public boolean pending() {check();return pending;}
    public long signals() {check();return signals;}
    public long consumedTicks() {check();return consumed;}
    /** Lifecycle/GUI transition discards old-game input events. */
    public void reset() {check();pending=false;}
}
