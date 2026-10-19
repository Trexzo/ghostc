package dev.ghostfusion.ui;

/** Shared real HUD dimensions for rendering, editor preview, and keyboard positioning. */
public final class HudFootprints {
    private HudFootprints() {}
    public static int width(HudPositions.Widget widget) {
        if(widget==null) throw new IllegalArgumentException("widget");
        return widget.width;
    }
    public static int height(HudPositions.Widget widget,boolean jump,boolean sneak,
                             boolean targetHealth) {
        if(widget==null) throw new IllegalArgumentException("widget");
        switch(widget) {
            case KEYSTROKES: return 37+(jump?20:0)+(sneak?20:0);
            case CPS: return 17;
            case TARGET: return targetHealth?43:29;
            default: throw new IllegalArgumentException("widget");
        }
    }
}
