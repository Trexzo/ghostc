package dev.ghostfusion.features;

import java.util.UUID;

/**
 * One original-game target and one NEXT-tick USE permission per projectile
 * hotbar selection. This does not select, aim, use an item, or store identity
 * in a report; the native Forge controller owns all game interaction.
 *
 * A fresh valid crosshair target at USE is insufficient: it MUST be the same
 * original native player UUID and entity ID, same world generation, exact
 * subsequent tick and a fresh native range/visibility check. No retarget.
 */
public final class ProjectileTargetFence {
    private final Thread owner=Thread.currentThread();
    private long epoch=-1, selectedTick=-1;
    private UUID target;
    private int entityId=-1;
    private boolean consumed;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("projectile target fence owner thread");
    }

    /** Call only AFTER a successful native hotbar SELECT and policy commit. */
    public void selected(long generation,long tick,UUID id,int nativeId) {
        check();
        if(generation<0||tick<0||id==null||nativeId<0)
            throw new IllegalArgumentException("invalid projectile target");
        epoch=generation;
        selectedTick=tick;
        target=id;
        entityId=nativeId;
        consumed=false;
    }

    /**
     * Exact next END tick only. The native controller independently verifies
     * current-world object, line-of-sight, health, range and manual input.
     * One evaluation consumes the permission whether approved or denied.
     */
    public boolean allowUse(long generation,long tick,UUID currentId,
                            int currentNativeId,boolean nativeTargetValid) {
        check();
        boolean allowed= !consumed && target!=null &&
            generation==epoch && tick>selectedTick &&
            tick-selectedTick==1 &&
            nativeTargetValid && currentId!=null &&
            target.equals(currentId) && currentNativeId==entityId;
        consumed=true;
        return allowed;
    }

    public void reset() {
        check();
        epoch=selectedTick=-1;
        target=null;
        entityId=-1;
        consumed=false;
    }
}
