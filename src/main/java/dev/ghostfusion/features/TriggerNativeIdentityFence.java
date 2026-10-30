package dev.ghostfusion.features;

import java.util.UUID;

/**
 * Owner-thread native TriggerBot candidate identity fence. UUID alone is
 * insufficient for a multi-tick dwell: a new EntityPlayer object may reuse
 * the same UUID or numeric entity ID in the same loaded world.
 *
 * Observes one native crosshair candidate per END tick. No game input,
 * rotations, packets, entity writes, or persistence. The Forge adapter must
 * independently recheck current-world registry, ray, reach, LOS and team
 * restrictions immediately before the KeyBinding native attack.
 */
public final class TriggerNativeIdentityFence {
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1;
    private int entityId=-1;
    private UUID uuid;
    private Object player,world;
    private boolean eligible;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("Trigger native identity owner thread");
    }

    /** True means an existing candidate changed and dwell must restart. */
    public boolean observe(long generation,long tick,boolean nativeEligible,
                           UUID identity,int id,Object entity,Object currentWorld) {
        check();
        if(generation<0||tick<0)
            throw new IllegalArgumentException("Trigger native identity epoch/tick");
        if(generation!=epoch) {
            resetState();
            epoch=generation;
        }
        if(lastTick>=0&&tick<=lastTick)
            throw new IllegalArgumentException("Trigger native duplicate/backward tick");
        lastTick=tick;
        if(!nativeEligible) {
            boolean changed=eligible;
            clear();
            return changed;
        }
        if(identity==null||id<0||entity==null||currentWorld==null)
            throw new IllegalArgumentException("Trigger native candidate");
        boolean changed=eligible &&
            (!identity.equals(uuid)||id!=entityId||
             entity!=player||currentWorld!=world);
        uuid=identity;entityId=id;player=entity;world=currentWorld;
        eligible=true;
        return changed;
    }

    /** Check at actual KeyBinding pulse boundary on the same client tick. */
    public boolean permits(long generation,long tick,UUID identity,int id,
                           Object entity,Object currentWorld) {
        check();
        return eligible && epoch==generation && lastTick==tick &&
            identity!=null && identity.equals(uuid) && id==entityId &&
            entity!=null && entity==player &&
            currentWorld!=null && currentWorld==world;
    }

    /** A failed final native guard invalidates the candidate (not cooldown). */
    public void invalidate() {
        check();clear();
    }
    private void clear() {
        uuid=null;entityId=-1;player=world=null;eligible=false;
    }
    private void resetState() {
        clear();lastTick=-1;
    }
    public void reset() {
        check();resetState();epoch=-1;
    }
}
