package dev.ghostfusion.features;

import java.lang.ref.WeakReference;

/**
 * Owner-thread quarantine for uncaught shared combat-frame RuntimeExceptions.
 * Individual module callbacks use FeatureFaultGuard instead. This fence
 * neither emits native input nor changes NativePulseSettlement's lane state.
 *
 * An abnormal frame may have left shared intents or policy state incomplete.
 * A mere tick, toggle, focus change, Panic, or same-world reconnect is not a
 * recovery proof. Only a NEW generation AND changed native world/player
 * identity may reopen optional combat dispatch.
 */
public final class CombatFrameFaultFence {
    private final Thread owner=Thread.currentThread();
    private long generation=-1;
    private WeakReference<Object> worldRef,playerRef;
    private boolean pinned;
    private boolean quarantined;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("combat frame fence owner thread");
    }

    /** Passive observation; null world/player never clears an existing fault. */
    public boolean observe(long epoch,Object world,Object player) {
        check();
        if(epoch<0 || epoch<generation)
            throw new IllegalArgumentException("combat frame generation");
        final boolean newer=epoch>generation;
        generation=epoch;
        if(world==null || player==null)return !quarantined;
        if(!pinned) {
            worldRef=new WeakReference<Object>(world);
            playerRef=new WeakReference<Object>(player);
            pinned=true;
        } else if(worldRef.get()!=world || playerRef.get()!=player) {
            if(newer) {
                worldRef=new WeakReference<Object>(world);
                playerRef=new WeakReference<Object>(player);
                quarantined=false;
            } else {
                // Native objects changed without the lifecycle generation
                // advancing: refuse an ambiguous same-epoch combat context.
                quarantined=true;
            }
        }
        return !quarantined;
    }

    /** Fail closed after a shared frame exception, without retaining it. */
    public void quarantine() {
        check();
        quarantined=true;
    }

    public boolean quarantined() {
        check();
        return quarantined;
    }
}
