package dev.ghostfusion.ui;

/**
 * Pure, screen-bounded ClickGUI geometry. All coordinates and panel dimensions
 * derive from one immutable layout, so resized GuiTextField hitboxes match draws.
 * Extremely small viewports favor containment over full feature visibility.
 */
public final class GuiLayout {
    public final int x, y, width, height, sidebar;
    public final int listY, listHeight, visibleRows;
    public final int previewX, previewY, previewWidth, previewHeight;
    public final int searchX, searchY, searchWidth;
    private GuiLayout(int x,int y,int width,int height,int sidebar) {
        this.x=x;this.y=y;this.width=width;this.height=height;this.sidebar=sidebar;
        listY=y+Math.min(73,height);
        listHeight=Math.min(Math.max(0,(height-91)/2),Math.max(0,y+height-listY));
        visibleRows=listHeight/28;
        searchX=Math.min(x+width-1, x+sidebar+12);
        searchY=Math.min(y+height-1,y+45);
        searchWidth=Math.max(1,Math.min(Math.max(0,width-sidebar-24),x+width-searchX));
        previewX=Math.min(x+width,x+sidebar+12);
        previewY=Math.min(y+height,y+82);
        previewWidth=Math.max(0,Math.min(Math.max(0,width-sidebar-24),x+width-previewX));
        previewHeight=Math.max(0,Math.min(Math.max(0,height-104),y+height-previewY));
    }
    public static GuiLayout calculate(int screenWidth,int screenHeight,UiState.Layout style) {
        if(screenWidth<1||screenHeight<1||style==null)
            throw new IllegalArgumentException("viewport/layout");
        final int maxW=style==UiState.Layout.WIDE?600:390;
        int width=Math.max(1,Math.min(maxW,screenWidth>16?screenWidth-16:screenWidth));
        int height=Math.max(1,Math.min(430,screenHeight>12?screenHeight-12:screenHeight));
        int x=Math.max(0,(screenWidth-width)/2),y=Math.max(0,(screenHeight-height)/2);
        int ideal=style==UiState.Layout.WIDE?118:93;
        int sidebar=Math.min(ideal,Math.max(0,width-34));
        sidebar=Math.min(sidebar,Math.max(0,width/3));
        return new GuiLayout(x,y,width,height,sidebar);
    }
    public boolean insidePanel(int screenWidth,int screenHeight) {
        return x>=0&&y>=0&&width>0&&height>0&&
            x+width<=screenWidth&&y+height<=screenHeight&&
            previewX>=x&&previewX+previewWidth<=x+width&&
            previewY>=y&&previewY+previewHeight<=y+height&&
            searchX>=x&&searchX+searchWidth<=x+width&&
            searchY>=y&&searchY<=y+height;
    }
}
