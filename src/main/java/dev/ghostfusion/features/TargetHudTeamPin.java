package dev.ghostfusion.features;

/**
 * Primitive-only tracker for one optional nearest non-teammate snapshot pin.
 * Minecraft scoreboard and world-identity admission are performed by Forge
 * before calling offer; this tracks distance, visibility and deterministic ID
 * ranking without retaining EntityPlayer or other game objects.
 */
public final class TargetHudTeamPin {
    private boolean hasBest;
    private double bestSquared;
    private int bestId;

    /**
     * Returns true only if this candidate becomes the nearest admissible
     * non-teammate. Callers then update the existing snapshot pin (max one).
     * A visible-only HUD must not pin an occluded candidate even if Aim
     * Assist's independent unfiltered demand admitted it.
     */
    public boolean offer(int entityId,double squared,boolean visible,
                         boolean visibleOnly,double rangeSquared) {
        if(!Double.isFinite(squared)||squared<0 ||
           !Double.isFinite(rangeSquared)||rangeSquared<=0 ||
           squared>rangeSquared || (visibleOnly && !visible))return false;
        if(hasBest && (squared>bestSquared ||
           (squared==bestSquared && entityId>=bestId)))return false;
        bestSquared=squared;
        bestId=entityId;
        hasBest=true;
        return true;
    }
    public boolean hasBest(){return hasBest;}
    public int bestId(){
        if(!hasBest)throw new IllegalStateException("no pinned HUD target");
        return bestId;
    }
    public void clear(){
        hasBest=false;
        bestSquared=0;
        bestId=0;
    }
}
