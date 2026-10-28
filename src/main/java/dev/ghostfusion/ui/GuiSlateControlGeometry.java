package dev.ghostfusion.ui;

/**
 * Read-only Slate setting-control accents. Reuses the same module geometry
 * as Forge pointer input; never changes persisted values or click hit zones.
 */
public final class GuiSlateControlGeometry {
    private static final int THUMB_WIDTH=4;
    private static final int CHOICE_ARROW_GUTTER=14;
    private GuiSlateControlGeometry() {}

    /** Left pixel of the 4px thumb, fully contained by the native slider. */
    public static int thumbX(GuiSlateWorkspace pane,double fraction) {
        if(pane==null || !Double.isFinite(fraction) ||
           fraction<0 || fraction>1 || pane.sliderWidth()<THUMB_WIDTH)
            throw new IllegalArgumentException("Slate slider thumb");
        return pane.sliderX()+(int)Math.round(
            (pane.sliderWidth()-THUMB_WIDTH)*fraction);
    }
    /** Value text stops before the icon without touching the label budget. */
    public static int choiceBudget(GuiSlateWorkspace pane) {
        if(pane==null||pane.choiceValueWidth()<=CHOICE_ARROW_GUTTER)
            throw new IllegalArgumentException("Slate choice width");
        return pane.choiceValueWidth()-CHOICE_ARROW_GUTTER;
    }
    /** Exclusive right edge for the actual glyph run. */
    public static int choiceTextRight(GuiSlateWorkspace pane) {
        return pane.choiceValueX()+choiceBudget(pane);
    }
    /** Top-left X of a tiny 5px chevron, inside the choice-value column. */
    public static int chevronX(GuiSlateWorkspace pane) {
        if(pane==null)throw new IllegalArgumentException("Slate choice chevron");
        return pane.choiceValueX()+pane.choiceValueWidth()-8;
    }
}
