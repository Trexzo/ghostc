package dev.ghostfusion.services;

/**
 * Owner-thread tracker for the exact live Minecraft world/player object pair.
 * Both objects must exist before a session is admitted. A dimension/world
 * swap or local-player recreation is a session SWITCH even if there was no
 * intervening null frame; no old generation may leak into the new world.
 *
 * This class stores object identity only; never a name, server IP or position.
 */
public final class WorldIdentityTracker {
    public enum Transition { ABSENT, ENTER, STABLE, SWITCH, LEAVE }
    private final Thread owner=Thread.currentThread();
    private Object world, player;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("world identity owner thread");
    }
    public Transition observe(Object currentWorld,Object currentPlayer) {
        check();
        final boolean ready=currentWorld!=null && currentPlayer!=null;
        final boolean previous=world!=null && player!=null;
        if(!ready) {
            world=null;
            player=null;
            return previous?Transition.LEAVE:Transition.ABSENT;
        }
        if(!previous) {
            world=currentWorld;
            player=currentPlayer;
            return Transition.ENTER;
        }
        if(world==currentWorld && player==currentPlayer)
            return Transition.STABLE;
        world=currentWorld;
        player=currentPlayer;
        return Transition.SWITCH;
    }
    /** Clear only local identity; caller owns service-generation invalidation. */
    public void reset() {
        check();
        world=null; player=null;
    }
    public boolean ready() {
        check(); return world!=null && player!=null;
    }
    /** Validate immediate render/input callbacks without mutating session state.
     * A new world/player must first pass the next owner END-tick transition. */
    public boolean matches(Object currentWorld,Object currentPlayer) {
        check();
        return world!=null && player!=null &&
            world==currentWorld && player==currentPlayer;
    }
}
