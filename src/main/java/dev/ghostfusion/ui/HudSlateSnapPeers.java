package dev.ghostfusion.ui;

/**
 * Slate-only visibility filtering for F7 magnetic alignment.
 *
 * The editor still paints and hit-tests every draggable placeholder.
 * Magnetic guides must only consider HUD objects currently visible in-game,
 * plus the canvas frame and center (owned by HudSnapPolicy). This filter
 * never changes the stored anchors, capture policy or native input.
 */
public final class HudSlateSnapPeers {
    private static final HudEditorControls.Item[] ITEMS=
        HudEditorControls.Item.values();
    private static final int ALL_BITS=(1<<ITEMS.length)-1;
    private HudSlateSnapPeers() { }

    /** All preview types remain available as magnetic targets in old themes. */
    public static int allBits() {return ALL_BITS;}

    /**
     * Footprints are indexed by the native F7 Item order:
     * Status, Keys, CPS, Target and Module List. The selected item must
     * have a null peer slot. All others must contain measured geometry.
     *
     * No visibility requirement is imposed on the dragged item itself:
     * a disabled element being arranged for later use may still align with
     * genuinely visible peers. Returned peers preserve existing tab order.
     */
    public static HudPreviewGeometry.Rect[] peers(
            HudEditorControls.Item selected,
            HudPreviewGeometry.Rect[] candidates,int visibleBits) {
        if(selected==null||candidates==null||candidates.length!=ITEMS.length||
           visibleBits<0||(visibleBits&~ALL_BITS)!=0)
            throw new IllegalArgumentException("Slate HUD snap candidates");
        int count=0;
        for(int i=0;i<ITEMS.length;i++) {
            HudPreviewGeometry.Rect r=candidates[i];
            if(i==selected.ordinal()) {
                if(r!=null)throw new IllegalArgumentException(
                    "Selected HUD cannot be its own snap target");
                continue;
            }
            if(r==null||r.width<1||r.height<1)
                throw new IllegalArgumentException("Missing HUD snap footprint");
            if((visibleBits&(1<<i))!=0)count++;
        }
        HudPreviewGeometry.Rect[] result=new HudPreviewGeometry.Rect[count];
        int next=0;
        for(int i=0;i<ITEMS.length;i++)
            if(i!=selected.ordinal() && (visibleBits&(1<<i))!=0)
                result[next++]=candidates[i];
        return result;
    }
}
