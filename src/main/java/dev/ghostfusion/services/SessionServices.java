package dev.ghostfusion.services;

import java.util.Collections;

/**
 * Owner-thread service generation, lifecycle and tick clock.
 * Session changes invalidate all actions, rotations, inventory work, packets and target snapshot.
 */
public final class SessionServices {
    private final Thread owner=Thread.currentThread();
    private final RotationCoordinator rotations=new RotationCoordinator();
    private final ActionCoordinator actions=new ActionCoordinator();
    private final PacketCoordinator packets=new PacketCoordinator(128);
    private final InventoryCoordinator inventory=new InventoryCoordinator(64);
    private long generation, tick;
    private boolean connected, closed, transitioning;
    private WorldSnapshot snapshot;
    private void check() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("services owner thread");
        if(closed) throw new IllegalStateException("services closed");
    }
    public RotationCoordinator rotations() { check(); return rotations; }
    public ActionCoordinator actions() { check(); return actions; }
    public PacketCoordinator packets() { check(); return packets; }
    public InventoryCoordinator inventory() { check(); return inventory; }
    public long generation() { check(); return generation; }
    public long tick() { check(); return tick; }
    public boolean connected() { check(); return connected; }
    public WorldSnapshot snapshot() { check(); return snapshot; }
    /** One session epoch transition owns the complete invalidation window.
     * Completion callbacks from packet reset may query state, but must not
     * recursively change world identity, disconnect or advance this tick. */
    private void transition(boolean active, boolean force) {
        check();
        if(transitioning)throw new IllegalStateException("session transition already in progress");
        if(!force && connected==active)return;
        if(generation==Long.MAX_VALUE)throw new IllegalStateException("session generation overflow");
        transitioning=true;
        try {
            connected=active;
            generation++;
            snapshot=null;
            // Packet completions are arbitrary client callbacks. Deliver them
            // BEFORE clearing other coordinators so any callback-submitted
            // intents cannot survive the session transition.
            packets.reset();
            rotations.clear(); actions.reset(); inventory.reset();
        } finally {
            transitioning=false;
        }
    }
    public void world(boolean active) {
        transition(active,false);
    }
    public void tickAdvance() {
        check();
        if(transitioning)throw new IllegalStateException("tick during session transition");
        if(!connected) return;
        if(tick==Long.MAX_VALUE) throw new IllegalStateException("tick overflow");
        tick++;
        // Snapshots belong to one exact client tick. Never expose stale
        // targets if player pose or world enumeration fails this frame.
        snapshot=null;
        rotations.prune(tick,generation);
        actions.prune(tick,generation);
    }
    public void publish(WorldSnapshot next) {
        check();
        if(transitioning)throw new IllegalStateException("snapshot during session transition");
        if(!connected || next==null || next.generation!=generation || next.tick!=tick)
            throw new IllegalArgumentException("stale snapshot");
        snapshot=next;
    }
    /** Disconnection always advances the epoch, including repeated disconnect callbacks. */
    public void disconnect() {
        transition(false,true);
    }
    public void close() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("services owner thread");
        if(closed) return;
        if(transitioning)throw new IllegalStateException("cannot close mid-transition");
        // Seal the facade before notifying untrusted SHUTDOWN completions.
        // No completion may reconnect or publish into a partially closed host.
        closed=true;
        snapshot=null;
        connected=false;
        packets.shutdown(); inventory.shutdown(); actions.shutdown(); rotations.shutdown();
    }
}
