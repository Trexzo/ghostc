package dev.ghostfusion.tests;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiNebulaWorkspace;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

public final class GuiSlateLayoutTests {
    private static int checks;
    private static void ok(boolean valid,String reason){
        checks++;if(!valid)throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        UiState ui=new UiState();
        ok(ui.theme()==UiState.Theme.NEBULA,"Slate first-run theme");
        GuiLayout original=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        GuiLayout slate=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE,ui.theme());
        ok(original.width==600&&original.height==430,
            "legacy dimensions remain stable");
        ok(slate.width==740&&slate.height==480,
            "Slate uses a true expanded 740x480 workspace");
        ok(slate.insidePanel(1920,1080),"expanded geometry contained");
        ok(GuiNebulaWorkspace.of(slate,ui)!=null,
            "expanded workspace gets independent settings column");
        int oldDetails=GuiNebulaWorkspace.of(
            GuiLayout.calculate(1920,1080,UiState.Layout.WIDE),ui).detailsWidth;
        ok(GuiNebulaWorkspace.of(slate,ui).detailsWidth>oldDetails+100,
            "configuration column gains meaningful width");
        Random rng=new Random(271026L);
        for(int i=0;i<25000;i++) {
            int sw=1+rng.nextInt(2800),sh=1+rng.nextInt(1500);
            GuiLayout g=GuiLayout.calculate(sw,sh,UiState.Layout.WIDE,ui.theme());
            ok(g.insidePanel(sw,sh),"Slate popup contained "+i);
            GuiNebulaWorkspace pane=GuiNebulaWorkspace.of(g,ui);
            if(pane!=null) {
                ok(pane.settingLabelBudget(0)>0 &&
                   pane.settingLabelBudget(2)>0,"labels fit expanded details "+i);
                ok(pane.detailsX+pane.detailsWidth<=g.x+g.width,
                   "detail column never escapes dialog "+i);
            }
        }
        GuiLayout compact=GuiLayout.calculate(1920,1080,UiState.Layout.COMPACT,ui.theme());
        ok(compact.width==390,"compact Slate keeps reliable old hitboxes");
        GuiLayout indigo=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE,UiState.Theme.INDIGO);
        ok(indigo.width==600&&indigo.height==430,"Indigo retains original geometry");
        System.out.println("GUI_SLATE_LAYOUT_PASS checks="+checks);
    }
}
