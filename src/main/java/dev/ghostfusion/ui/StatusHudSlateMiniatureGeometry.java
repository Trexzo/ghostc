package dev.ghostfusion.ui;

/**
 * Slate-only F7 Status + Metrics preview. Actual StatusHudLayout supplies
 * the row-presence flags and measured vertical ordering. The thumbnail
 * contains deliberately neutral text bars, never synthetic game readings.
 *
 * Does not sample performance, coordinates, ping, or status; never changes
 * display settings, pointer hit geometry or persistent HUD positions.
 */
public final class StatusHudSlateMiniatureGeometry {
    private StatusHudSlateMiniatureGeometry() { }

    private static int bottom(StatusHudLayout layout,int y) {
        int end=layout.measuredHeight;
        int[] starts={layout.statusY,layout.fpsY,
            layout.coordinatesY,layout.pingY};
        for(int start:starts)
            if(start>y && start<end)end=start;
        return end;
    }

    private static void mark(HudPreviewGeometry.Rect bounds,
                             StatusHudLayout layout,HudPreviewArt.Sink sink,
                             int x0,int y0,int x1,int y1,int role) {
        if(x0<0||y0<0||x1<=x0||y1<=y0||
           x1>layout.measuredWidth||y1>layout.measuredHeight)
            throw new IllegalArgumentException("Slate Status preview rectangle");
        long w=layout.measuredWidth,h=layout.measuredHeight;
        int left=(int)((long)x0*bounds.width/w);
        int top=(int)((long)y0*bounds.height/h);
        int right=(int)(((long)x1*bounds.width+w-1)/w);
        int below=(int)(((long)y1*bounds.height+h-1)/h);
        // Bound the rounded visual to the already measured editor footprint.
        right=Math.min(bounds.width,right);
        below=Math.min(bounds.height,below);
        if(right<=left||below<=top)return;
        sink.box(bounds.x+left,bounds.y+top,right-left,below-top,role);
    }

    private static void line(HudPreviewGeometry.Rect bounds,
                             StatusHudLayout layout,HudPreviewArt.Sink sink,
                             int y,int percentage,int role) {
        if(y<0)return;
        int after=bottom(layout,y);
        if(after<=y)return;
        int x0=Math.min(3,layout.measuredWidth-1);
        int goal=(int)((long)layout.measuredWidth*percentage/100);
        int x1=Math.min(layout.measuredWidth,Math.max(x0+1,goal));
        int center=y+(after-y-1)/2;
        mark(bounds,layout,sink,x0,center,x1,center+1,role);
    }

    public static void paint(HudPreviewGeometry.Rect bounds,
                             StatusHudLayout layout,boolean realStatusEnabled,
                             HudPreviewArt.Sink sink) {
        if(bounds==null||layout==null||sink==null||
           bounds.width<1||bounds.height<1||
           layout.measuredWidth<1||layout.measuredHeight<1)
            throw new IllegalArgumentException("Slate Status preview");
        sink.box(bounds.x,bounds.y,bounds.width,bounds.height,HudPreviewArt.PANEL);
        // When every feature is off, editor's selectable placeholder is
        // still outlined, but never claims that Status is actually active.
        if(layout.statusY>=0) {
            line(bounds,layout,sink,layout.statusY,68,
                realStatusEnabled?HudPreviewArt.SECONDARY:HudPreviewArt.MUTED);
            if(realStatusEnabled) {
                int end=bottom(layout,layout.statusY);
                mark(bounds,layout,sink,0,layout.statusY,1,end,HudPreviewArt.PRIMARY);
            }
        }
        // These bars are named *by row position only*, not fabricated
        // strings/numbers. Hidden metrics add no F7 decoration.
        line(bounds,layout,sink,layout.fpsY,47,HudPreviewArt.SECONDARY);
        line(bounds,layout,sink,layout.coordinatesY,88,HudPreviewArt.SECONDARY);
        line(bounds,layout,sink,layout.pingY,58,HudPreviewArt.MUTED);
    }
}
