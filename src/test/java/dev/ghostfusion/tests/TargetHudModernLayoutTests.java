package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudModernLayout;
import java.util.Random;

/**
 * Geometry and legacy isolation regressions for new opt-in Target HUD styles.
 * Uses no Minecraft, GL, network or automation classes.
 */
public final class TargetHudModernLayoutTests {
    private static int count;
    private static void ok(boolean value,String why) {
        count++;
        if(!value)throw new AssertionError(why);
    }
    private static void invalid(String name) {
        boolean rejected=false;
        try {TargetHudModernLayout.of(name);}
        catch(IllegalArgumentException expected) {rejected=true;}
        ok(rejected,"reject unsupported style");
    }
    private static void test() {
        TargetHudModernLayout minimal=TargetHudModernLayout.of("minimal");
        TargetHudModernLayout slate=TargetHudModernLayout.of("slate");
        ok(minimal==TargetHudModernLayout.of("minimal"),"minimal allocation-free");
        ok(slate==TargetHudModernLayout.of("slate"),"slate allocation-free");
        ok(minimal!=slate && !minimal.topBand && slate.topBand,
            "different first-party layouts");
        ok(minimal.trackY!=slate.trackY &&
           minimal.trackHeight!=slate.trackHeight,"distinct HP geometry");
        for(TargetHudModernLayout style:new TargetHudModernLayout[]{minimal,slate}) {
            ok(style.fits(160,43,9,true),"health HUD within full card");
            ok(style.fits(160,29,9,false),"health-off HUD within compact card");
            ok(!style.fits(160,29,9,true),"reject too-small HP card");
            ok(!style.fits(160,15,9,false),"reject too-small compact card");
            ok(!style.fits(0,43,9,true),"reject zero width");
            ok(!style.fits(160,43,0,true),"reject bad font");
            ok(style.nameY+9<=29 && style.distanceWithoutHealthY+9<=29,
                "compact rows fit 29px");
            ok(style.nameY+9<=43 && style.healthLabelY+9<=43 &&
               style.trackY+style.trackHeight<=43,"full rows fit 43px");
            ok(style.distanceWithHealthY+9<=style.trackY ||
               style.trackY+style.trackHeight<=style.distanceWithHealthY,
               "HP and range text never overlap");
        }
        invalid("classic");invalid("glass");invalid("");invalid(null);
        Random r=new Random(20261026L);
        for(int i=0;i<24000;i++) {
            TargetHudModernLayout s=r.nextBoolean()?minimal:slate;
            int width=1+r.nextInt(300),height=1+r.nextInt(70);
            int fontH=1+r.nextInt(20);
            boolean health=r.nextBoolean();
            boolean accepted=s.fits(width,height,fontH,health);
            if(accepted) {
                int distanceY=health?s.distanceWithHealthY:s.distanceWithoutHealthY;
                ok(width>=30 && distanceY+fontH<=height &&
                   s.nameY+fontH<=height,"accepted viewport remains bounded");
                if(health) {
                    ok(s.trackY+s.trackHeight<=height &&
                       s.healthLabelY+fontH<=height,
                       "health paint remains bounded");
                }
            }
        }
    }
    public static void main(String[] args) {
        test();
        System.out.println("TARGET_HUD_MODERN_LAYOUT_PASS checks="+count);
    }
}
