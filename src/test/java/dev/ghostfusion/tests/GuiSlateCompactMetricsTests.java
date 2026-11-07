package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateCompactMetrics;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import java.util.Random;

/** Check fallback row/slider/choice spacing across real scaled viewports. */
public final class GuiSlateCompactMetricsTests {
    private static long checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void verify(GuiLayout g,UiState ui) {
        if(!GuiChrome.interactive(g) || GuiSlateWorkspace.of(g,ui)!=null)return;
        int left=g.x+g.sidebar+15,right=g.x+g.width-17;
        int module=GuiSlateCompactMetrics.moduleLabelBudget(g);
        ok(module>=0 && left+module+8<=g.x+g.width-54,
            "module name never overlaps native toggle column");
        for(int kind=0;kind<3;kind++) {
            int budget=GuiSlateCompactMetrics.labelBudget(g,kind);
            int control=kind==0?right-31:
                kind==1?right-GuiSlateCompactMetrics.barWidth(g)-39:
                    GuiSlateCompactMetrics.choiceX(g);
            ok(left+budget+8<=control,
               "setting label keeps real control gutter "+kind);
            ok(budget>=0,"setting label budget nonnegative");
        }
        int choiceStart=GuiSlateCompactMetrics.choiceX(g);
        int choiceBudget=GuiSlateCompactMetrics.choiceBudget(g);
        int arrow=GuiSlateCompactMetrics.choiceArrowX(g);
        ok(choiceStart+choiceBudget+4==arrow &&
           arrow+5<=right,"choice arrow and fitted text disjoint");
        ok(GuiSlateCompactMetrics.barWidth(g)<=g.width-g.sidebar,
            "slider fits fallback content column");
    }
    public static void main(String[] args) {
        UiState ui=new UiState();ui.theme(UiState.Theme.SLATE);
        ui.layout(UiState.Layout.WIDE);
        // Real game UI at 1920x1080 Auto scale is roughly 480x270.
        GuiLayout actual=GuiLayout.calculate(480,270,ui.layout(),ui.theme());
        ok(GuiChrome.interactive(actual)&&GuiSlateWorkspace.of(actual,ui)==null,
            "1080p Auto draws compact interactive Slate fallback");
        verify(actual,ui);
        boolean refused=false;
        try{GuiSlateCompactMetrics.labelBudget(actual,3);}
        catch(IllegalArgumentException expected){refused=true;}
        ok(refused,"unknown control kind rejected");
        Random rng=new Random(28102026L);
        for(int i=0;i<50000;i++) {
            int w=1+rng.nextInt(1800),h=1+rng.nextInt(1200);
            GuiLayout g=GuiLayout.calculate(w,h,ui.layout(),ui.theme());
            verify(g,ui);
        }
        System.out.println("GUI_SLATE_COMPACT_METRICS_PASS checks="+checks);
    }
}
