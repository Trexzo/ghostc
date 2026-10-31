package dev.ghostfusion.ui;

/**
 * Visual-only F7 Slate canvas guidance.
 *
 * All coordinates belong to the already letterboxed HudPreviewGeometry.
 * This class never changes draggable hit zones or persisted HUD anchors.
 * A quiet thirds grid and small corner brackets replace dense 30px lines.
 */
public final class HudEditorSlateCanvasGeometry {
    private static final HudEditorControls.Item[] ITEMS=HudEditorControls.Item.values();
    private HudEditorSlateCanvasGeometry() {}

    public static int gridX(HudPreviewGeometry.Rect canvas,int third) {
        return thirdLine(canvas,true,third);
    }
    public static int gridY(HudPreviewGeometry.Rect canvas,int third) {
        return thirdLine(canvas,false,third);
    }
    private static int thirdLine(HudPreviewGeometry.Rect r,boolean horizontal,int third) {
        if(r==null||r.width<1||r.height<1||third<1||third>2)
            throw new IllegalArgumentException("F7 Slate grid");
        int span=horizontal?r.width:r.height;
        if(span<3)return -1;
        int offset=Math.max(1,Math.min(span-1,(int)Math.round(span*third/3.0)));
        return (horizontal?r.x:r.y)+offset;
    }
    public static int centerX(HudPreviewGeometry.Rect canvas) {
        if(canvas==null||canvas.width<1||canvas.height<1)
            throw new IllegalArgumentException("F7 Slate center");
        return canvas.x+canvas.width/2;
    }
    public static int centerY(HudPreviewGeometry.Rect canvas) {
        if(canvas==null||canvas.width<1||canvas.height<1)
            throw new IllegalArgumentException("F7 Slate center");
        return canvas.y+canvas.height/2;
    }
    /** Short corner bracket inside a thumbnail, even at tiny GUI scales. */
    public static int corner(HudPreviewGeometry.Rect rect) {
        if(rect==null||rect.width<1||rect.height<1)
            throw new IllegalArgumentException("F7 Slate selected thumbnail");
        return Math.max(1,Math.min(7,Math.min(rect.width,rect.height)/3));
    }
    /**
     * Reverse preview paint order equals original editor pointer selection:
     * Module List > Target > CPS > Keys > Status. No new click behavior.
     */
    public static HudEditorControls.Item hovered(
            HudPreviewGeometry.Rect canvas,
            HudPreviewGeometry.Rect[] footprints,int mx,int my) {
        if(canvas==null||footprints==null||footprints.length!=ITEMS.length)
            throw new IllegalArgumentException("F7 Slate hover footprints");
        for(HudPreviewGeometry.Rect rect:footprints)
            if(rect==null||rect.width<1||rect.height<1)
                throw new IllegalArgumentException("F7 Slate unmeasured thumbnail");
        if(!canvas.contains(mx,my))return null;
        for(int i=ITEMS.length-1;i>=0;i--) {
            if(footprints[i].contains(mx,my))return ITEMS[i];
        }
        return null;
    }
    /**
     * On a stationary pointer, identify exactly the widget that native F7
     * would select at that pixel. Hide the label during a drag so it never
     * obscures snap lines or distracts from precise placement.
     *
     * Caller supplies the already computed reverse-order native hover.
     */
    public static String hoverCaption(HudEditorControls.Item hovered,
                                      boolean dragging) {
        return hoverCaption(hovered,dragging,true,false);
    }

    /**
     * Slate editor previews are positionable even if their live HUD feature
     * is disabled. Label that state rather than implying preview=active.
     * Status is independent from Metrics, which may remain drawn alone.
     * Only the caller can know actual current feature/visible-row flags.
     */
    public static String hoverCaption(HudEditorControls.Item hovered,
                                      boolean dragging,boolean enabled,
                                      boolean visibleMetrics) {
        if(hovered==null||dragging)return null;
        if(hovered==HudEditorControls.Item.STATUS) {
            if(enabled)
                return visibleMetrics?"STATUS + METRICS":"STATUS HUD";
            return visibleMetrics?"METRICS ONLY (STATUS OFF)":"STATUS HUD OFF";
        }
        String name;
        switch(hovered) {
            case KEYS: name="KEYSTROKES";break;
            case CPS: name="CPS METER";break;
            case TARGET: name="TARGET HUD";break;
            case MODULE_LIST: name="MODULE LIST";break;
            default: throw new IllegalArgumentException("F7 Slate hovered widget");
        }
        return enabled?name:name+" OFF";
    }
    public static String coordinateLabel(HudEditorControls.Item item,
                                          double normalizedX,double normalizedY) {
        if(item==null||!Double.isFinite(normalizedX)||!Double.isFinite(normalizedY))
            throw new IllegalArgumentException("F7 Slate anchor label");
        int x=(int)Math.round(100*Math.max(0,Math.min(1,normalizedX)));
        int y=(int)Math.round(100*Math.max(0,Math.min(1,normalizedY)));
        return item.label+"  X "+x+"%  Y "+y+"%";
    }
}
