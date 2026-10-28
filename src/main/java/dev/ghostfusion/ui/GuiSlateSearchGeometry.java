package dev.ghostfusion.ui;

/** Shared allocation-free Slate search text, clear button and surface geometry. */
public final class GuiSlateSearchGeometry {
    public static final int SURFACE_HEIGHT=21;
    public static final int INPUT_HEIGHT=17;
    private GuiSlateSearchGeometry() {}
    public static int fieldX(GuiSlateWorkspace p){return p.searchX()+24;}
    public static int fieldY(GuiSlateWorkspace p){return p.searchY()+2;}
    public static int fieldWidth(GuiSlateWorkspace p){return Math.max(1,p.searchWidth()-50);}
    public static int clearX(GuiSlateWorkspace p){return p.searchX()+p.searchWidth()-21;}
    public static int clearY(GuiSlateWorkspace p){return p.searchY()+3;}
    public static boolean clearHit(GuiSlateWorkspace p,int mx,int my) {
        return p!=null && (long)mx>=clearX(p) && (long)mx<clearX(p)+16L &&
            (long)my>=clearY(p) && (long)my<clearY(p)+15L;
    }
    public static boolean surfaceHit(GuiSlateWorkspace p,int mx,int my) {
        return p!=null && (long)mx>=p.searchX() &&
            (long)mx<(long)p.searchX()+p.searchWidth() &&
            (long)my>=p.searchY() &&
            (long)my<(long)p.searchY()+SURFACE_HEIGHT;
    }
}
