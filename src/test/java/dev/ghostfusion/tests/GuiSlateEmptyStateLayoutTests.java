package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateEmptyStateLayout;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** 40k varied layout probes: empty state is inside actionable visible rows. */
public final class GuiSlateEmptyStateLayoutTests {
    private static long count;
    private static void ok(boolean b,String m){count++;if(!b)throw new AssertionError(m);}
    public static void main(String[] args) {
        UiState ui=new UiState();
        ui.theme(UiState.Theme.SLATE);ui.layout(UiState.Layout.WIDE);
        boolean rejected=false;
        try{GuiSlateEmptyStateLayout.of(null);}
        catch(IllegalArgumentException good){rejected=true;}
        ok(rejected,"null pane rejected");
        Random rng=new Random(28102026L);
        int admitted=0;
        for(int i=0;i<40000;i++){
            int sw=1+rng.nextInt(3100),sh=1+rng.nextInt(2000);
            GuiLayout g=GuiLayout.calculate(sw,sh,ui.layout(),ui.theme());
            GuiSlateWorkspace pane=GuiSlateWorkspace.of(g,ui);
            if(pane==null)continue;
            admitted++;
            GuiSlateEmptyStateLayout.Frame f=GuiSlateEmptyStateLayout.of(pane);
            ok(f!=null,"wide Slate shows readable empty state");
            ok(f.x>=pane.modulesX &&
               f.x+f.width<=pane.modulesX+pane.modulesWidth,
               "empty card horizontally inside module list");
            ok(f.y>=pane.modulesY &&
               f.y+f.height<=pane.modulesY+pane.moduleRows*29,
               "empty card stays inside painted module row region");
            ok(f.titleX>=f.x+12&&f.titleX<f.x+f.width,
               "title follows consistent padding");
            ok(f.titleY>=f.y+10&&f.hintY+9<=f.y+f.height,
               "both text baselines fully inside card");
            ok(f.width<=156&&f.height==56,"bounded quiet card");
        }
        ok(admitted>5000,"meaningful array of three-pane viewports");
        System.out.println("GUI_SLATE_EMPTY_STATE_PASS checks="+count);
    }
}
