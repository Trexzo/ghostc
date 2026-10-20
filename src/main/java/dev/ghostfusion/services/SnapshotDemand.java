package dev.ghostfusion.services;

/**
 * Minimal target data required by the enabled snapshot consumers.
 * Calculate once per client tick; no allocation in the per-player loop.
 * This policy must not disable the empty snapshot needed for the combat tick
 * and generation freshness fence.
 */
public final class SnapshotDemand {
    public final boolean needVisible,needUnfiltered;
    public final double maximumDistanceSq;
    private SnapshotDemand(boolean needVisible,boolean needUnfiltered,double limit) {
        this.needVisible=needVisible;
        this.needUnfiltered=needUnfiltered;
        this.maximumDistanceSq=limit*limit;
    }
    public static SnapshotDemand of(boolean hudEnabled,boolean hudVisible,double hudRange,
                                    boolean aimEnabled,boolean aimVisible,double aimRange) {
        if((hudEnabled && (!Double.isFinite(hudRange)||hudRange<=0||hudRange>24)) ||
           (aimEnabled && (!Double.isFinite(aimRange)||aimRange<=0||aimRange>6)))
            throw new IllegalArgumentException("snapshot consumer range");
        boolean visible=(hudEnabled&&hudVisible)||(aimEnabled&&aimVisible);
        boolean unrestricted=(hudEnabled&&!hudVisible)||(aimEnabled&&!aimVisible);
        double range=Math.max(hudEnabled?hudRange:0,aimEnabled?aimRange:0);
        return new SnapshotDemand(visible,unrestricted,range);
    }
    public boolean needed(){return needVisible||needUnfiltered;}
    public boolean accepts(double square) {
        return needed() && Double.isFinite(square) &&
            square>=0 && square<=maximumDistanceSq;
    }
    /** When visibility is not requested, do not run any ray/occlusion checks. */
    public boolean testVisibility(){return needVisible;}
    /** A hidden target is relevant only if an unfiltered consumer exists. */
    public boolean admit(boolean visible){
        return visible ? needVisible||needUnfiltered : needUnfiltered;
    }
}
