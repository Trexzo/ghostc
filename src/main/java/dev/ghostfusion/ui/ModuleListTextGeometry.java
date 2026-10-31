package dev.ghostfusion.ui;

/**
 * Pixel-bounded, allocation-free placement of Slate ArrayList labels.
 * Layout owns membership/sort; this only handles visual entry motion.
 * It never changes legacy Classic/Minimal positions or feature state.
 */
public final class ModuleListTextGeometry {
    private static final int TEXT_PADDING=6;
    private static final int MAX_TRAVEL=7;
    private ModuleListTextGeometry() {}

    /**
     * Entry slides a few pixels from its anchored edge while fading in.
     * The whole glyph run stays on screen even at custom HUD anchors.
     */
    public static int x(ModuleListLayout.Row row,int textWidth,
                        int viewportWidth,boolean right,double phase) {
        if(row==null || viewportWidth<1 || textWidth<0 ||
           row.x<0 || row.width<2*TEXT_PADDING ||
           (long)row.x+row.width>viewportWidth ||
           textWidth>row.width-2*TEXT_PADDING ||
           !Double.isFinite(phase) || phase<0 || phase>1)
            throw new IllegalArgumentException("Module List Slate text bounds");
        int base=right?row.x+row.width-TEXT_PADDING-textWidth:
                       row.x+TEXT_PADDING;
        int travel=(int)Math.round((1.0-phase)*MAX_TRAVEL);
        int displaced=right?base+travel:base-travel;
        return Math.max(0,Math.min(viewportWidth-textWidth,displaced));
    }
}
