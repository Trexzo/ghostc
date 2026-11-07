package dev.ghostfusion.ui;

/**
 * Noninteractive empty-state card within the *visible* Slate module rows.
 * Rise-style search hierarchy, but no assumed module descriptions/assets.
 */
public final class GuiSlateEmptyStateLayout {
    private GuiSlateEmptyStateLayout() {}
    public static final class Frame {
        public final int x,y,width,height;
        public final int titleX,titleY,hintY;
        private Frame(int x,int y,int width,int height) {
            this.x=x;this.y=y;this.width=width;this.height=height;
            this.titleX=x+12;this.titleY=y+13;this.hintY=y+31;
        }
    }
    public static Frame of(GuiSlateWorkspace pane) {
        if(pane==null)throw new IllegalArgumentException("Slate empty workspace");
        int available=pane.moduleRows*29;
        int w=Math.min(156,pane.modulesWidth-12),h=56;
        if(w<66||available<h)return null;
        int x=pane.modulesX+(pane.modulesWidth-w)/2;
        int y=pane.modulesY+(available-h)/2;
        return new Frame(x,y,w,h);
    }
}
