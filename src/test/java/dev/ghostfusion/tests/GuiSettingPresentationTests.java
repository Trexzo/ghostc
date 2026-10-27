package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSettingPresentation;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

public final class GuiSettingPresentationTests {
    private static int checks;
    private static void ok(boolean good,String desc){
        checks++;if(!good)throw new AssertionError(desc);
    }
    private static void invalid(Runnable code,String msg){
        boolean threw=false;
        try{code.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,msg);
    }
    public static void main(String[] args) {
        ok(GuiSettingPresentation.label("visible_only").equals("Visible Only"),
           "human-readable switch caption");
        ok(GuiSettingPresentation.label("max_fps").equals("Max FPS"),
           "acronym title preservation");
        ok(GuiSettingPresentation.label("min_cps").equals("Min CPS"),
           "CPS title preservation");
        ok(GuiSettingPresentation.choice("bottom_right").equals("Bottom Right"),
           "choice text no longer uses snake case");
        ok(GuiSettingPresentation.choice("slate").equals("Slate"),
           "all persisted Slate appearances display as Slate");
        ok(GuiSettingPresentation.number("scale",1.0).equals("1x"),
           "scale suffix");
        ok(GuiSettingPresentation.number("cps",10).equals("10 CPS"),
           "actual CPS unit");
        ok(GuiSettingPresentation.number("range",4.5).equals("4.5 blocks"),
           "range unit");
        ok(GuiSettingPresentation.number("interval_ticks",4).equals("4 ticks"),
           "tick unit");
        ok(GuiSettingPresentation.number("opacity",0.82).equals("82%"),
           "alpha percentage display");
        ok(GuiSettingPresentation.number("red",0.62).equals("0.62"),
           "unknown value receives no invented unit");
        invalid(()->GuiSettingPresentation.label("bad__id"),
            "invalid separators rejected");
        invalid(()->GuiSettingPresentation.number("scale",Double.NaN),
            "non-finite numeric display rejected");
        Random rng=new Random(271026L);
        UiState ui=new UiState();ui.theme(UiState.Theme.SLATE);
        for(int i=0;i<30000;i++) {
            int sw=1+rng.nextInt(2700),sh=1+rng.nextInt(1500);
            GuiSlateWorkspace pane=GuiSlateWorkspace.of(
                GuiLayout.calculate(sw,sh,UiState.Layout.WIDE),ui);
            if(pane==null)continue;
            ok(pane.settingLabelBudget(0)>0&&
               pane.settingLabelBudget(1)>0&&pane.settingLabelBudget(2)>0,
               "all settings retain positive label width "+i);
            ok(pane.choiceValueWidth()>0 &&
               pane.choiceValueX()+pane.choiceValueWidth()<=
                    pane.detailsX+pane.detailsWidth-16,
               "choice value remains within right gutter "+i);
            ok(pane.detailsX+14+pane.settingLabelBudget(2)+8<=
               pane.choiceValueX(),"choice labels cannot overlap values "+i);
            ok(pane.detailsX+14+pane.settingLabelBudget(1)+8<=pane.sliderX(),
               "numeric captions cannot overlap sliders "+i);
            ok(pane.searchX()==pane.modulesX&&pane.searchWidth()==pane.modulesWidth,
               "search is confined to module navigation "+i);
        }
        System.out.println("GUI_SETTING_PRESENTATION_PASS checks="+checks);
    }
}
