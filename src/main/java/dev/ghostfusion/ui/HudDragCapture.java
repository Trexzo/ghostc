package dev.ghostfusion.ui;

/** Owner-thread capture of a single HUD editor anchor and source viewport.
 * A cancelled gesture is committed only when its final normalized position
 * differs from the mouse-down position. No I/O or native Minecraft types. */
public final class HudDragCapture {
    private final Thread owner=Thread.currentThread();
    private boolean active;
    private HudPositions.Widget widget; // null captures Status
    private double initialX,initialY;
    private int screenWidth,screenHeight;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("HUD capture owner thread");
    }
    private static double x(UiState ui,HudPositions.Widget widget) {
        return widget==null?ui.hudX():ui.widgets().x(widget);
    }
    private static double y(UiState ui,HudPositions.Widget widget) {
        return widget==null?ui.hudY():ui.widgets().y(widget);
    }
    public void begin(UiState ui,HudPositions.Widget target,int width,int height) {
        check();
        if(active)throw new IllegalStateException("Nested HUD capture");
        if(ui==null || width<1 || height<1)
            throw new IllegalArgumentException("HUD capture viewport/state");
        widget=target;
        initialX=x(ui,target);
        initialY=y(ui,target);
        screenWidth=width;screenHeight=height;
        active=true;
    }
    public boolean active(){check();return active;}
    public boolean sameViewport(int width,int height) {
        check();return active && screenWidth==width && screenHeight==height;
    }
    public boolean finish(UiState ui) {
        check();
        if(!active)return false;
        if(ui==null)throw new IllegalArgumentException("HUD capture UI");
        boolean moved=Double.compare(initialX,x(ui,widget))!=0 ||
                      Double.compare(initialY,y(ui,widget))!=0;
        active=false;widget=null;
        initialX=0;initialY=0;screenWidth=0;screenHeight=0;
        return moved;
    }
}
