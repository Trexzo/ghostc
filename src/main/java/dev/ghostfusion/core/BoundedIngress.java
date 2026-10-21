package dev.ghostfusion.core;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe bounded ingress: a future host adapter can forward immutable events to client thread. */
public final class BoundedIngress {
    private final ConcurrentLinkedQueue<GameEvent> queue = new ConcurrentLinkedQueue<GameEvent>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong rejected = new AtomicLong();
    private final int capacity;
    public BoundedIngress(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public boolean submit(GameEvent event) {
        if (event == null) throw new IllegalArgumentException("event");
        while (true) {
            int n = queued.get();
            if (n >= capacity) { rejected.incrementAndGet(); return false; }
            if (queued.compareAndSet(n, n + 1)) break;
        }
        queue.add(event);
        return true;
    }
    public int drain(ModuleRegistry target, int limit) {
        return drainAfter(target, limit, -1L);
    }
    /**
     * Drop events stamped at or before the last closed session boundary.
     * The budget limits DEQUEUED events, including stale ones, so clearing a
     * full old-world queue can never create an unbounded owner-tick spike.
     * A producer may reserve a slot before the transition and publish it
     * afterward; its pre-boundary sequence remains rejected.
     *
     * @return number of current-session events dispatched (not stale drops)
     */
    public int drainAfter(ModuleRegistry target, int limit, long expiredThroughSequence) {
        target.assertOwnerThread();
        if (limit < 1 || expiredThroughSequence < -1)
            throw new IllegalArgumentException("drain limit/epoch");
        int polled = 0, delivered = 0;
        while (polled < limit) {
            GameEvent event = queue.poll();
            if (event == null) break;
            queued.decrementAndGet();
            polled++;
            if (event.sequence() > expiredThroughSequence) {
                target.dispatch(event);
                delivered++;
            }
        }
        return delivered;
    }
    public int pending() { return queued.get(); }
    public long rejected() { return rejected.get(); }
}
