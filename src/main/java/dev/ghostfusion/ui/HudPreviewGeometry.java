package dev.ghostfusion.ui;

/**
 * Letterboxed representation of the real scaled Minecraft viewport inside
 * the F7 HUD editor panel. The same normalized anchor is used in both places,
 * so a drag has the same screen-relative placement on wide and tall displays.
 *
 * Pure geometry: no LWJGL, Minecraft classes, native input or render state.
 */
public final class HudPreviewGeometry {
    public static final class Rect {
        public final int x, y, width, height;
        Rect(int x,int y,int width,int height) {
            this.x=x;this.y=y;this.width=width;this.height=height;
        }
        public boolean contains(int px,int py) {
            return px>=x && py>=y && (long)px<(long)x+width &&
                (long)py<(long)y+height;
        }
    }

    private final int screenWidth, screenHeight;
    public final Rect canvas;

    public HudPreviewGeometry(int panelX,int panelY,int panelWidth,int panelHeight,
                              int scaledScreenWidth,int scaledScreenHeight) {
        if(panelWidth<1||panelHeight<1||scaledScreenWidth<1||scaledScreenHeight<1)
            throw new IllegalArgumentException("HUD preview viewport");
        // Double math avoids overflow when a large screen is mapped to a
        // narrow editor. Each rounded dimension stays inside its panel.
        double scale=Math.min((double)panelWidth/scaledScreenWidth,
                              (double)panelHeight/scaledScreenHeight);
        int width=Math.max(1,Math.min(panelWidth,(int)Math.floor(scaledScreenWidth*scale)));
        int height=Math.max(1,Math.min(panelHeight,(int)Math.floor(scaledScreenHeight*scale)));
        this.screenWidth=scaledScreenWidth;
        this.screenHeight=scaledScreenHeight;
        this.canvas=new Rect(panelX+(panelWidth-width)/2,
            panelY+(panelHeight-height)/2,width,height);
    }

    public int displayWidth(int realWidth) {
        if(realWidth<1)throw new IllegalArgumentException("HUD element width");
        return Math.max(1,Math.min(canvas.width,
            (int)Math.round((double)realWidth*canvas.width/screenWidth)));
    }
    public int displayHeight(int realHeight) {
        if(realHeight<1)throw new IllegalArgumentException("HUD element height");
        return Math.max(1,Math.min(canvas.height,
            (int)Math.round((double)realHeight*canvas.height/screenHeight)));
    }
    public Rect widget(HudPositions anchors,HudPositions.Widget widget) {
        if(widget==null)throw new IllegalArgumentException("HUD widget");
        return widget(anchors,widget,widget.width,widget.height);
    }
    public Rect widget(HudPositions anchors,HudPositions.Widget widget,
                       int actualWidth,int actualHeight) {
        if(anchors==null||widget==null)throw new IllegalArgumentException("HUD widget");
        int width=displayWidth(actualWidth),height=displayHeight(actualHeight);
        return new Rect(canvas.x+anchors.pixelX(widget,canvas.width,width),
            canvas.y+anchors.pixelY(widget,canvas.height,height),width,height);
    }
    public Rect status(UiState ui,int actualWidth,int actualHeight) {
        if(ui==null)throw new IllegalArgumentException("HUD status");
        int width=displayWidth(actualWidth),height=displayHeight(actualHeight);
        return new Rect(canvas.x+ui.pixelX(canvas.width,width),
            canvas.y+ui.pixelY(canvas.height,height),width,height);
    }
    public void moveWidget(HudPositions anchors,HudPositions.Widget widget,
                           int mouseX,int mouseY,int grabX,int grabY) {
        if(widget==null)throw new IllegalArgumentException("HUD widget");
        moveWidget(anchors,widget,mouseX,mouseY,grabX,grabY,widget.width,widget.height);
    }
    public void moveWidget(HudPositions anchors,HudPositions.Widget widget,
                           int mouseX,int mouseY,int grabX,int grabY,
                           int actualWidth,int actualHeight) {
        Rect previous=widget(anchors,widget,actualWidth,actualHeight);
        anchors.positionPixels(widget,mouseX-canvas.x-grabX,mouseY-canvas.y-grabY,
            canvas.width,canvas.height,previous.width,previous.height);
    }
    public void moveStatus(UiState ui,int actualWidth,int actualHeight,
                           int mouseX,int mouseY,int grabX,int grabY) {
        Rect previous=status(ui,actualWidth,actualHeight);
        ui.positionPixels(mouseX-canvas.x-grabX,mouseY-canvas.y-grabY,
            canvas.width,canvas.height,previous.width,previous.height);
    }
}