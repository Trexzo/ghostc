package dev.ghostfusion.ui;

/**
 * F7 Slate CPS pictogram follows the actual left-only / left-and-right
 * counter option, matching the runtime's 103x17 neutral matte card.
 *
 * No fabricated click counts, recorded input, player data or persistence.
 * All rectangles are clipped into the existing F7 widget footprint;
 * this model cannot change the real HUD dimensions or editor hitboxes.
 */
public final class CpsSlateMiniatureGeometry {
    private static final int WIDTH=103;
    private static final int HEIGHT=17;
    private CpsSlateMiniatureGeometry() { }

    private static void box(HudPreviewGeometry.Rect r,HudPreviewArt.Sink sink,
                            int x0,int y0,int x1,int y1,int role) {
        if(x0<0||y0<0||x1>WIDTH||y1>HEIGHT||x1<=x0||y1<=y0)
            throw new IllegalArgumentException("Slate CPS preview shape");
        // A minimum one-pixel visual survives small editor thumbnails.
        long left=(long)x0*r.width/WIDTH;
        long top=(long)y0*r.height/HEIGHT;
        long right=((long)x1*r.width+WIDTH-1)/WIDTH;
        long bottom=((long)y1*r.height+HEIGHT-1)/HEIGHT;
        if(right<=left||bottom<=top)return;
        sink.box(r.x+(int)left,r.y+(int)top,
            (int)(right-left),(int)(bottom-top),role);
    }

    public static void paint(HudPreviewGeometry.Rect bounds,boolean showRight,
                             HudPreviewArt.Sink sink) {
        if(bounds==null||sink==null||bounds.width<1||bounds.height<1)
            throw new IllegalArgumentException("Slate CPS preview");
        box(bounds,sink,0,0,WIDTH,HEIGHT,HudPreviewArt.PANEL);
        box(bounds,sink,0,0,1,HEIGHT,HudPreviewArt.PRIMARY);
        box(bounds,sink,1,0,102,1,HudPreviewArt.MUTED);
        // Left key and a neutral count placeholder, not a fake "0 CPS".
        box(bounds,sink,7,6,16,10,HudPreviewArt.SECONDARY);
        box(bounds,sink,19,7,32,9,HudPreviewArt.MUTED);
        if(showRight) {
            // Both counters share the exact one-line native HUD footprint.
            box(bounds,sink,47,5,48,12,HudPreviewArt.TRACK);
            box(bounds,sink,55,6,64,10,HudPreviewArt.SECONDARY);
            box(bounds,sink,68,7,81,9,HudPreviewArt.MUTED);
            box(bounds,sink,86,7,98,9,HudPreviewArt.MUTED);
        } else {
            // Left-only label has no misleading right-side counter.
            box(bounds,sink,40,7,66,9,HudPreviewArt.MUTED);
        }
    }
}
