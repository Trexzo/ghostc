package dev.ghostfusion.ui;

/** Pure viewport-safe placement for a mandatory fault/recovery warning. */
public final class HudWarningPlacement {
    private HudWarningPlacement() { }
    /**
     * A right-anchored Status HUD must not truncate an unrelated mandatory
     * warning down to an invisible suffix. Keep the preferred anchor when
     * the whole warning fits; otherwise use the independent left fallback.
     */
    public static int x(int preferred,int viewportWidth,int messageWidth) {
        if(viewportWidth<1||messageWidth<0)
            throw new IllegalArgumentException("warning width");
        int candidate=Math.max(0,preferred);
        return (long)candidate+2L+(long)messageWidth<=viewportWidth
            ? candidate : 0;
    }
    public static int y(int wanted,int viewportHeight,int fontHeight) {
        if(viewportHeight<1 || fontHeight<1)
            throw new IllegalArgumentException("warning viewport");
        return Math.max(0,Math.min(wanted,
            Math.max(0,viewportHeight-fontHeight-1)));
    }
}
