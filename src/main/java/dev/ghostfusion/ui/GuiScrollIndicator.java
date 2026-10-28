package dev.ghostfusion.ui;

/**
 * Pure-Java scrollbar geometry shared by Slate rendering and mouse input.
 * The indicator itself never owns selection or changes user settings.
 */
public final class GuiScrollIndicator {
    private GuiScrollIndicator(){}
    public static int height(int trackHeight,int total,int visible) {
        if(trackHeight<0||total<0||visible<0)
            throw new IllegalArgumentException("Scroll dimension");
        if(trackHeight==0||total<=visible||visible==0)return 0;
        long proportional=(long)trackHeight*visible/Math.max(1,total);
        return Math.min(trackHeight,Math.max(Math.min(12,trackHeight),(int)proportional));
    }
    public static int offset(int trackHeight,int total,int visible,int first) {
        if(trackHeight<0||total<0||visible<0||first<0)
            throw new IllegalArgumentException("Scroll offset");
        int thumb=height(trackHeight,total,visible);
        if(thumb==0)return 0;
        int range=Math.max(1,total-visible);
        int clamped=Math.max(0,Math.min(range,first));
        return (int)((long)clamped*(trackHeight-thumb)/range);
    }
    /** Inverse of offset(): drag a thumb top to its nearest valid list row. */
    public static int firstAt(int trackHeight,int total,int visible,int thumbTop) {
        int thumb=height(trackHeight,total,visible);
        if(thumb==0)return 0;
        int travel=trackHeight-thumb;
        int last=Math.max(0,total-visible);
        if(travel<=0||last==0)return 0;
        long clamped=Math.min((long)travel,Math.max(0L,(long)thumbTop));
        return (int)((clamped*last+travel/2L)/travel);
    }
}
