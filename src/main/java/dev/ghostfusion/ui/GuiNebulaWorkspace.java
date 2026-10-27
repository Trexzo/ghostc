package dev.ghostfusion.ui;

/**
 * Original three-pane Nebula workspace. The same bounds drive painting,
 * selection, keyboard focus, wheel scrolling and slider/keybind hit testing.
 * Indigo/Mint/compact modes retain their original layout.
 */
public final class GuiNebulaWorkspace {
    public final int modulesX,modulesY,modulesWidth,modulesHeight,moduleRows;
    public final int detailsX,detailsY,detailsWidth,detailsHeight;
    public final int bindingY,settingY,settingRows;
    private GuiNebulaWorkspace(GuiLayout g) {
        modulesX=g.x+g.sidebar+11;
        modulesY=g.y+89;
        modulesWidth=176;
        modulesHeight=Math.max(0,g.height-111);
        moduleRows=modulesHeight/29;
        detailsX=modulesX+modulesWidth+11;
        detailsY=g.y+80;
        detailsWidth=g.x+g.width-13-detailsX;
        detailsHeight=g.height-104;
        bindingY=detailsY+51;
        settingY=detailsY+92;
        settingRows=Math.max(0,(g.y+g.height-28-settingY)/30);
    }
    public static GuiNebulaWorkspace of(GuiLayout g,UiState ui) {
        if(g==null||ui==null)throw new IllegalArgumentException("workspace inputs");
        if(ui.theme()!=UiState.Theme.NEBULA ||
           ui.layout()!=UiState.Layout.WIDE ||
           g.width<580||g.height<340||!GuiChrome.interactive(g))
            return null;
        GuiNebulaWorkspace pane=new GuiNebulaWorkspace(g);
        return pane.detailsWidth>=210 && pane.settingRows>=3 ? pane : null;
    }
    private static boolean inside(int mx,int my,int x,int y,int w,int h) {
        return w>0&&h>0&&(long)mx>=x&&(long)my>=y&&
            (long)mx<(long)x+w&&(long)my<(long)y+h;
    }
    public int moduleIndex(int mx,int my,int scroll,int count) {
        if(scroll<0||count<0)throw new IllegalArgumentException("module range");
        if(!inside(mx,my,modulesX,modulesY,modulesWidth,modulesHeight))return -1;
        int offset=my-modulesY, row=offset/29;
        if(row>=moduleRows || offset%29>=26)return -1;
        long index=(long)scroll+row;
        return index<count?(int)index:-1;
    }
    public int settingIndex(int mx,int my,int scroll,int count) {
        if(scroll<0||count<0)throw new IllegalArgumentException("setting range");
        if(!inside(mx,my,detailsX+8,settingY,detailsWidth-16,settingRows*30))
            return -1;
        int offset=my-settingY, row=offset/30;
        if(offset%30>=27)return -1;
        long index=(long)scroll+row;
        return index<count?(int)index:-1;
    }
    public boolean moduleArea(int mx,int my) {
        return inside(mx,my,modulesX,modulesY,modulesWidth,modulesHeight);
    }
    public boolean settingArea(int mx,int my) {
        return inside(mx,my,detailsX+8,settingY,detailsWidth-16,settingRows*30);
    }
    public boolean keybindArea(int mx,int my) {
        return inside(mx,my,detailsX+9,bindingY,detailsWidth-18,24);
    }
    public boolean moduleToggleArea(int mx) {
        return mx>=modulesX+modulesWidth-39&&mx<modulesX+modulesWidth-5;
    }
    public int sliderX(){return detailsX+detailsWidth-87;}
    public int sliderWidth(){return 67;}
}
