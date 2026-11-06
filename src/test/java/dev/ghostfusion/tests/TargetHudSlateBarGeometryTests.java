package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudSlateBarGeometry;
import dev.ghostfusion.features.TargetHudModernLayout;
import java.util.Random;

/**
 * 50k source-independent HUD bar pixel checks, including odd widths, reverse
 * screen origins, nonfinite fractions, and the same font-height admission.
 */
public final class TargetHudSlateBarGeometryTests {
    private static long checks;
    private static void ok(boolean yes,String desc) {
        checks++;if(!yes)throw new AssertionError(desc);
    }
    private static void rejects(Runnable code,String desc) {
        boolean thrown=false;
        try{code.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,desc);
    }
    private static void verify(int x,int width,int height,double fraction) {
        boolean valid=TargetHudSlateBarGeometry.fits(width,height,9);
        if(!valid)return;
        ok(TargetHudModernLayout.of("slate").fits(width,height,9,true),
            "Slate style and bar admission agree");
        int left=TargetHudSlateBarGeometry.left(x);
        int right=TargetHudSlateBarGeometry.right(x,width);
        int track=right-left;
        ok(left>=x&&right<=x+width,"bar within card");
        ok(TargetHudSlateBarGeometry.TOP+TargetHudSlateBarGeometry.HEIGHT<=height,
            "bar below text, inside bottom");
        ok(TargetHudSlateBarGeometry.DIVIDER_Y< TargetHudSlateBarGeometry.TOP,
            "type divider above bar");
        int filled=TargetHudSlateBarGeometry.filled(track,fraction);
        ok(filled>=0&&filled<=track,"clamped health fill");
        int old=0;
        for(int part=1;part<=3;part++) {
            int at=TargetHudSlateBarGeometry.marker(track,part);
            ok(at>0&&at<track,"quarter marker inside track");
            ok(at>old,"quarter marks ordered");
            old=at;
        }
    }
    public static void main(String[] args) {
        verify(0,160,43,0);
        verify(0,160,43,1);
        verify(-100,160,43,0.3333);
        ok(TargetHudSlateBarGeometry.left(10)==19 &&
            TargetHudSlateBarGeometry.right(10,160)==161,"160px inset geometry");
        ok(TargetHudSlateBarGeometry.filled(142,0)==0 &&
            TargetHudSlateBarGeometry.filled(142,1)==142 &&
            TargetHudSlateBarGeometry.filled(142,Double.NaN)==0,
            "zero/full/nonfinite health values safe");
        ok(!TargetHudSlateBarGeometry.fits(160,29,9),
            "health-off card must never paint a health bar");
        ok(!TargetHudSlateBarGeometry.fits(160,43,13),
            "large pixel font cannot collide with separator");
        rejects(()->TargetHudSlateBarGeometry.marker(142,0),"zero quarter invalid");
        rejects(()->TargetHudSlateBarGeometry.marker(142,4),"fourth quarter invalid");
        rejects(()->TargetHudSlateBarGeometry.filled(0,0.7),"empty track invalid");
        rejects(()->TargetHudSlateBarGeometry.right(0,16),"narrow card invalid");
        Random rng=new Random(28102026L);
        for(int i=0;i<50000;i++) {
            int width=36+rng.nextInt(700),h=29+rng.nextInt(70);
            int x=rng.nextInt(1800)-700;
            double fraction=rng.nextDouble()*4-1.5;
            verify(x,width,h,fraction);
        }
        System.out.println("TARGET_HUD_SLATE_BAR_GEOMETRY_PASS checks="+checks);
    }
}
