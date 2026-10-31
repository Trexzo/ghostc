package dev.ghostfusion.ui;

/**
 * Text-centering and active underline for Slate F7 HUD tabs.
 *
 * Inspired by measured alignment principles in the user's recovered Slinky
 * GUI source; independent first-party implementation and Minecraft font.
 * Tab hit rectangles remain owned by HudEditorControls.
 */
public final class HudEditorSlateTabTypography {
    private HudEditorSlateTabTypography() {}

    public static int labelBudget(int width) {
        if(width<1)throw new IllegalArgumentException("Slate F7 tab width");
        return Math.max(0,width-8);
    }

    public static int labelX(int tabX,int width,int measuredTextWidth) {
        if(width<1||measuredTextWidth<0||
           measuredTextWidth>labelBudget(width))
            throw new IllegalArgumentException("Slate F7 tab font measurement");
        return tabX+(width-measuredTextWidth)/2;
    }

    public static int underlineInset(int width) {
        if(width<1)throw new IllegalArgumentException("Slate F7 underline width");
        return Math.min(6,Math.max(0,(width-1)/4));
    }

    public static int underlineWidth(int width) {
        return Math.max(1,width-2*underlineInset(width));
    }
}
