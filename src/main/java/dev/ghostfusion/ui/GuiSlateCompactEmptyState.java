package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Noninteractive compact Slate no-results feedback, strictly inside the
 * existing visible module rows. Does not change selected module, focus,
 * scroll state, registry filters, native click/keyboard hitboxes or themes.
 */
public final class GuiSlateCompactEmptyState {
    private GuiSlateCompactEmptyState() { }
    public static final int HEIGHT=56;

    public static final class Frame {
        public final int x,y,width,height,titleX,titleY,hintY;
        private Frame(int x,int y,int width) {
            this.x=x;this.y=y;this.width=width;this.height=HEIGHT;
            titleX=x+12;titleY=y+13;hintY=y+31;
        }
    }
    public static final class Copy {
        public final String title,hint;
        private Copy(String title,String hint) {
            this.title=title;this.hint=hint;
        }
    }

    /**
     * Only full captions fitted using the actual Minecraft font are painted.
     * Search and category empty states are distinct but never imply a
     * disabled feature or promise that a matching module exists.
     */
    public static Copy copy(Frame frame,boolean searching,
                            ToIntFunction<String> fontWidth) {
        if(frame==null||fontWidth==null)
            throw new IllegalArgumentException("Slate compact empty caption");
        int budget=frame.width-24;
        String title=firstFitting(budget,fontWidth,
            new String[]{"NO RESULTS","NONE"});
        String hint=firstFitting(budget,fontWidth,searching
            ?new String[]{"CHANGE SEARCH","TRY SEARCH","SEARCH"}
            :new String[]{"CHANGE CATEGORY","TRY CATEGORY","FILTER"});
        return title==null||hint==null?null:new Copy(title,hint);
    }
    private static String firstFitting(int budget,ToIntFunction<String> font,
                                       String[] candidates) {
        for(String candidate:candidates) {
            int width=font.applyAsInt(candidate);
            if(width<0)throw new IllegalArgumentException("Negative native font width");
            if(width<=budget)return candidate;
        }
        return null;
    }

    public static Frame of(GuiLayout g) {
        if(g==null)throw new IllegalArgumentException("Slate compact geometry");
        if(!GuiChrome.interactive(g))return null;
        int listHeight=g.visibleRows*28;
        int areaWidth=g.width-g.sidebar-22;
        int width=Math.min(156,areaWidth-12);
        if(listHeight<HEIGHT||width<100)return null;
        return new Frame(g.x+g.sidebar+11+(areaWidth-width)/2,
            g.listY+(listHeight-HEIGHT)/2,width);
    }
}
