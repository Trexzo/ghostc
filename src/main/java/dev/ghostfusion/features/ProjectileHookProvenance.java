package dev.ghostfusion.features;

/**
 * Pure owner-thread local world-hook provenance check. Objects are opaque
 * native Minecraft player/world/hook identities; Forge validates live
 * entity classes and the exact current-world entity registry.
 *
 * New registered LOCAL fish hooks are stronger client-world observations
 * than a non-null fishEntity pointer; neither proves the server accepted a
 * PvP hit, the intended target, or any packet acknowledgement.
 *
 * This helper never captures/writes game objects, creates hooks, or
 * authorizes a subsequent cast.
 */
public final class ProjectileHookProvenance {
    private final Thread owner=Thread.currentThread();
    private Object player,world,previousHook;
    private boolean armed;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("projectile hook provenance owner thread");
    }

    /** Record the current local hook immediately BEFORE vanilla sendUseItem. */
    public void arm(Object originalPlayer,Object originalWorld,Object beforeHook) {
        check();
        if(originalPlayer==null||originalWorld==null)
            throw new IllegalArgumentException("missing native rod session");
        player=originalPlayer;
        world=originalWorld;
        previousHook=beforeHook;
        armed=true;
    }

    /**
     * Forge supplies the native EntityFishHook's angler, worldObj, isDead
     * and current-world getEntityByID result. Compare exact object identity,
     * never merely equal entity ID, UUID or a previous-world cached hook.
     */
    public boolean eligible(Object currentPlayer,Object currentWorld,
                            Object currentHook,Object hookAngler,Object hookWorld,
                            Object registeredHook,boolean hookDead) {
        check();
        return armed && player==currentPlayer && world==currentWorld &&
            currentHook!=null && currentHook!=previousHook && !hookDead &&
            hookAngler==player && hookWorld==world &&
            registeredHook==currentHook;
    }

    public void reset() {
        check();
        player=world=previousHook=null;
        armed=false;
    }
}
