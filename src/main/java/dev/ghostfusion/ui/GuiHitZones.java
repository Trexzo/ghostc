package dev.ghostfusion.ui;

/**
 * Shared ClickGUI hit detection for the *drawn* module/setting rows.
 * Unlike raw panel hit checks, no input may select a hidden scrolled row,
 * a 3px separator, or a control beyond the viewport.
 */
public final class GuiHitZones {
    private GuiHitZones() { }
    private static boolean hit(int px,int py,int x,int y,int width,int height) {
        return width>0 && height>0 && (long)px>=x && (long)py>=y &&
            (long)px<(long)x+width && (long)py<(long)y+height;
    }
    public static int moduleIndex(GuiLayout g,int px,int py,int scroll,int count) {
        if(g==null || scroll<0 || count<0) throw new IllegalArgumentException("module range");
        int x=g.x+g.sidebar+9, width=g.width-g.sidebar-20;
        if(!hit(px,py,x,g.listY,width,g.listHeight) || g.visibleRows==0) return -1;
        int offset=py-g.listY, row=offset/28;
        if(row>=g.visibleRows || offset%28>=25) return -1;
        long result=(long)scroll+row;
        return result<count ? (int)result : -1;
    }
    public static int settingStartY(GuiLayout g) {
        if(g==null) throw new IllegalArgumentException("layout");
        return g.listY+g.listHeight+5+22+24;
    }
    public static int visibleSettings(GuiLayout g) {
        int top=settingStartY(g);
        return Math.max(0,(g.y+g.height-18-top)/22);
    }
    public static int settingIndex(GuiLayout g,int px,int py,int scroll,int count) {
        if(g==null || scroll<0 || count<0) throw new IllegalArgumentException("setting range");
        int top=settingStartY(g),available=visibleSettings(g);
        int width=g.width-g.sidebar-26;
        if(!hit(px,py,g.x+g.sidebar+11,top,width,available*22)) return -1;
        int row=(py-top)/22;
        if((py-top)%22>=19) return -1;
        long result=(long)scroll+row;
        return result<count ? (int)result : -1;
    }
    public static boolean settingsArea(GuiLayout g,int px,int py) {
        if(g==null) throw new IllegalArgumentException("layout");
        return hit(px,py,g.x+g.sidebar+9,settingStartY(g),
            g.width-g.sidebar-20,Math.max(0,g.y+g.height-18-settingStartY(g)));
    }
    public static boolean moduleArea(GuiLayout g,int px,int py) {
        if(g==null) throw new IllegalArgumentException("layout");
        return hit(px,py,g.x+g.sidebar+9,g.listY,
            g.width-g.sidebar-20,g.listHeight);
    }
}
