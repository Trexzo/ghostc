package dev.ghostfusion.ui;

/**
 * Read-only overflow rail for compact Slate settings. No pointer capture,
 * settings mutation or additional hit zones: use the exact settings row
 * viewport already painted and operated on by GuiHitZones.
 */
public final class GuiSlateCompactSettingsScroll {
    public static final class Frame {
        public final int x,y,width,height,thumbY,thumbHeight;
        private Frame(int x,int y,int height,int thumbY,int thumbHeight) {
            this.x=x;this.y=y;this.width=2;this.height=height;
            this.thumbY=thumbY;this.thumbHeight=thumbHeight;
        }
    }
    private GuiSlateCompactSettingsScroll() { }

    /** Absent when settings do not overflow or native rows cannot be drawn. */
    public static Frame of(GuiLayout layout,int total,int first) {
        if(layout==null||total<0||first<0)
            throw new IllegalArgumentException("Slate compact settings scroll");
        if(!GuiChrome.interactive(layout))return null;
        int rows=GuiHitZones.visibleSettings(layout);
        if(rows<1)return null;
        int height=rows*22; // exact rendered row pitch; excludes footer
        int thumb=GuiScrollIndicator.height(height,total,rows);
        if(thumb==0)return null;
        int clamped=Math.min(first,Math.max(0,total-rows));
        int top=GuiHitZones.settingStartY(layout);
        int offset=GuiScrollIndicator.offset(height,total,rows,clamped);
        // The native settings click region ends at x+width-15 and the
        // scroll wheel zone ends at x+width-11: this is in the outer margin.
        return new Frame(layout.x+layout.width-8,top,height,top+offset,thumb);
    }
}
