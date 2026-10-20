package dev.ghostfusion.ui;

/** Pure viewport-safe placement for a mandatory fault/recovery warning. */
public final class HudWarningPlacement {
    private HudWarningPlacement() { }
    public static int y(int wanted,int viewportHeight,int fontHeight) {
        if(viewportHeight<1 || fontHeight<1)
            throw new IllegalArgumentException("warning viewport");
        return Math.max(0,Math.min(wanted,
            Math.max(0,viewportHeight-fontHeight-1)));
    }
}
