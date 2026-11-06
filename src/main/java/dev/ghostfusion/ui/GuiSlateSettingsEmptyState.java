package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

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

    /** All wording is honest and short enough to preserve its meaning. */
    public static final class Copy {
        public final String title,hint;
        public final int textBudget;
        private Copy(String title,String hint,int textBudget) {
            this.title=title;this.hint=hint;this.textBudget=textBudget;
        }
    }

    private static String firstFitting(int budget,ToIntFunction<String> font,
                                       String[] choices) {
        for(String choice:choices) {
            int width=font.applyAsInt(choice);
            if(width<0)throw new IllegalArgumentException("Slate settings font");
            if(width<=budget)return choice;
        }
        return null;
    }

    /**
     * Chooses real, complete captions from the current Minecraft font's
     * measured pixel width. Never paints a clipped half-word or guesses
     * a font's average glyph advance at compact GUI scales.
     *
     * No allocations or font queries happen if the caller's frame is null.
     */
    public static Copy copy(Frame frame,ToIntFunction<String> font) {
        if(frame==null||font==null)
            throw new IllegalArgumentException("Slate settings caption");
        int textBudget=Math.max(0,frame.width-24);
        String title=firstFitting(textBudget,font,new String[]{
            "NO EXTRA SETTINGS","NO SETTINGS","NONE"
        });
        String hint=firstFitting(textBudget,font,new String[]{
            "TOGGLE OR SET A KEY ABOVE","TOGGLE / KEYBIND",
            "TOGGLE / KEY","USE KEY","BIND"
        });
        return title==null||hint==null?null:new Copy(title,hint,textBudget);
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
