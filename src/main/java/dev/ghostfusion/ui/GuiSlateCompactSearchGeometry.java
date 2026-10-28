package dev.ghostfusion.ui;

/**
 * Shared compact Slate search surface, native input and clear-hit geometry.
 * Values come exclusively from the actual (possibly scaled) GuiLayout.
 * Unlike the wide view this bar occupies the legacy module-list header.
 */
public final class GuiSlateCompactSearchGeometry {
    public static final int SURFACE_HEIGHT=21;
    public static final int INPUT_HEIGHT=17;
    private GuiSlateCompactSearchGeometry() {}
    public static int surfaceX(GuiLayout g){return g.searchX-2;}
    public static int surfaceY(GuiLayout g){return g.searchY-2;}
    public static int surfaceWidth(GuiLayout g){return g.searchWidth+4;}
    public static int fieldX(GuiLayout g){return g.searchX+5;}
    public static int fieldY(GuiLayout g){return g.searchY;}
    public static int fieldWidth(GuiLayout g){return Math.max(1,g.searchWidth-29);}
    public static int clearX(GuiLayout g){return surfaceX(g)+surfaceWidth(g)-18;}
    public static int clearY(GuiLayout g){return surfaceY(g)+3;}
    public static boolean surfaceHit(GuiLayout g,int mx,int my) {
        return g!=null && hit(mx,my,surfaceX(g),surfaceY(g),
                             surfaceWidth(g),SURFACE_HEIGHT);
    }
    public static boolean clearHit(GuiLayout g,int mx,int my) {
        return g!=null && hit(mx,my,clearX(g),clearY(g),14,15);
    }
    private static boolean hit(int mx,int my,int x,int y,int w,int h) {
        return w>0&&h>0&&(long)mx>=x&&(long)my>=y&&
            (long)mx<(long)x+w&&(long)my<(long)y+h;
    }
}
