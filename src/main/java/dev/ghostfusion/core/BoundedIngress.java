package dev.ghostfusion.core;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe bounded ingress: a future host adapter can forward immutable events to client thread. */
public final class BoundedIngress {
    private final ConcurrentLinkedQueue<GameEvent> queue = new ConcurrentLinkedQueue<GameEvent>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicLong rejected = new AtomicLong();
    // A producer may submit during drain. Only one consumer may dequeue,
    // including when a module callback tries to drain this queue recursively.
    // Atomic acquisition also rejects a second, differently owned registry
    // racing the active consumer on another thread.
    private final AtomicBoolean draining = new AtomicBoolean();
    private final int capacity;
    // Serialize publication with the terminal close fence. CAS still
    // coordinates producer reservations with the single owner consumer.
    private final Object admissionLock = new Object();
    private boolean sealed;
    public BoundedIngress(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }
    public boolean submit(GameEvent event) {
        if (event == null) throw new IllegalArgumentException("event");
        synchronized (admissionLock) {
            if (sealed) { rejected.incrementAndGet(); return false; }
            while (true) {
                int n = queued.get();
                if (n >= capacity) { rejected.incrementAndGet(); return false; }
                if (queued.compareAndSet(n, n + 1)) break;
            }
            // A producer cannot hold a reserved slot after the close fence:
            // it must publish before leaving this monitor, or be rejected.
            queue.add(event);
            return true;
        }
    }
    /** Terminal admission fence. A producer already publishing completes
     * before this returns; every later submit is refused. Previously queued
     * events remain bounded and may be drained by the owner if desired.
     * Idempotent; no module callback runs while holding this monitor. */
    public void seal() {
        synchronized (admissionLock) {
            sealed = true;
        }
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
        // Acquire before queue.poll(): ModuleRegistry's recursive dispatch
        // guard runs only AFTER a poll and would otherwise lose that event.
        if (!draining.compareAndSet(false,true))
            throw new IllegalStateException("Recursive or concurrent ingress drain");
        try {
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
        } finally {
            // An exceptional target.dispatch must never latch the queue.
            draining.set(false);
        }
    }
    public int pending() { return queued.get(); }
    public long rejected() { return rejected.get(); }
}
