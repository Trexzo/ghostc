package dev.ghostfusion.ui;

/**
 * F7 Slate Keystrokes miniature derived solely from real display options.
 *
 * Uses the exact 83-pixel wide W/A/S/D footprint and the native HUD's
 * 37/57/77-pixel heights: Jump and Sneak are independent optional rows.
 * Key shapes are deliberately neutral (not fictitiously held down).
 *
 * Pure, bounded paint geometry: no input polling, game state, GL calls,
 * keyboard bindings, hitbox changes or profile writes.
 */
public final class KeystrokesSlateMiniatureGeometry {
    public static final int WIDTH=83;
    private static final int BASE_HEIGHT=37;
    private static final int TILE_HEIGHT=17;
    private KeystrokesSlateMiniatureGeometry() { }

    private static void emit(HudPreviewGeometry.Rect bounds,
                             int originalHeight,HudPreviewArt.Sink sink,
                             int x0,int y0,int x1,int y1,int role) {
        if(x0<0||y0<0||x1>WIDTH||y1>originalHeight||
           x1<=x0||y1<=y0)
            throw new IllegalArgumentException("Slate keys miniature rectangle");
        int l=(int)((long)x0*bounds.width/WIDTH);
        int t=(int)((long)y0*bounds.height/originalHeight);
        int r=(int)(((long)x1*bounds.width+WIDTH-1)/WIDTH);
        int b=(int)(((long)y1*bounds.height+originalHeight-1)/originalHeight);
        if(r<=l||b<=t)return;
        sink.box(bounds.x+l,bounds.y+t,r-l,b-t,role);
    }

    private static void tile(HudPreviewGeometry.Rect bounds,int originalHeight,
                             HudPreviewArt.Sink sink,int x,int y,int width) {
        emit(bounds,originalHeight,sink,x,y,x+width,y+TILE_HEIGHT,
            HudPreviewArt.TRACK);
        // Neutral hint of a bound-key label: never imply a key is pressed.
        int markerWidth=Math.min(14,Math.max(5,width/3));
        int markerLeft=x+(width-markerWidth)/2;
        emit(bounds,originalHeight,sink,markerLeft,y+7,
            markerLeft+markerWidth,y+9,HudPreviewArt.SECONDARY);
    }

    public static void paint(HudPreviewGeometry.Rect bounds,
                             boolean showJump,boolean showSneak,
                             HudPreviewArt.Sink sink) {
        if(bounds==null||sink==null||bounds.width<1||bounds.height<1)
            throw new IllegalArgumentException("Slate keys miniature");
        int height=BASE_HEIGHT+(showJump?20:0)+(showSneak?20:0);
        emit(bounds,height,sink,0,0,WIDTH,height,HudPreviewArt.PANEL);
        // Exact native order: W; A/S/D; then optional Jump and Sneak.
        tile(bounds,height,sink,29,0,25);
        tile(bounds,height,sink,0,20,25);
        tile(bounds,height,sink,29,20,25);
        tile(bounds,height,sink,58,20,25);
        int row=40;
        if(showJump) {
            tile(bounds,height,sink,0,row,WIDTH);
            row+=20;
        }
        if(showSneak)tile(bounds,height,sink,0,row,WIDTH);
    }
}
