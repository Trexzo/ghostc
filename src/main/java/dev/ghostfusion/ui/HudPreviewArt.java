package dev.ghostfusion.ui;

/**
 * Allocation-free, resolution-independent miniature preview shapes for the F7
 * HUD editor. The shapes are schematic, never fabricated game information.
 * Actual HUD hitboxes and persistence are controlled elsewhere.
 */
public final class HudPreviewArt {
    public static final int PANEL=0,PRIMARY=1,SECONDARY=2,MUTED=3,TRACK=4;
    private HudPreviewArt(){}
    /** Receives only positive clipped rectangles. Implementations may paint directly. */
    public interface Sink {
        void box(int x,int y,int width,int height,int role);
    }
    /** Fractions are integers in [0,1000] to avoid floating-point frame work. */
    private static void box(HudPreviewGeometry.Rect r,Sink sink,
                            int x0,int y0,int x1,int y1,int role) {
        if(x0<0||y0<0||x1>1000||y1>1000||x1<=x0||y1<=y0)
            throw new IllegalArgumentException("Miniature fractional geometry");
        long l=(long)r.x+(long)r.width*x0/1000L;
        long t=(long)r.y+(long)r.height*y0/1000L;
        long right=(long)r.x+(long)r.width*x1/1000L;
        long bottom=(long)r.y+(long)r.height*y1/1000L;
        if(right<=l||bottom<=t)return;
        if(l<Integer.MIN_VALUE||t<Integer.MIN_VALUE||
           right>Integer.MAX_VALUE||bottom>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Miniature coordinate overflow");
        sink.box((int)l,(int)t,(int)(right-l),(int)(bottom-t),role);
    }
    /**
     * Null widget represents Status; other modules retain the same identity
     * and click geometry used by the normal HUD editor.
     */
    public static void paint(HudPreviewGeometry.Rect r,HudPositions.Widget widget,Sink sink) {
        if(r==null||sink==null||r.width<1||r.height<1)
            throw new IllegalArgumentException("Miniature preview dimensions");
        box(r,sink,0,0,1000,1000,PANEL);
        if(widget==null) {
            // Status: accent rail and two muted information rows.
            box(r,sink,0,0,45,1000,PRIMARY);
            box(r,sink,115,240,675,400,SECONDARY);
            box(r,sink,115,600,900,735,MUTED);
            return;
        }
        switch(widget) {
            case KEYSTROKES:
                // WASD arrangement; active key marked by the brighter tone.
                box(r,sink,365,70,635,340,SECONDARY);
                box(r,sink,60,400,330,690,MUTED);
                box(r,sink,365,400,635,690,PRIMARY);
                box(r,sink,670,400,940,690,MUTED);
                box(r,sink,60,755,940,890,TRACK);
                return;
            case CPS:
                box(r,sink,0,0,40,1000,PRIMARY);
                box(r,sink,125,255,455,425,SECONDARY);
                box(r,sink,530,255,855,425,MUTED);
                box(r,sink,125,670,855,805,TRACK);
                return;
            case TARGET:
                // Name and small HP text above an approximate 70% health bar.
                box(r,sink,65,145,585,320,SECONDARY);
                box(r,sink,730,145,925,320,MUTED);
                box(r,sink,65,595,935,750,TRACK);
                box(r,sink,65,595,675,750,PRIMARY);
                box(r,sink,665,595,675,750,SECONDARY);
                return;
            case MODULE_LIST:
                // Three descending enabled-module rows, left-edge accent.
                box(r,sink,25,80,55,275,PRIMARY);
                box(r,sink,130,125,920,255,SECONDARY);
                box(r,sink,25,390,55,575,PRIMARY);
                box(r,sink,285,415,920,545,MUTED);
                box(r,sink,25,700,55,875,PRIMARY);
                box(r,sink,475,715,920,850,MUTED);
                return;
            default: throw new IllegalArgumentException("Unknown HUD miniature");
        }
    }
}
