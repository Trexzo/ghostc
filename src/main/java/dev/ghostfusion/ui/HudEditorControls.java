package dev.ghostfusion.ui;

/** Accessible selection, nudge, and restore-default operations for the F7 HUD editor. */
public final class HudEditorControls {
    public enum Item {
        STATUS(null,"Status"),
        KEYS(HudPositions.Widget.KEYSTROKES,"Keys"),
        CPS(HudPositions.Widget.CPS,"CPS"),
        TARGET(HudPositions.Widget.TARGET,"Target");
        public final HudPositions.Widget widget;
        public final String label;
        Item(HudPositions.Widget widget,String label) {
            this.widget=widget;this.label=label;
        }
    }
    private Item selected=Item.STATUS;
    public Item selected() { return selected; }
    public void select(Item next) {
        if(next==null) throw new IllegalArgumentException("HUD selected item");
        selected=next;
    }
    public static Item itemFor(HudPositions.Widget widget) {
        if(widget==null) return Item.STATUS;
        for(Item item:Item.values())if(widget==item.widget)return item;
        throw new IllegalArgumentException("HUD widget");
    }
    /** Four equal segments, the last accepting the remainder pixel. */
    public static Item tabAt(int left,int top,int width,int mx,int my) {
        if(width<40||mx<left||my<top||my>=top+17||mx>=left+width)
            return null;
        int slot=(int)((long)(mx-left)*4/width);
        return Item.values()[Math.min(3,slot)];
    }
    public static int tabLeft(int left,int width,Item item) {
        if(item==null||width<40)throw new IllegalArgumentException("HUD tabs");
        return left+(int)(((long)width*item.ordinal()+3)/4);
    }
    public static int tabWidth(int width,Item item) {
        if(item==null||width<40)throw new IllegalArgumentException("HUD tabs");
        int index=item.ordinal();
        return (int)(((long)width*(index+1)+3)/4)-
            (int)(((long)width*index+3)/4);
    }
    public void reset(UiState ui) {
        resetIfChanged(ui);
    }
    /** Return whether the selected serialized anchor actually changed. */
    public boolean resetIfChanged(UiState ui) {
        if(ui==null)throw new IllegalArgumentException("UI");
        double beforeX=x(ui),beforeY=y(ui);
        if(selected==Item.STATUS)ui.position(UiState.DEFAULT_HUD_X,UiState.DEFAULT_HUD_Y);
        else ui.widgets().reset(selected.widget);
        return Double.compare(beforeX,x(ui))!=0 ||
            Double.compare(beforeY,y(ui))!=0;
    }
    private double x(UiState ui) {
        return selected==Item.STATUS?ui.hudX():ui.widgets().x(selected.widget);
    }
    private double y(UiState ui) {
        return selected==Item.STATUS?ui.hudY():ui.widgets().y(selected.widget);
    }
    /** Preserve exact normalized coordinates for unaffected axes and
     * avoid rewriting an anchor when a nudge clamps to the same pixel. */
    public boolean nudgeIfChanged(UiState ui,int dx,int dy,int screenWidth,int screenHeight,
                                  int contentWidth,int contentHeight) {
        if(ui==null || screenWidth<1 || screenHeight<1 ||
           contentWidth<1 || contentHeight<1)
            throw new IllegalArgumentException("HUD nudge dimensions");
        double beforeX=x(ui),beforeY=y(ui);
        int px=selected==Item.STATUS?ui.pixelX(screenWidth,contentWidth):
            ui.widgets().pixelX(selected.widget,screenWidth,contentWidth);
        int py=selected==Item.STATUS?ui.pixelY(screenHeight,contentHeight):
            ui.widgets().pixelY(selected.widget,screenHeight,contentHeight);
        int maxX=Math.max(0,screenWidth-contentWidth);
        int maxY=Math.max(0,screenHeight-contentHeight);
        int nextX=Math.max(0,Math.min(maxX,safeAdd(px,dx)));
        int nextY=Math.max(0,Math.min(maxY,safeAdd(py,dy)));
        // No effective pixel movement: never quantize or rewrite the anchor.
        if(nextX==px && nextY==py)return false;
        double fx=nextX==px?beforeX:(double)nextX/Math.max(1,maxX);
        double fy=nextY==py?beforeY:(double)nextY/Math.max(1,maxY);
        if(selected==Item.STATUS)ui.position(fx,fy);
        else ui.widgets().position(selected.widget,fx,fy);
        return Double.compare(beforeX,x(ui))!=0 ||
            Double.compare(beforeY,y(ui))!=0;
    }
    /**
     * Nudge by physical Minecraft scaled pixels, not by editor-preview pixels.
     * Preserves all other widget anchors and clamps within the real screen.
     */
    public void nudge(UiState ui,int dx,int dy,int screenWidth,int screenHeight,
                      int contentWidth,int contentHeight) {
        nudgeIfChanged(ui,dx,dy,screenWidth,screenHeight,contentWidth,contentHeight);
    }
    private static int safeAdd(int a,int b) {
        long sum=(long)a+b;
        return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,sum));
    }
}
