package dev.ghostfusion.ui;

/**
 * Pointer capture for Slate's module/settings scroll tracks.
 * Does not mutate modules, typed settings, profiles, or gameplay state.
 * Mouse coordinates, viewport and content counts are validated on each drag.
 */
public final class GuiScrollDrag {
    public enum Lane { MODULES, SETTINGS }
    private Lane lane;
    private int trackY,trackHeight,total,visible,grabY,viewportWidth,viewportHeight;

    /** Inclusive 6px pointer gutter around a 2px painted scrollbar. */
    public static boolean trackHit(int mx,int my,int trackX,int y,int height) {
        return height>0 && (long)mx>=trackX-2L && (long)mx<trackX+4L &&
               (long)my>=y && (long)my<(long)y+height;
    }
    /** -1 means no track was hit; otherwise the selected top row. */
    public int begin(Lane next,int mx,int my,int trackX,int y,int height,
                     int count,int rows,int first,int screenWidth,int screenHeight) {
        if(active())throw new IllegalStateException("scroll drag already active");
        if(next==null||height<0||count<0||rows<0||first<0||
           screenWidth<1||screenHeight<1)
            throw new IllegalArgumentException("scroll drag geometry");
        int thumb=GuiScrollIndicator.height(height,count,rows);
        if(thumb==0||!trackHit(mx,my,trackX,y,height))return -1;
        int top=y+GuiScrollIndicator.offset(height,count,rows,first);
        // Clicking the thumb preserves the exact grip point. Clicking the
        // empty track centers the thumb around the pointer before capture.
        int grab=my>=top && (long)my<(long)top+thumb ? my-top : thumb/2;
        lane=next;trackY=y;trackHeight=height;total=count;visible=rows;
        grabY=grab;viewportWidth=screenWidth;viewportHeight=screenHeight;
        return move(my);
    }
    public boolean active(){return lane!=null;}
    public Lane lane(){if(!active())throw new IllegalStateException("no scroll capture");return lane;}
    public boolean sameLayout(int screenWidth,int screenHeight,
                              int count,int rows,int height) {
        return active() && screenWidth==viewportWidth &&
               screenHeight==viewportHeight && count==total &&
               rows==visible && height==trackHeight;
    }
    public int move(int my){
        if(!active())throw new IllegalStateException("no scroll capture");
        long delta=(long)my-trackY-grabY;
        int bounded=delta<Integer.MIN_VALUE?Integer.MIN_VALUE:
                    delta>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)delta;
        return GuiScrollIndicator.firstAt(trackHeight,total,visible,bounded);
    }
    public void end(){lane=null;}
}
