package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorSlateTabTypography;
import dev.ghostfusion.ui.HudEditorControls;
import java.util.Random;

/** Text and indicator containment across every HUD tab click region. */
public final class HudEditorSlateTabTypographyTests {
    private static long checks;
    private static void ok(boolean b,String msg) {
        checks++;if(!b)throw new AssertionError(msg);
    }
    private static void rejects(Runnable fn,String msg) {
        boolean failed=false;
        try{fn.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,msg);
    }
    public static void main(String[] args) {
        Random rng=new Random(29102026L);
        for(int i=0;i<50000;i++) {
            int total=40+rng.nextInt(2600);
            int parentX=rng.nextInt(5000)-2000;
            for(HudEditorControls.Item item:HudEditorControls.Item.values()) {
                int x=HudEditorControls.tabLeft(parentX,total,item);
                int w=HudEditorControls.tabWidth(total,item);
                int budget=HudEditorSlateTabTypography.labelBudget(w);
                int ink=budget>0?rng.nextInt(budget+1):0;
                int aligned=HudEditorSlateTabTypography.labelX(x,w,ink);
                ok(aligned>=x&&aligned+ink<=x+w,
                   "measured text fully within actionable tab");
                ok(Math.abs((aligned-x)-(x+w-(aligned+ink)))<=1,
                   "font glyphs pixel-centered");
                int inset=HudEditorSlateTabTypography.underlineInset(w);
                int underline=HudEditorSlateTabTypography.underlineWidth(w);
                ok(inset>=0&&inset+underline<=w,
                   "active indicator inside real click region");
                ok(underline>=1,"visible narrow active indicator");
            }
        }
        rejects(()->HudEditorSlateTabTypography.labelBudget(0),"zero tab rejected");
        rejects(()->HudEditorSlateTabTypography.labelX(0,20,21),
                "oversized text rejected");
        rejects(()->HudEditorSlateTabTypography.labelX(0,20,-1),
                "negative font width rejected");
        System.out.println("HUD_EDITOR_SLATE_TAB_TYPOGRAPHY_PASS checks="+checks);
    }
}
