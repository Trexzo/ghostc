package dev.ghostfusion.ui;

/**
 * Pure-Java geometry contract for the interactive ClickGUI chrome.
 * Keep native mouse hitboxes identical to the rectangles drawn by Forge.
 * Very small scaled viewports show a non-interactive resize notice instead
 * of allowing overlapping theme, tabs, categories, settings or key capture.
 */
public final class GuiChrome {
    public enum Header { NONE, MODULES, HUD, LAYOUT, THEME }
    private GuiChrome() { }

    /**
     * The wide and compact layouts have different sidebar widths. The HUD tab
     * ends at sidebar+131, and the layout button starts at width-99. Requiring
     * width >= sidebar+230 keeps their hit rectangles disjoint in BOTH modes.
     * A fixed 445px minimum made Compact (max 390px) permanently unusable.
     */
    public static boolean interactive(GuiLayout g) {
        return g != null && g.height >= 250 &&
            g.sidebar >= 70 && g.width >= g.sidebar + 230;
    }

    private static boolean hit(int px,int py,int x,int y,int w,int h) {
        return w > 0 && h > 0 && (long)px >= x && (long)py >= y &&
            (long)px < (long)x+w && (long)py < (long)y+h;
    }

    public static Header headerAt(GuiLayout g,int mx,int my) {
        if (!interactive(g)) return Header.NONE;
        if (hit(mx,my,g.x+g.width-99,g.y+6,40,17)) return Header.LAYOUT;
        if (hit(mx,my,g.x+g.width-55,g.y+6,50,17)) return Header.THEME;
        if (hit(mx,my,g.x+g.sidebar+8,g.y+3,70,24)) return Header.MODULES;
        if (hit(mx,my,g.x+g.sidebar+84,g.y+3,47,24)) return Header.HUD;
        return Header.NONE;
    }

    /**
     * Returns 0=ALL and 1..5=the same category order as drawSidebar.
     * Rejects the 4px vertical row gaps, footer, and all invisible rows.
     */
    public static int categoryIndex(GuiLayout g,int mx,int my) {
        if (!interactive(g)) return -1;
        int left=g.x+4, top=g.y+62, width=g.sidebar-8;
        for (int i=0;i<6;i++) {
            int row=top+i*23;
            if (row+19>g.y+g.height-48) break;
            if (hit(mx,my,left,row,width,19)) return i;
        }
        return -1;
    }
}
