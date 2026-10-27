package dev.ghostfusion.tests;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiNebulaWorkspace;
import dev.ghostfusion.ui.UiState;
import java.util.Random;
public final class GuiNebulaWorkspaceTests {
    private static int checks;
    private static void ok(boolean good,String detail) {
        checks++;if(!good)throw new AssertionError(detail);
    }
    public static void main(String[] args){
        UiState ui=new UiState();
        GuiLayout g=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        ok(ui.theme()==UiState.Theme.NEBULA,"Slate first-run theme");
        ui.theme(UiState.Theme.INDIGO);
        ok(GuiNebulaWorkspace.of(g,ui)==null,"Indigo unchanged");
        ui.theme(UiState.Theme.NEBULA);
        GuiNebulaWorkspace p=GuiNebulaWorkspace.of(g,ui);
        ok(p!=null&&p.detailsX>p.modulesX+p.modulesWidth,
           "distinct modules and settings panels");
        ok(p.moduleRows>=8&&p.settingRows>=6,"full height independent scroll");
        ok(p.modulesY>=p.detailsY+24,"module caption cannot touch first row");
        ok(p.modulesY>=p.searchY()+29,"search and first module row are separate");
        ok(p.moduleIndex(p.modulesX,p.modulesY,0,200)==0,"first module");
        ok(p.moduleIndex(p.modulesX,p.modulesY+26,0,200)==-1,"module gap");
        ok(p.moduleIndex(p.modulesX,p.modulesY+29,2,200)==3,"scrolled module");
        ok(p.settingIndex(p.detailsX+9,p.settingY,0,200)==0,"first setting");
        ok(p.settingIndex(p.detailsX+9,p.settingY+27,0,200)==-1,"setting gap");
        ok(p.settingIndex(p.detailsX+9,p.settingY+30,3,200)==4,
           "scrolled setting");
        ok(!p.keybindArea(p.modulesX,p.bindingY),"binding is independent");
        ok(!p.moduleArea(p.detailsX,p.modulesY),"modules do not overlap detail");
        Random rng=new Random(271026L);
        for(int i=0;i<50000;i++){
            GuiLayout zone=GuiLayout.calculate(1+rng.nextInt(2600),
                1+rng.nextInt(1600),UiState.Layout.WIDE);
            GuiNebulaWorkspace pane=GuiNebulaWorkspace.of(zone,ui);
            if(pane==null)continue;
            ok(pane.modulesX>=zone.x&&pane.detailsX+pane.detailsWidth<=zone.x+zone.width,
               "horizontal viewport containment "+i);
            ok(pane.settingY+pane.settingRows*30<=zone.y+zone.height,
               "vertical viewport containment "+i);
            int mx=zone.x+rng.nextInt(zone.width);
            int my=zone.y+rng.nextInt(zone.height);
            ok(!(pane.moduleArea(mx,my)&&pane.settingArea(mx,my)),
               "independent input ownership "+i);
            int module=pane.moduleIndex(mx,my,rng.nextInt(8),12);
            int setting=pane.settingIndex(mx,my,rng.nextInt(8),12);
            ok(module<12&&setting<12,"row indices bounded "+i);
            if(module>=0)ok(pane.moduleArea(mx,my),"module selection drawn "+i);
            if(setting>=0)ok(pane.settingArea(mx,my),"setting selection drawn "+i);
        }
        ui.layout(UiState.Layout.COMPACT);
        ok(GuiNebulaWorkspace.of(GuiLayout.calculate(1920,1080,
            UiState.Layout.COMPACT),ui)==null,"compact fallback");
        System.out.println("GUI_NEBULA_WORKSPACE_PASS checks="+checks);
    }
}
