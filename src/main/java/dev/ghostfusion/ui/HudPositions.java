package dev.ghostfusion.ui;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Independent, resolution-neutral anchors for the three movable feature HUDs.
 * Status HUD keeps its existing UiState.position/hud.x/hud.y contract.
 */
public final class HudPositions {
    public enum Widget {
        KEYSTROKES("keystrokes", 83, 83, 1.0, 0.86),
        CPS("cps", 103, 17, 1.0, 1.0),
        TARGET("target", 160, 43, 0.5, 0.93);
        public final String storageId;
        public final int width, height;
        final double defaultX, defaultY;
        Widget(String id,int width,int height,double x,double y) {
            this.storageId=id;this.width=width;this.height=height;
            this.defaultX=x;this.defaultY=y;
        }
    }
    private final EnumMap<Widget,double[]> anchors=new EnumMap<Widget,double[]>(Widget.class);
    public HudPositions() {
        for(Widget widget:Widget.values())
            anchors.put(widget,new double[]{widget.defaultX,widget.defaultY});
    }
    private static void require(Widget widget) {
        if(widget==null) throw new IllegalArgumentException("HUD widget");
    }
    public double x(Widget widget) { require(widget); return anchors.get(widget)[0]; }
    public double y(Widget widget) { require(widget); return anchors.get(widget)[1]; }
    public void position(Widget widget,double x,double y) {
        require(widget);
        // Validate the pair before changing either coordinate.
        double left=UiState.clamp(x,0,1),top=UiState.clamp(y,0,1);
        double[] value=anchors.get(widget);
        value[0]=left;value[1]=top;
    }
    private static int pixel(double fraction,int viewport,int content) {
        if(viewport<1 || content<1) throw new IllegalArgumentException("HUD viewport");
        return (int)Math.round(fraction*Math.max(0,viewport-content));
    }
    public int pixelX(Widget widget,int viewport,int content) {
        return pixel(x(widget),viewport,content);
    }
    public int pixelY(Widget widget,int viewport,int content) {
        return pixel(y(widget),viewport,content);
    }
    public void positionPixels(Widget widget,int x,int y,int viewportWidth,int viewportHeight,
                               int contentWidth,int contentHeight) {
        require(widget);
        if(viewportWidth<1 || viewportHeight<1 || contentWidth<1 || contentHeight<1)
            throw new IllegalArgumentException("HUD dimensions");
        double fx=(double)x/Math.max(1,viewportWidth-contentWidth);
        double fy=(double)y/Math.max(1,viewportHeight-contentHeight);
        position(widget,fx,fy);
    }
    public static String property(Widget widget,boolean horizontal) {
        require(widget);
        return "hud."+widget.storageId+(horizontal?".x":".y");
    }
}