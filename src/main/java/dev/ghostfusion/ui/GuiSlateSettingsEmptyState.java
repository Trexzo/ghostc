package dev.ghostfusion.ui;

/**
 * Slate-only measured settings empty state. Visual content is shown only when
 * a real selected registry module has zero user-configurable settings.
 *
 * No fake toggles or clickable cards: this rectangle is purely paint geometry.
 * The existing keybind/action area, footer and native setting hitboxes own
 * their unchanged coordinates.
 */
public final class GuiSlateSettingsEmptyState {
    private GuiSlateSettingsEmptyState() { }

    public static final int MIN_WIDTH=110;
    public static final int MIN_HEIGHT=47;
    public static final int MAX_HEIGHT=72;

    public static final class Frame {
        public final int x,y,width,height;
        public final int titleX,titleY,hintY,accentX,separatorY;
        private Frame(int x,int y,int w,int h) {
            this.x=x;this.y=y;this.width=w;this.height=h;
            titleX=x+12;titleY=y+12;hintY=y+32;
            accentX=x+1;separatorY=y+26;
        }
    }

    /**
     * @param availableWidth exact inner settings-paint width
     * @param availableHeight exact settings-pane space excluding the footer
     * @return null when the panel is too small for a legible empty message
     */
    public static Frame place(int left,int top,int availableWidth,int availableHeight) {
        if(availableWidth<0||availableHeight<0)
            throw new IllegalArgumentException("Slate settings empty dimensions");
        if(availableWidth<MIN_WIDTH||availableHeight<MIN_HEIGHT)return null;
        int height=Math.min(MAX_HEIGHT,availableHeight);
        return new Frame(left,top,availableWidth,height);
    }
}
