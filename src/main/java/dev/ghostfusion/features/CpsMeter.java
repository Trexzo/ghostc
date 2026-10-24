package dev.ghostfusion.features;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Monotonic, testable 1-second click counter, edge-triggered (not poll-triggered).
 * Host supplies System.nanoTime; no dependence on Minecraft or native input.
 */
public final class CpsMeter {
    private static final long WINDOW_NS = 1000000000L;
    /** Hard memory budget per physical button. Normal human CPS is far
     * below this; a malformed input flood saturates rather than allocating
     * one boxed timestamp forever inside an active one-second window. */
    public static final int MAX_WINDOW_CLICKS = 4096;
    private final Thread owner = Thread.currentThread();
    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();
    private final boolean[] held = new boolean[2];
    private long lastTimestamp = Long.MIN_VALUE;

    private void check(long now) {
        if (Thread.currentThread() != owner) throw new IllegalStateException("CPS owner thread");
        if (lastTimestamp != Long.MIN_VALUE && now < lastTimestamp)
            throw new IllegalArgumentException("non-monotonic click clock");
        lastTimestamp = now;
        evict(left, now); evict(right, now);
    }
    private static void evict(Deque<Long> events, long now) {
        // Nondecreasing timestamps may still span more than Long.MAX_VALUE
        // nanos; unsigned elapsed comparison expires such old clicks.
        while (!events.isEmpty() &&
               Long.compareUnsigned(now-events.peekFirst(),WINDOW_NS)>=0)
            events.removeFirst();
    }
    private static void recordEdge(Deque<Long> events,long now) {
        // Eviction already runs on every input event. When an input flood
        // exceeds the finite budget retain the most recent observations:
        // these are the last edges to expire as the window advances.
        if(events.size()==MAX_WINDOW_CLICKS)events.removeFirst();
        events.addLast(now);
    }
    public void mouse(int button, boolean down, long now, boolean accepting) {
        check(now);
        if (button != 0 && button != 1) return;
        if (!accepting) { held[button] = false; return; }
        if (down && !held[button])
            recordEdge(button == 0 ? left : right, now);
        held[button] = down;
    }
    public int left(long now) { check(now); return left.size(); }
    public int right(long now) { check(now); return right.size(); }
    public void clear() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("CPS owner thread");
        left.clear(); right.clear(); held[0] = false; held[1] = false;
        lastTimestamp = Long.MIN_VALUE;
    }
}
