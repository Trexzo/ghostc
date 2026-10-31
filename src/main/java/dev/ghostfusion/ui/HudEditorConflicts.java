package dev.ghostfusion.ui;

/**
 * Read-only, allocation-free collision classification for the F7 HUD editor.
 * Array indices equal HudEditorControls.Item ordinal, STATUS included.
 * Never changes anchors, feature state, saved preferences or Minecraft input.
 */
public final class HudEditorConflicts {
    private static final HudEditorControls.Item[] ITEMS=HudEditorControls.Item.values();
    private static final int ALL_BITS=(1<<ITEMS.length)-1;
    private HudEditorConflicts(){}

    /**
     * Return a bit per *other* item touching the selected footprint.
     * Half-open rectangles that only share an edge do not count.
     * Compute once and reuse for outlines, warning and badges.
     */
    public static int mask(HudEditorControls.Item selected,
                           HudPreviewGeometry.Rect[] footprints) {
        if(selected==null||footprints==null||footprints.length!=ITEMS.length)
            throw new IllegalArgumentException("F7 overlap footprints");
        for(HudPreviewGeometry.Rect item:footprints) {
            if(item==null||item.width<1||item.height<1)
                throw new IllegalArgumentException("Unmeasured F7 footprint");
        }
        HudPreviewGeometry.Rect selectedRect=footprints[selected.ordinal()];
        int result=0;
        for(int i=0;i<ITEMS.length;i++) {
            if(i==selected.ordinal())continue;
            if(HudOverlapPolicy.overlaps(selectedRect,footprints[i]))
                result|=1<<i;
        }
        return result;
    }
    /**
     * Slate only: classify collisions between *active HUD footprints*.
     * Editor placeholders remain draggable even when modules are off, but
     * two objects cannot visibly collide if either is disabled. This filters
     * a precomputed geometric mask without rerunning collision math.
     *
     * The status bit is active when Status OR an independent Metrics row
     * is drawn. Callers decide that from their existing measured snapshot.
     */
    public static int activeMask(HudEditorControls.Item selected,
                                 int geometricMask,int activeBits) {
        if(selected==null||geometricMask<0||activeBits<0||
           (geometricMask&~ALL_BITS)!=0||(activeBits&~ALL_BITS)!=0||
           (geometricMask&(1<<selected.ordinal()))!=0)
            throw new IllegalArgumentException("F7 active overlap bits");
        if((activeBits&(1<<selected.ordinal()))==0)return 0;
        return geometricMask&activeBits;
    }
    public static boolean includes(int mask,HudEditorControls.Item item) {
        if(item==null||(mask&~ALL_BITS)!=0||mask<0)
            throw new IllegalArgumentException("F7 conflict bits");
        return (mask&(1<<item.ordinal()))!=0;
    }
    /** Always select the first collision in sidebar tab order. */
    public static String message(HudEditorControls.Item selected,int mask) {
        if(selected==null||(mask&~ALL_BITS)!=0||mask<0||
           includes(mask,selected))
            throw new IllegalArgumentException("F7 conflict summary");
        if(mask==0)return "";
        int count=Integer.bitCount(mask);
        for(HudEditorControls.Item item:ITEMS) {
            if(includes(mask,item)) {
                return selected.label+" overlaps "+item.label+
                    (count>1?" +"+(count-1):"");
            }
        }
        throw new IllegalStateException("F7 conflict bits");
    }
}
