package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudSlateReadoutGeometry;
import dev.ghostfusion.features.TargetHudModernLayout;
import java.util.Random;

/** Bound native Minecraft font columns at all health-card viewport sizes. */
public final class TargetHudSlateReadoutGeometryTests {
    private static long count;
    private static void ok(boolean v,String note) {
        count++;if(!v)throw new AssertionError(note);
    }
    private static void invalid(Runnable runnable,String note) {
        boolean rejected=false;
        try{runnable.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,note);
    }
    public static void main(String[] args) {
        final int width=160,titleWidth=30;
        ok(TargetHudSlateReadoutGeometry.valueBudget(width,titleWidth)==104,
           "160px card reserves range label and 8px gap");
        ok(TargetHudSlateReadoutGeometry.valueX(100,width,54)==197,
           "right aligned actual measured value");
        ok(TargetHudSlateReadoutGeometry.separated(width,titleWidth,104),
           "exact budget boundary allowed");
        ok(!TargetHudSlateReadoutGeometry.separated(width,titleWidth,105),
           "extra width rejected");
        invalid(()->TargetHudSlateReadoutGeometry.valueBudget(160,-1),
                "negative width rejected");
        invalid(()->TargetHudSlateReadoutGeometry.valueX(0,160,-2),
                "negative numeric width rejected");
        Random rng=new Random(28102026L);
        for(int i=0;i<50000;i++) {
            int cardWidth=30+rng.nextInt(800);
            int textWidth=rng.nextInt(180);
            int x=rng.nextInt(4000)-2000;
            int budget=TargetHudSlateReadoutGeometry.valueBudget(cardWidth,textWidth);
            ok(budget>=0&&budget<=cardWidth,"bounded live numeric budget");
            int valueWidth=budget==0?0:rng.nextInt(budget+1);
            if(TargetHudSlateReadoutGeometry.separated(cardWidth,textWidth,valueWidth)) {
                int labelRight=x+TargetHudSlateReadoutGeometry.INSET+textWidth;
                int valueX=TargetHudSlateReadoutGeometry.valueX(x,cardWidth,valueWidth);
                ok(labelRight+TargetHudSlateReadoutGeometry.GAP<=valueX,
                   "distance columns do not overlap at extreme text lengths");
                ok(valueX+valueWidth==x+cardWidth-
                   TargetHudSlateReadoutGeometry.INSET,
                   "right aligned value stays within card margin");
            } else {
                ok(textWidth+TargetHudSlateReadoutGeometry.GAP+valueWidth >
                   cardWidth-2*TargetHudSlateReadoutGeometry.INSET,
                   "readout is suppressed only when text does not fit");
            }
        }
        for(boolean hp:new boolean[]{true,false}) {
            TargetHudModernLayout s=TargetHudModernLayout.of("slate");
            ok(s.fits(160,hp?43:29,9,hp),"both Slate distance baselines still fit");
        }
        System.out.println("TARGET_HUD_SLATE_READOUT_GEOMETRY_PASS checks="+count);
    }
}
