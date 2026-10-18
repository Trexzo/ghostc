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
        target.assertOwnerThread();
        if (limit < 1) throw new IllegalArgumentException("limit");
        int n = 0;
        while (n < limit) {
            GameEvent event = queue.poll();
            if (event == null) break;
            queued.decrementAndGet();
            target.dispatch(event); n++;
        }
        return n;
    }
    public int pending() { return queued.get(); }
    public long rejected() { return rejected.get(); }
}
