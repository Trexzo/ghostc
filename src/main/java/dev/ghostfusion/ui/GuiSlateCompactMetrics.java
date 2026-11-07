package dev.ghostfusion.ui;

/**
 * Pixel budgets for the original single-column ClickGUI when a Slate
 * workspace cannot fit at the current Minecraft scaled resolution.
 *
 * Rendering only: all module/setting input regions stay in GuiHitZones.
 * Preserves at least eight pixels between a setting label and its control.
 */
public final class GuiSlateCompactMetrics {
    private GuiSlateCompactMetrics() {}
    private static int controlWidth(GuiLayout g) {
        return g.width-g.sidebar;
    }
    public static int moduleLabelBudget(GuiLayout g) {
        if(g==null)throw new IllegalArgumentException("Slate fallback layout");
        return Math.max(0,controlWidth(g)-77);
    }
    public static int barWidth(GuiLayout g) {
        if(g==null)throw new IllegalArgumentException("Slate fallback layout");
        return Math.max(33,controlWidth(g)/3);
    }
    public static int labelBudget(GuiLayout g,int kind) {
        if(g==null||kind<0||kind>2)
            throw new IllegalArgumentException("Slate fallback setting");
        int left=g.x+g.sidebar+15;
        int right=g.x+g.width-17;
        // Bool uses the same 27px animated Slate switch as the wide view.
        // Numeric values need 39px before the slider track.
        int control=kind==0?right-31:
            kind==1?right-barWidth(g)-39:
                    choiceX(g);
        return Math.max(0,control-left-8);
    }
    /** Fallback choice text shares no pixels with the arrow indicator. */
    public static int choiceX(GuiLayout g) {
        if(g==null)throw new IllegalArgumentException("Slate fallback choice");
        return g.x+g.width-17-Math.min(controlWidth(g)/3,90);
    }
    public static int choiceBudget(GuiLayout g) {
        if(g==null)throw new IllegalArgumentException("Slate fallback choice");
        return Math.max(0,Math.min(controlWidth(g)/3,90)-13);
    }
    public static int choiceArrowX(GuiLayout g) {
        return choiceX(g)+choiceBudget(g)+4;
    }
}
