package dev.ghostfusion.ui;

/**
 * Noninteractive wheel-scroll indicator for the Slate fallback module list.
 *
 * Compact Minecraft GUI scales can show only two module rows. A quiet thumb
 * tells users that filtered results continue below the fold. The rail lives
 * beyond GuiHitZones.moduleArea and never changes module/toggle hitboxes.
 */
public final class GuiSlateCompactModuleScroll {
    public static final class Frame {
        public final int x,y,width,height,thumbY,thumbHeight;
        private Frame(int x,int y,int height,int thumbY,int thumbHeight) {
            this.x=x;this.y=y;this.width=2;this.height=height;
            this.thumbY=thumbY;this.thumbHeight=thumbHeight;
        }
    }
    private GuiSlateCompactModuleScroll() { }

    /** Return null if there is no overflow or the viewport is not interactive. */
    public static Frame of(GuiLayout layout,int total,int first) {
        if(layout==null||total<0||first<0)
            throw new IllegalArgumentException("Slate compact scroll counts");
        if(!GuiChrome.interactive(layout)||layout.visibleRows<1)return null;
        int visible=layout.visibleRows;
        int height=visible*28; // only rows actually painted and hit-tested
        int thumb=GuiScrollIndicator.height(height,total,visible);
        if(thumb==0)return null;
        int scroll=Math.max(0,Math.min(first,Math.max(0,total-visible)));
        int y=layout.listY;
        int x=layout.x+layout.width-8;
        int offset=GuiScrollIndicator.offset(height,total,visible,scroll);
        return new Frame(x,y,height,y+offset,thumb);
    }
}
