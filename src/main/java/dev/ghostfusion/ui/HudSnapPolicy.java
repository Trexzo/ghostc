package dev.ghostfusion.ui;

/**
 * Pure, deterministic HUD editor magnet alignment. Preview pixels are the
 * authoritative coordinates; neither persisted anchors nor other widgets
 * are ever modified by this policy. No native or Minecraft dependency.
 */
public final class HudSnapPolicy {
    private HudSnapPolicy() {}
    public static final int DEFAULT_TOLERANCE=5;
    public static final int NO_GUIDE=Integer.MIN_VALUE;

    public static final class Result {
        public final int x,y,guideX,guideY;
        private Result(int x,int y,int guideX,int guideY) {
            this.x=x;this.y=y;this.guideX=guideX;this.guideY=guideY;
        }
    }
    private static final class Axis {
        final int original;
        int origin,distance,guide;
        Axis(int origin,int tolerance) {
            this.original=origin;this.origin=origin;this.distance=tolerance+1;
            this.guide=NO_GUIDE;
        }
        void consider(long proposed,long guideCoordinate,long floor,long ceiling) {
            if(proposed<floor||proposed>ceiling ||
               guideCoordinate<Integer.MIN_VALUE || guideCoordinate>Integer.MAX_VALUE)
                return;
            long delta=Math.abs(proposed-original);
            // Strict comparison preserves deterministic precedence on ties:
            // canvas frame and center guides win over overlapping peers.
            if(delta<distance) {
                distance=(int)delta;
                guide=(int)guideCoordinate;
                origin=(int)proposed;
            }
        }
    }
    /** One axis at a time; candidates use the original pointer-dragged origin. */
    private static void nearest(int size,Axis best,
                                long first,long last,long center,
                                long min,long max) {
        // Match like anchors: left to left, right to right, center to center.
        best.consider(first,first,min,max);
        best.consider(last-size,last,min,max);
        best.consider(center-size/2,center,min,max);
    }

    public static Result snap(HudPreviewGeometry.Rect canvas,
                              HudPreviewGeometry.Rect moving,
                              HudPreviewGeometry.Rect[] peers,int tolerance) {
        if(canvas==null||moving==null||peers==null ||
           canvas.width<1||canvas.height<1||moving.width<1||moving.height<1 ||
           tolerance<0||tolerance>24)
            throw new IllegalArgumentException("HUD snap dimensions");
        long xMin=canvas.x,yMin=canvas.y,
            xMax=xMin+canvas.width-moving.width,
            yMax=yMin+canvas.height-moving.height;
        if(xMax<xMin||yMax<yMin)
            throw new IllegalArgumentException("HUD snap moving widget exceeds canvas");
        if(moving.x<xMin||moving.y<yMin||moving.x>xMax||moving.y>yMax)
            throw new IllegalArgumentException("Moving widget is outside preview");

        Axis horiz=new Axis(moving.x,tolerance),vert=new Axis(moving.y,tolerance);
        // Canvas corners, edges, center. All positions remain inside the canvas.
        nearest(moving.width,horiz,xMin,xMin+canvas.width,
            xMin+canvas.width/2,xMin,xMax);
        nearest(moving.height,vert,yMin,yMin+canvas.height,
            yMin+canvas.height/2,yMin,yMax);
        for(HudPreviewGeometry.Rect other:peers) {
            if(other==null)throw new IllegalArgumentException("Null HUD peer");
            if(other.width<1||other.height<1)
                throw new IllegalArgumentException("Invalid HUD peer dimensions");
            long ox=other.x,oy=other.y;
            nearest(moving.width,horiz,ox,ox+other.width,
                ox+other.width/2,xMin,xMax);
            nearest(moving.height,vert,oy,oy+other.height,
                oy+other.height/2,yMin,yMax);
            // Adjacent widgets: line up an edge without visually overlapping.
            horiz.consider(ox-moving.width,ox,xMin,xMax);
            horiz.consider(ox+other.width,ox+other.width,xMin,xMax);
            vert.consider(oy-moving.height,oy,yMin,yMax);
            vert.consider(oy+other.height,oy+other.height,yMin,yMax);
        }
        return new Result(horiz.origin,vert.origin,horiz.guide,vert.guide);
    }
}
