package dev.ghostfusion.ui;

/**
 * Live Module List miniature for the F7 Slate HUD editor.
 *
 * The reference clients' Mod Overlay/Text GUI previews inform the
 * per-label, width-measured bars, but all geometry comes from GhostC's
 * real enabled-only ModuleListLayout. No synthetic labels are drawn.
 *
 * Does not change the actual HUD, toggle state, sort, positions, mouse hit
 * regions, profile persistence, Minecraft font, or GL state.
 */
public final class ModuleListSlateMiniatureGeometry {
    private ModuleListSlateMiniatureGeometry() { }

    private static int map(int value,int origin,int span,int extent) {
        long relative=(long)value-origin;
        long mapped=relative*extent/span;
        if(mapped<0)return 0;
        if(mapped>extent)return extent;
        return (int)mapped;
    }

    /** Always clips output to the original letterboxed F7 widget footprint. */
    private static void emit(HudPreviewGeometry.Rect bounds,
                             ModuleListLayout.Frame frame,
                             HudPreviewArt.Sink sink,
                             int x0,int y0,int x1,int y1,int role) {
        if(x1<=x0||y1<=y0)return;
        int left=map(x0,frame.x,frame.width,bounds.width);
        int top=map(y0,frame.y,frame.height,bounds.height);
        int right=map(x1-1,frame.x,frame.width,bounds.width)+1;
        int bottom=map(y1-1,frame.y,frame.height,bounds.height)+1;
        right=Math.min(bounds.width,Math.max(left+1,right));
        bottom=Math.min(bounds.height,Math.max(top+1,bottom));
        if(left>=bounds.width||top>=bounds.height)return;
        sink.box(bounds.x+left,bounds.y+top,right-left,bottom-top,role);
    }

    /**
     * Every enabled row produces one measured line, and optional per-label
     * matte and side dash exactly as the native Slate HUD intends.
     *
     * For an empty list, return false so the caller retains the existing
     * generic selectable miniature. No fabricated module names are used.
     */
    public static boolean paint(HudPreviewGeometry.Rect bounds,
                                ModuleListLayout.Frame frame,
                                boolean rightAligned,boolean background,
                                boolean accent,HudPreviewArt.Sink sink) {
        if(bounds==null||frame==null||sink==null||
           bounds.width<1||bounds.height<1)
            throw new IllegalArgumentException("Slate miniature footprint");
        if(frame.rows.isEmpty())return false;
        if(frame.width<1||frame.height<1)
            throw new IllegalArgumentException("Slate miniature frame");
        for(ModuleListLayout.Row row:frame.rows) {
            if(row.textWidth<1||row.height<1)
                throw new IllegalArgumentException("Slate miniature row");
            int textX=rightAligned
                ?frame.x+frame.width-6-row.textWidth:row.textX;
            int surfaceX=textX-6;
            int surfaceW=row.textWidth+12;
            if(background) {
                emit(bounds,frame,sink,surfaceX,row.y,
                    surfaceX+surfaceW,row.y+row.height,HudPreviewArt.PANEL);
            }
            // Approximate the ink with a narrow, actual-width bar at the
            // real row center. Tiny F7 previews collapse to one safe pixel.
            int inkTop=row.y+row.height/2;
            emit(bounds,frame,sink,textX,inkTop,
                textX+row.textWidth,inkTop+1,HudPreviewArt.SECONDARY);
            if(accent) {
                int dashX=rightAligned?surfaceX+surfaceW-2:surfaceX;
                int dashHeight=Math.min(7,Math.max(1,row.height-2));
                int dashY=row.y+(row.height-dashHeight)/2;
                emit(bounds,frame,sink,dashX,dashY,
                    dashX+2,dashY+dashHeight,HudPreviewArt.PRIMARY);
            }
        }
        return true;
    }
}
