package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Read-only full-label reveal for truly clipped Slate F7 HUD tabs.
 * The existing HudEditorControls.tabAt owns every hover region;
 * this helper never creates an action target or mutates selection.
 */
public final class HudEditorSlateTabTooltip {
    private HudEditorSlateTabTooltip() { }

    /**
     * Uses the same measured tab budget and HudTextFit as the native
     * renderer. A null result means the tab fits or the pointer is not in
     * a tab. Hidden/full labels are not precomputed or retained.
     */
    public static String hovered(int left,int top,int totalWidth,
                                 int mouseX,int mouseY,
                                 ToIntFunction<String> font) {
        if(font==null || totalWidth<0)
            throw new IllegalArgumentException("Slate F7 tab tooltip");
        HudEditorControls.Item tab=HudEditorControls.tabAt(
            left,top,totalWidth,mouseX,mouseY);
        if(tab==null)return null;
        int slotWidth=HudEditorControls.tabWidth(totalWidth,tab);
        int budget=HudEditorSlateTabTypography.labelBudget(slotWidth);
        String fitted=HudTextFit.fit(tab.label,budget,font);
        return tab.label.equals(fitted)?null:tab.label;
    }
}
