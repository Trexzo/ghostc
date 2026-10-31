package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateControlGeometry;
import dev.ghostfusion.ui.GuiSlateWorkspace;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** Screen and control containment without Minecraft or per-frame allocation. */
public final class GuiSlateControlGeometryTests {
    private static long checks;
    private static void ok(boolean value,String context) {
        checks++;
        if(!value)throw new AssertionError(context);
    }
    private static void rejects(Runnable action,String context) {
        boolean failed=false;
        try {action.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,context);
    }
    public static void main(String[] args) {
        UiState ui=new UiState();
        ui.layout(UiState.Layout.WIDE);
        ui.theme(UiState.Theme.SLATE);
        GuiSlateWorkspace standard=GuiSlateWorkspace.of(
            GuiLayout.calculate(1920,1080,UiState.Layout.WIDE),ui);
        ok(standard!=null,"wide Slate workspace exists");
        ok(GuiSlateControlGeometry.thumbX(standard,0)==standard.sliderX(),
            "zero slider fraction at left control edge");
        ok(GuiSlateControlGeometry.thumbX(standard,1)+4==
           standard.sliderX()+standard.sliderWidth(),
            "full slider fraction ends at right control edge");
        ok(GuiSlateControlGeometry.choiceTextRight(standard)+14==
           standard.choiceValueX()+standard.choiceValueWidth(),
            "value reserves a 14px affordance gutter");
        ok(GuiSlateControlGeometry.chevronX(standard)>=
           GuiSlateControlGeometry.choiceTextRight(standard),
            "chevron is outside value glyph budget");
        rejects(()->GuiSlateControlGeometry.thumbX(standard,Double.NaN),
            "NaN slider fraction rejected");
        rejects(()->GuiSlateControlGeometry.thumbX(standard,-0.01),
            "negative slider fraction rejected");
        rejects(()->GuiSlateControlGeometry.thumbX(standard,1.01),
            "overshoot slider fraction rejected");
        rejects(()->GuiSlateControlGeometry.choiceBudget(null),
            "null workspace rejected");
        Random rng=new Random(28102026L);
        for(int i=0;i<40000;i++) {
            GuiSlateWorkspace p=GuiSlateWorkspace.of(GuiLayout.calculate(
                1+rng.nextInt(2560),1+rng.nextInt(1700),
                UiState.Layout.WIDE),ui);
            if(p==null)continue;
            int start=p.sliderX(),end=start+p.sliderWidth();
            int last=start-1;
            for(int n=0;n<=20;n++) {
                int thumb=GuiSlateControlGeometry.thumbX(p,n/20.0);
                ok(thumb>=start && thumb+4<=end,
                    "slider thumb contained within actual drag lane "+i);
                ok(thumb>=last,"thumb moves monotonically "+i);
                last=thumb;
            }
            int textRight=GuiSlateControlGeometry.choiceTextRight(p);
            int arrow=GuiSlateControlGeometry.chevronX(p);
            ok(p.choiceValueX()>=p.detailsX+14+
               p.settingLabelBudget(2)+8,
               "choice left edge preserves setting label gap "+i);
            ok(textRight>=p.choiceValueX() &&
               arrow>=textRight &&
               arrow+5<=p.choiceValueX()+p.choiceValueWidth(),
               "chevron never collides with fitted value "+i);
            ok(arrow+5<=p.detailsX+p.detailsWidth-16,
               "choice affordance remains in settings column "+i);
        }
        System.out.println("GUI_SLATE_CONTROL_GEOMETRY_PASS checks="+checks);
    }
}
