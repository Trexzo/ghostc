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
    public final double visibleDistanceSq,unfilteredDistanceSq;
    private SnapshotDemand(double visibleRange,double unrestrictedRange) {
        this.needVisible=visibleRange>0;
        this.needUnfiltered=unrestrictedRange>0;
        this.visibleDistanceSq=visibleRange*visibleRange;
        this.unfilteredDistanceSq=unrestrictedRange*unrestrictedRange;
        double maximum=Math.max(visibleRange,unrestrictedRange);
        this.maximumDistanceSq=maximum*maximum;
    }
    public static SnapshotDemand of(boolean hudEnabled,boolean hudVisible,double hudRange,
                                    boolean aimEnabled,boolean aimVisible,double aimRange) {
        if((hudEnabled && (!Double.isFinite(hudRange)||hudRange<=0||hudRange>24)) ||
           (aimEnabled && (!Double.isFinite(aimRange)||aimRange<=0||aimRange>6)))
            throw new IllegalArgumentException("snapshot consumer range");
        double visible=Math.max(hudEnabled&&hudVisible?hudRange:0,
                                aimEnabled&&aimVisible?aimRange:0);
        double unrestricted=Math.max(hudEnabled&&!hudVisible?hudRange:0,
                                     aimEnabled&&!aimVisible?aimRange:0);
        return new SnapshotDemand(visible,unrestricted);
    }
    public boolean needed(){return needVisible||needUnfiltered;}
    public boolean accepts(double square) {
        return needed() && Double.isFinite(square) &&
            square>=0 && square<=maximumDistanceSq;
    }
    /** Coarse compatibility introspection; the per-distance gate is authoritative. */
    public boolean testVisibility(){return needVisible;}
    public boolean admit(boolean visible){
        return visible ? needVisible||needUnfiltered : needUnfiltered;
    }
    /** Never ray-check entities outside the *visible-only* feature's radius. */
    public boolean testVisibility(double squaredDistance){
        return Double.isFinite(squaredDistance) && squaredDistance>=0 &&
            needVisible && squaredDistance<=visibleDistanceSq;
    }
    /** Unfiltered consumers can use either visible or hidden records. */
    public boolean admit(boolean visible,double squaredDistance){
        if(!accepts(squaredDistance))return false;
        return visible ? squaredDistance<=visibleDistanceSq ||
                         squaredDistance<=unfilteredDistanceSq
                       : squaredDistance<=unfilteredDistanceSq;
    }
}
