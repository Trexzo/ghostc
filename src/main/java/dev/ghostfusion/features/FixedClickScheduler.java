package dev.ghostfusion.features;

/**
 * Deterministic hold-to-click timing policy with no delayed catch-up bursts.
 * Injecting an approved intent belongs to the single Forge adapter.
 * System.nanoTime() has an arbitrary origin, including negative values.
 */
public final class FixedClickScheduler {
    private final Thread owner=Thread.currentThread();
    private boolean armed, observed;
    private long armedAtNanos, lastNanos;
    private void checkThread() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("click scheduler owner thread");
    }
    public boolean due(long nowNanos, boolean enabled, boolean held, double cps) {
        checkThread();
        if(Double.isNaN(cps) || Double.isInfinite(cps) || cps<1 || cps>25)
            throw new IllegalArgumentException("click timing");
        if(observed && nowNanos<lastNanos) throw new IllegalArgumentException("nonmonotonic clock");
        observed=true;
        lastNanos=nowNanos;
        if(!enabled || !held) {
            armed=false;
            return false;
        }
        long period=(long)(1000000000.0/cps);
        if(!armed) {
            armed=true;
            armedAtNanos=nowNanos;
            return false; // native first press is processed by vanilla Minecraft
        }
        // A valid forward nanoTime interval can exceed the signed long range.
        // Compare elapsed nanos without signed-subtraction overflow.
        if(Long.compareUnsigned(nowNanos-armedAtNanos,period)<0) return false;
        // Missed frames do not create a burst of queued clicks.
        armedAtNanos=nowNanos;
        return true;
    }
    public void reset() {
        checkThread(); armed=false; observed=false; lastNanos=0; armedAtNanos=0;
    }
}
