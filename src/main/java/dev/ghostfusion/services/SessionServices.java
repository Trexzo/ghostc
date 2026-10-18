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
    private boolean connected, closed;
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
    public void world(boolean active) {
        check();
        if(connected==active) return;
        connected=active;
        generation++;
        rotations.clear(); actions.reset(); packets.reset(); inventory.reset();
        snapshot=null;
    }
    public void tickAdvance() {
        check(); if(!connected) return;
        if(tick==Long.MAX_VALUE) throw new IllegalStateException("tick overflow");
        tick++;
        rotations.prune(tick,generation);
        actions.prune(tick,generation);
    }
    public void publish(WorldSnapshot next) {
        check();
        if(!connected || next==null || next.generation!=generation || next.tick!=tick)
            throw new IllegalArgumentException("stale snapshot");
        snapshot=next;
    }
    /** Disconnection always advances the epoch, including repeated disconnect callbacks. */
    public void disconnect() {
        check();
        connected=false;
        generation++;
        rotations.clear(); actions.reset(); packets.reset(); inventory.reset();
        snapshot=null;
    }
    public void close() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("services owner thread");
        if(closed) return;
        packets.shutdown(); inventory.shutdown(); actions.shutdown(); rotations.shutdown();
        snapshot=null; connected=false; closed=true;
    }
}
