package dev.ghostfusion.ui;

/**
 * Strictly bounded, allocation-light HUD scale geometry shared by native
 * rendering and the editor. Scale 1.00 keeps all legacy pixels unchanged.
 * Real footprints use ceil so a scaled OpenGL primitive cannot escape screen.
 */
public final class HudScaleLayout {
    public static final double MIN=0.75,MAX=1.75;
    private HudScaleLayout(){}
    public static void validate(double scale) {
        if(!Double.isFinite(scale)||scale<MIN||scale>MAX)
            throw new IllegalArgumentException("HUD scale outside supported range");
    }
    public static int extent(int logical,double scale) {
        validate(scale);
        if(logical<1)throw new IllegalArgumentException("HUD logical footprint");
        long result=(long)Math.ceil(logical*scale-1.0e-10);
        if(result<1||result>Integer.MAX_VALUE)
            throw new IllegalArgumentException("HUD scaled extent overflow");
        return (int)result;
    }
    /** Effective unscaled viewport used for row/text layout inside glScale. */
    public static int logicalViewport(int viewport,double scale) {
        validate(scale);
        if(viewport<1)throw new IllegalArgumentException("HUD viewport");
        return Math.max(1,(int)Math.floor(viewport/scale));
    }
    public static final class Frame {
        public final int x,y,width,height,logicalWidth,logicalHeight;
        public final double scale;
        private Frame(int x,int y,int width,int height,int lw,int lh,double scale) {
            this.x=x;this.y=y;this.width=width;this.height=height;
            logicalWidth=lw;logicalHeight=lh;this.scale=scale;
        }
        public boolean contained(int vw,int vh){
            return x>=0&&y>=0&&width>0&&height>0&&
                (long)x+width<=vw&&(long)y+height<=vh;
        }
    }
    /** Null means the complete scaled widget does not fit; no partial painting. */
    public static Frame place(HudPositions positions,HudPositions.Widget widget,
                              int viewportW,int viewportH,
                              int logicalW,int logicalH,double scale) {
        if(positions==null||widget==null)throw new IllegalArgumentException("HUD anchor");
        validate(scale);
        if(viewportW<1||viewportH<1)return null;
        int w=extent(logicalW,scale),h=extent(logicalH,scale);
        if(w>viewportW||h>viewportH)return null;
        int x=(int)Math.round(positions.x(widget)*(viewportW-w));
        int y=(int)Math.round(positions.y(widget)*(viewportH-h));
        Frame result=new Frame(x,y,w,h,logicalW,logicalH,scale);
        if(!result.contained(viewportW,viewportH))
            throw new IllegalStateException("HUD scaled placement escaped viewport");
        return result;
    }
}
