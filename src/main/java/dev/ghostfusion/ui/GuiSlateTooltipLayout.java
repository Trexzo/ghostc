package dev.ghostfusion.ui;

/**
 * Read-only Slate tooltip placement within the actual Minecraft GUI content
 * panel. Pure pixel geometry: never changes hover targets or UI state.
 */
public final class GuiSlateTooltipLayout {
    public static final int HEIGHT=19;
    public static final int PAD_X=6;
    public static final int MAX_TEXT_WIDTH=260;
    private GuiSlateTooltipLayout(){}

    public static final class Frame {
        public final int x,y,width,height,textX,textY,textBudget;
        private Frame(int x,int y,int width,int textBudget) {
            this.x=x;this.y=y;this.width=width;this.height=HEIGHT;
            this.textX=x+PAD_X;this.textY=y+5;this.textBudget=textBudget;
        }
    }

    /**
     * Reserve 20px underneath the content for error and keyboard help.
     * A tooltip cannot escape the content panel even at small GUI scales.
     * Returns null if there isn't room to draw a readable caption.
     */
    public static Frame place(int panelX,int panelY,int panelWidth,int panelHeight,
                              int mouseX,int mouseY,int measuredTextWidth) {
        if(panelWidth<1||panelHeight<1||measuredTextWidth<0)
            throw new IllegalArgumentException("Slate tooltip dimensions");
        int availableW=panelWidth-16,availableH=panelHeight-20;
        if(availableW<2*PAD_X+12||availableH<HEIGHT+4)return null;
        int textBudget=Math.min(MAX_TEXT_WIDTH,availableW-2*PAD_X);
        int textWidth=Math.min(textBudget,measuredTextWidth);
        int boxWidth=textWidth+2*PAD_X;
        long maxX=(long)panelX+panelWidth-8-boxWidth;
        long minX=(long)panelX+8;
        if(maxX<minX)return null;
        int x=(int)Math.max(minX,Math.min(maxX,(long)mouseX+12));
        long minY=(long)panelY+2,maxY=(long)panelY+availableH-HEIGHT;
        long target=(long)mouseY+12;
        if(target>maxY)target=(long)mouseY-HEIGHT-7;
        int y=(int)Math.max(minY,Math.min(maxY,target));
        return new Frame(x,y,boxWidth,textBudget);
    }
}
