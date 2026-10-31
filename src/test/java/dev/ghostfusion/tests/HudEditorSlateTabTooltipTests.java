package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudEditorSlateTabTooltip;
import dev.ghostfusion.ui.HudEditorSlateTabTypography;
import dev.ghostfusion.ui.HudTextFit;
import java.util.Random;
import java.util.function.ToIntFunction;

/** 50k varied pixel widths, actual tab bounds and overflow decisions. */
public final class HudEditorSlateTabTooltipTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    private static void invalid(Runnable test,String why) {
        boolean threw=false;
        try{test.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,why);
    }
    public static void main(String[] args) {
        Random random=new Random(31102026L);
        int revealed=0,suppressed=0;
        for(int i=0;i<50000;i++) {
            int left=random.nextInt(2000)-900;
            int top=random.nextInt(1400)-600;
            int total=40+random.nextInt(930);
            int fontPixels=3+random.nextInt(14);
            ToIntFunction<String> font=s->s.length()*fontPixels;
            HudEditorControls.Item item=HudEditorControls.Item.values()[
                i%HudEditorControls.Item.values().length];
            int x=HudEditorControls.tabLeft(left,total,item);
            int w=HudEditorControls.tabWidth(total,item);
            int mouseX=x+random.nextInt(w),mouseY=top+random.nextInt(17);
            String found=HudEditorSlateTabTooltip.hovered(left,top,total,
                mouseX,mouseY,font);
            int budget=HudEditorSlateTabTypography.labelBudget(w);
            boolean clipped=!HudTextFit.fit(item.label,budget,font)
                .equals(item.label);
            ok(found==null?!clipped:item.label.equals(found),
                "reveal only the actually clipped registered tab name");
            if(found==null)suppressed++;else revealed++;
            ok(HudEditorSlateTabTooltip.hovered(left,top,total,
                mouseX,top-1,font)==null,
                "hover one pixel before tabs is never admitted");
            ok(HudEditorSlateTabTooltip.hovered(left,top,total,
                mouseX,top+17,font)==null,
                "hover one pixel below tabs is never admitted");
            ok(HudEditorSlateTabTooltip.hovered(left,top,total,
                left-1,top,font)==null,
                "hover outside tab strip never admitted");
            ok(HudEditorSlateTabTooltip.hovered(left,top,total,
                left+total,top,font)==null,
                "right edge is excluded by actual click bounds");
        }
        ok(revealed>1000&&suppressed>1000,
           "both cramped and fully readable tab cases exercised");
        ToIntFunction<String> six=s->s.length()*6;
        ok(HudEditorSlateTabTooltip.hovered(0,0,40,0,0,six)
            .equals("Status"),"very narrow tabs reveal full first label");
        ok(HudEditorSlateTabTooltip.hovered(0,0,700,20,3,six)==null,
            "readable first tab does not generate nuisance tooltip");
        invalid(()->HudEditorSlateTabTooltip.hovered(0,0,90,1,1,null),
            "null native font rejected");
        invalid(()->HudEditorSlateTabTooltip.hovered(0,0,-1,1,1,six),
            "negative tab width rejected");
        invalid(()->HudEditorSlateTabTooltip.hovered(0,0,40,0,0,s->-1),
            "negative font measurement rejected");
        System.out.println("HUD_EDITOR_SLATE_TAB_TOOLTIP_PASS checks="+checks);
    }
}
