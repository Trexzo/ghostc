package dev.ghostfusion.features;

/**
 * Owner-thread bounded interval scheduler for the optional AutoClicker
 * balanced/range cadence. No game objects, injections, networking or
 * dependencies. Never catches up missed clicks and never clicks on arming.
 *
 * A seeded instance is reproducible in offline trace tests. Production
 * instances use a different start state; this is not an anticheat bypass.
 */
public final class PatternClickScheduler {
    private static final long ZERO_SEED = 0x9E3779B97F4A7C15L;
    private final Thread owner = Thread.currentThread();
    private long state;
    private boolean armed, observed;
    private long lastNanos, armedAtNanos, intervalNanos;
    private double previousMin, previousMax;
    private String previousPattern;

    public PatternClickScheduler() {
        this(System.nanoTime() ^ ZERO_SEED);
    }
    public PatternClickScheduler(long seed) {
        state = seed == 0L ? ZERO_SEED : seed;
    }
    private void checkOwner() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("click pattern owner thread");
    }
    /** [0,1) uniform double without allocations or shared RNG state. */
    private double sample() {
        state ^= state >>> 12;
        state ^= state << 25;
        state ^= state >>> 27;
        long bits = state * 2685821657736338717L;
        return (bits >>> 11) * 0x1.0p-53;
    }
    private long chooseInterval(double minCps, double maxCps, String pattern) {
        double unit = sample();
        if ("balanced".equals(pattern))
            unit = (unit + sample()) * 0.5;
        double cps = minCps + (maxCps - minCps) * unit;
        return Math.max(1L, (long) (1000000000.0 / cps));
    }
    public boolean due(long nowNanos, boolean enabled, boolean held,
                       double minCps, double maxCps, String pattern) {
        checkOwner();
        if (pattern == null ||
                (!"balanced".equals(pattern) && !"range".equals(pattern)) ||
                !Double.isFinite(minCps) || !Double.isFinite(maxCps) ||
                minCps < 1.0 || maxCps > 25.0 || minCps > maxCps)
            throw new IllegalArgumentException("click pattern settings");
        if (observed && nowNanos < lastNanos)
            throw new IllegalArgumentException("nonmonotonic clock");
        observed = true;
        lastNanos = nowNanos;
        if (!enabled || !held) {
            armed = false;
            return false;
        }
        // Changing a live pattern or CPS bounds may not inherit a pending
        // old interval; it starts a new held-input admission instead.
        if (!armed || !pattern.equals(previousPattern) ||
                minCps != previousMin || maxCps != previousMax) {
            armed = true;
            armedAtNanos = nowNanos;
            previousPattern = pattern;
            previousMin = minCps;
            previousMax = maxCps;
            intervalNanos = chooseInterval(minCps, maxCps, pattern);
            return false;
        }
        // Avoid overflow when a large valid forward interval wraps signed long.
        if (Long.compareUnsigned(nowNanos - armedAtNanos, intervalNanos) < 0)
            return false;
        armedAtNanos = nowNanos;
        intervalNanos = chooseInterval(minCps, maxCps, pattern);
        return true; // never emit more than one pulse in one tick
    }
    /** For trace tests only: current selected deadline is bounded by CPS. */
    public long intervalNanos() {
        checkOwner();
        return armed ? intervalNanos : 0L;
    }
    public void reset() {
        checkOwner();
        armed = false;
        observed = false;
        lastNanos = 0;
        armedAtNanos = 0;
        intervalNanos = 0;
        previousPattern = null;
        previousMin = 0;
        previousMax = 0;
    }
}
