package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiSlateControlGeometry;
import dev.ghostfusion.ui.GuiSlateSearchGeometry;
import dev.ghostfusion.ui.GuiSlateTooltipLayout;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/**
 * R5 Slate visual readiness against the *actual* theme-aware GuiLayout path.
 * Simulated scaled Minecraft viewport geometry, NOT original-game screenshots.
 */
public final class GuiSlateScaleMatrixTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    /** Minecraft 1.8.9 ScaledResolution's minimum 320x240 physical window. */
    private static int scaleFactor(int physicalW,int physicalH,int requested) {
        int requestedScale=requested==0?1000:requested;
        int factor=1;
        while(factor<requestedScale && physicalW/(factor+1)>=320 &&
              physicalH/(factor+1)>=240)factor++;
        return factor;
    }
    private static void verify(int physicalW,int physicalH,int requested) {
        int factor=scaleFactor(physicalW,physicalH,requested);
        int sw=(physicalW+factor-1)/factor,sh=(physicalH+factor-1)/factor;
        UiState ui=new UiState();
        ui.theme(UiState.Theme.SLATE);
        ui.layout(UiState.Layout.WIDE);
        GuiLayout g=GuiLayout.calculate(sw,sh,ui.layout(),ui.theme());
        ok(g.insidePanel(sw,sh),"actual Slate theme layout fits "+sw+"x"+sh);
        boolean active=GuiChrome.interactive(g);
        GuiSlateWorkspace p=GuiSlateWorkspace.of(g,ui);
        if(p==null) {
            if(!active) {
                ok(GuiChrome.headerAt(g,g.x+g.sidebar+10,g.y+12)==
                   GuiChrome.Header.NONE,"undersized viewport safely inert");
                return;
            }
            ok(GuiHitZones.visibleSettings(g)>=0,
               "fallback never exposes negative visible settings");
            int lastModuleY=g.listY+g.visibleRows*28;
            ok(lastModuleY<=g.y+g.height,
               "fallback list remains inside panel");
            ok(GuiHitZones.settingStartY(g)<=g.y+g.height ||
               GuiHitZones.visibleSettings(g)==0,
               "fallback setting admission bounded at bottom");
            return;
        }
        ok(active && g.width>=580&&g.height>=340,
           "three-pane only for viable Slate real viewports");
        ok(p.moduleRows>=7&&p.settingRows>=3,
           "both native scroll lists have viable visible rows");
        ok(p.detailsX>p.modulesX+p.modulesWidth,
           "module/settings gutter remains separate");
        ok(p.detailsX+p.detailsWidth<=g.x+g.width-8,
           "settings pane contained horizontally");
        ok(p.settingY+p.settingRows*30<=g.y+g.height-28,
           "settings reserved footer does not overlap");
        ok(p.searchY()+GuiSlateSearchGeometry.SURFACE_HEIGHT<p.modulesY,
           "native search never collides with first module");
        ok(!p.moduleArea(p.detailsX,p.modulesY),
           "module and settings areas never share pointer input");
        ok(GuiSlateSearchGeometry.fieldX(p)+
           GuiSlateSearchGeometry.fieldWidth(p)<
           GuiSlateSearchGeometry.clearX(p),
           "search caret and clear button disjoint");
        int thumb=GuiSlateControlGeometry.thumbX(p,1);
        ok(thumb+4==p.sliderX()+p.sliderWidth(),
           "slider handle contained at 100% for real Slate width");
        ok(GuiSlateControlGeometry.chevronX(p)+5<=
           p.detailsX+p.detailsWidth-16,
           "choice arrow stays inside real settings column");
        GuiSlateTooltipLayout.Frame tooltip=GuiSlateTooltipLayout.place(
            g.x+g.sidebar+8,g.y+30,g.width-g.sidebar-16,
            g.height-30,p.settingY,p.settingY+26,250);
        ok(tooltip!=null &&
           tooltip.y+tooltip.height<=g.y+g.height-20,
           "long-label hover respects real Slate footer");
        int first=p.moduleIndex(p.modulesX,p.modulesY,0,10);
        ok(first==0 && p.moduleIndex(p.modulesX,p.modulesY+26,0,10)==-1,
           "draw and native click hitbox agree at first-row separator");
        ok(p.settingIndex(p.detailsX+9,p.settingY,0,10)==0 &&
           p.settingIndex(p.detailsX+9,p.settingY+27,0,10)==-1,
           "setting row separator is not clickable");
    }
    public static void main(String[] args) {
        int[][] displays={{854,480},{1280,720},{1366,768},{1920,1080},
                          {2560,1440},{3440,1440},{3840,2160},
                          {800,600},{1024,768},{1600,900},{640,480}};
        for(int[] d:displays)
            for(int scale=0;scale<=4;scale++)verify(d[0],d[1],scale);
        ok(scaleFactor(1920,1080,0)==4,
           "1080p Auto resolves to physical scale factor four");
        ok(scaleFactor(1280,720,0)==3,
           "720p Auto resolves to physical scale factor three");
        ok(scaleFactor(854,480,4)==2,
           "480p cannot scale smaller than 320x240 physical");
        Random rng=new Random(28102026L);
        for(int i=0;i<25000;i++) {
            int w=320+rng.nextInt(3600),h=240+rng.nextInt(1900);
            verify(w,h,rng.nextInt(6));
        }
        System.out.println("GUI_SLATE_SCALE_MATRIX_PASS checks="+checks);
    }
}
