package dev.ghostfusion.ui;

/**
 * Shared admission/coordinates for complete HUD widgets in scaled pixels.
 * Prevents 2D GUI drawing outside a viewport smaller than the actual
 * Keystrokes or Target footprint; layout and saved anchors stay unchanged.
 */
public final class HudWidgetViewport {
    private HudWidgetViewport() {}

    public static final class Frame {
        public final int x,y,width,height;
        private Frame(int x,int y,int width,int height) {
            this.x=x;this.y=y;this.width=width;this.height=height;
        }
        public boolean contained(int sw,int sh) {
            return sw>0&&sh>0&&x>=0&&y>=0&&width>0&&height>0&&
                (long)x+width<=sw&&(long)y+height<=sh;
        }
    }

    /**
     * Null means the *whole* widget cannot fit: no partial gl leakage.
     * R1.9 normalized anchors and R2.1 dynamic widget heights are authoritative.
     */
    public static Frame place(HudPositions anchors,HudPositions.Widget widget,
                              int screenWidth,int screenHeight,
                              boolean jump,boolean sneak,boolean targetHealth) {
        if(anchors==null||widget==null)
            throw new IllegalArgumentException("HUD anchors/widget");
        int width=HudFootprints.width(widget);
        int height=HudFootprints.height(widget,jump,sneak,targetHealth);
        if(screenWidth<width||screenHeight<height)return null;
        int x=anchors.pixelX(widget,screenWidth,width);
        int y=anchors.pixelY(widget,screenHeight,height);
        Frame frame=new Frame(x,y,width,height);
        if(!frame.contained(screenWidth,screenHeight))
            throw new IllegalStateException("HUD anchor escaped viewport");
        return frame;
    }
}
