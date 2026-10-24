package dev.ghostfusion.features;

/**
 * Monotonic, testable 1-second physical click counter.
 * Two fixed primitive rings avoid allocating a boxed Long for every mouse
 * edge, even during bursts. The oldest observation is discarded at capacity.
 * Host supplies System.nanoTime; no Minecraft or native input dependencies.
 */
public final class CpsMeter {
    private static final long WINDOW_NS = 1000000000L;
    /** Saturation is far above ordinary human CPS. */
    public static final int MAX_WINDOW_CLICKS = 4096;

    private static final class TimestampRing {
        private final long[] timestamps = new long[MAX_WINDOW_CLICKS];
        private int head, size;

        void expire(long now) {
            while (size > 0 &&
                   Long.compareUnsigned(now - timestamps[head], WINDOW_NS) >= 0) {
                head++;
                if (head == timestamps.length) head = 0;
                size--;
            }
        }

        void record(long now) {
            // Preserve the newest physical edges, exactly as the old capped
            // ArrayDeque did. No allocation, shifting, or rescan on insertion.
            if (size == timestamps.length) {
                head++;
                if (head == timestamps.length) head = 0;
                size--;
            }
            int tail = head + size;
            if (tail >= timestamps.length) tail -= timestamps.length;
            timestamps[tail] = now;
            size++;
        }

        int count() { return size; }

        void clear() {
            // Old data is unreachable when size=0. Zeroing every slot on
            // disconnect/focus loss is unnecessary and harms tick latency.
            head = 0;
            size = 0;
        }
    }

    private final Thread owner = Thread.currentThread();
    private final TimestampRing left = new TimestampRing();
    private final TimestampRing right = new TimestampRing();
    private final boolean[] held = new boolean[2];
    private long lastTimestamp = Long.MIN_VALUE;

    private void check(long now) {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("CPS owner thread");
        if (lastTimestamp != Long.MIN_VALUE && now < lastTimestamp)
            throw new IllegalArgumentException("non-monotonic click clock");
        lastTimestamp = now;
        left.expire(now);
        right.expire(now);
    }

    public void mouse(int button, boolean down, long now, boolean accepting) {
        check(now);
        if (button != 0 && button != 1) return;
        if (!accepting) { held[button] = false; return; }
        if (down && !held[button])
            (button == 0 ? left : right).record(now);
        held[button] = down;
    }

    public int left(long now) { check(now); return left.count(); }
    public int right(long now) { check(now); return right.count(); }

    public void clear() {
        if (Thread.currentThread() != owner)
            throw new IllegalStateException("CPS owner thread");
        left.clear();
        right.clear();
        held[0] = false;
        held[1] = false;
        lastTimestamp = Long.MIN_VALUE;
    }
}
