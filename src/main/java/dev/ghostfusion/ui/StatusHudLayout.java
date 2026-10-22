package dev.ghostfusion.ui;

/**
 * Pure, measured Status + Metrics HUD layout. All dimensions are the same
 * scaled Minecraft pixels used by the Forge font renderer. The complete
 * feature block shares one normalized anchor even with Status disabled.
 *
 * Measurements can be wider/taller than the viewport (very small windows or
 * giant coordinates). The visible footprint is clamped; Forge trims native
 * text to the remaining screen width and skips fully offscreen rows.
 */
public final class StatusHudLayout {
    public final int x, y, width, height;
    public final int statusY, fpsY, coordinatesY, pingY;
    public final int measuredWidth, measuredHeight;
    private StatusHudLayout(int x,int y,int width,int height,
                            int measuredWidth,int measuredHeight,
                            int statusY,int fpsY,int coordinatesY,int pingY) {
        this.x=x;this.y=y;this.width=width;this.height=height;
        this.measuredWidth=measuredWidth;this.measuredHeight=measuredHeight;
        this.statusY=statusY;this.fpsY=fpsY;this.coordinatesY=coordinatesY;this.pingY=pingY;
    }
    /** Original callers keep precisely their pre-ping footprint. */
    public static StatusHudLayout calculate(
            UiState ui,int viewportWidth,int viewportHeight,
            boolean showStatus,boolean showFps,boolean showCoordinates,
            int statusWidth,int statusHeight,int fpsWidth,int coordinatesWidth,
            int fontHeight) {
        return calculate(ui,viewportWidth,viewportHeight,
            showStatus,showFps,showCoordinates,false,
            statusWidth,statusHeight,fpsWidth,coordinatesWidth,0,fontHeight);
    }
    /** Extended metrics footprint, measured in scaled Minecraft pixels. */
    public static StatusHudLayout calculate(
            UiState ui,int viewportWidth,int viewportHeight,
            boolean showStatus,boolean showFps,boolean showCoordinates,boolean showPing,
            int statusWidth,int statusHeight,int fpsWidth,int coordinatesWidth,
            int pingWidth,int fontHeight) {
        if(ui==null || viewportWidth<1 || viewportHeight<1 ||
           statusWidth<0 || statusHeight<0 || fpsWidth<0 ||
           coordinatesWidth<0 || pingWidth<0 || fontHeight<1)
            throw new IllegalArgumentException("Status HUD geometry");
        if(showStatus && (statusWidth<1 || statusHeight<1))
            throw new IllegalArgumentException("Status footprint");
        if(!showStatus&&!showFps&&!showCoordinates&&!showPing)
            throw new IllegalArgumentException("No visible status or metric");
        long w=showStatus?statusWidth:0;
        if(showFps)w=Math.max(w,(long)fpsWidth+2);
        if(showCoordinates)w=Math.max(w,(long)coordinatesWidth+2);
        if(showPing)w=Math.max(w,(long)pingWidth+2);
        long h=showStatus?statusHeight:0;
        int statusAt=showStatus?0:-1;
        int fpsAt=-1,coordsAt=-1,pingAt=-1;
        if(showFps) {
            fpsAt=(int)Math.min(Integer.MAX_VALUE,h);
            h+=(long)fontHeight+2;
        }
        if(showCoordinates) {
            coordsAt=(int)Math.min(Integer.MAX_VALUE,h);
            h+=(long)fontHeight+2;
        }
        if(showPing) {
            pingAt=(int)Math.min(Integer.MAX_VALUE,h);
            h+=(long)fontHeight+2;
        }
        int visibleW=(int)Math.min(viewportWidth,w);
        int visibleH=(int)Math.min(viewportHeight,h);
        int originX=ui.pixelX(viewportWidth,visibleW);
        int originY=ui.pixelY(viewportHeight,visibleH);
        return new StatusHudLayout(originX,originY,visibleW,visibleH,
            (int)Math.min(Integer.MAX_VALUE,w),
            (int)Math.min(Integer.MAX_VALUE,h),statusAt,fpsAt,coordsAt,pingAt);
    }
    public boolean fitsViewport(int viewportWidth,int viewportHeight) {
        return viewportWidth>0 && viewportHeight>0 && x>=0 && y>=0 &&
               width>=1 && height>=1 &&
               (long)x+width<=viewportWidth && (long)y+height<=viewportHeight;
    }
}
