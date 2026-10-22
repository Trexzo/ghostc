package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspLivePaintGate;
import dev.ghostfusion.features.EspDistanceFade;
import dev.ghostfusion.features.HudLiveTargetGate;
import java.util.Random;

/** R2.60 ESP draw-time range and fade use current local/target positions. */
public final class EspLivePaintGateTests {
    private static int checks;
    private static void ok(boolean v,String label) {
        checks++;if(!v)throw new AssertionError(label);
    }
    private static double live(double tx,double ty,double tz,
                               double lx,double ly,double lz,double range) {
        return EspLivePaintGate.distanceSquared(tx,ty,tz,lx,ly,lz,range);
    }
    private static void boundaries(){
        ok(live(3,4,0,0,0,0,5)==25,"inclusive five-block edge");
        ok(Double.isNaN(live(3,4,0,0,0,0,4.99)),"outside range rejected");
        ok(Double.isNaN(live(3,4,0,0,-.1,0,5)),
            "local player moving after candidate selection invalidates draw");
        ok(Double.isNaN(live(6,0,0,0,0,0,5)),
            "target moving after candidate selection invalidates draw");
        ok(live(0,0,0,0,0,0,5)==0,"overlapping positions remain admissible");
        ok(live(4,3,0,0,0,0,5)==live(-4,-3,0,0,0,0,5),
            "live range symmetric");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,
                                     Double.NEGATIVE_INFINITY,Double.MAX_VALUE}) {
            ok(Double.isNaN(live(bad,1,0,0,0,0,5)),
                "invalid/overflow target distance rejected");
            ok(Double.isNaN(live(0,0,0,bad,0,0,5)),
                "invalid/overflow local position rejected");
        }
        ok(Double.isNaN(live(1,0,0,0,0,0,Double.NaN)),
            "NaN range rejected");
        ok(Double.isNaN(live(1,0,0,0,0,0,0)),"zero range rejected");
        ok(Double.isNaN(live(1,0,0,0,0,0,-1)),"negative range rejected");
        ok(Double.isNaN(live(1,0,0,0,0,0,Double.POSITIVE_INFINITY)),
            "infinite range rejected");
        double old=live(8,0,0,0,0,0,16);
        double current=live(14,0,0,0,0,0,16);
        ok(old==64&&current==196,"two in-range positions retain different fade distances");
        ok(EspDistanceFade.factor(current,16,true,.5,.25)<
           EspDistanceFade.factor(old,16,true,.5,.25),
           "optional fade updates when player moves farther while still in range");
        ok(EspDistanceFade.factor(current,16,false,.5,.25)==1f,
           "disabled fade remains legacy full opacity");
    }
    private static void randomized(){
        Random r=new Random(2602026L);
        for(int i=0;i<120000;i++) {
            double max=2+r.nextDouble()*78;
            double lx=(r.nextDouble()-.5)*100000;
            double ly=(r.nextDouble()-.5)*100000;
            double lz=(r.nextDouble()-.5)*100000;
            double tx=lx+(r.nextDouble()-.5)*max*4;
            double ty=ly+(r.nextDouble()-.5)*max*4;
            double tz=lz+(r.nextDouble()-.5)*max*4;
            double dx=tx-lx,dy=ty-ly,dz=tz-lz;
            double expected=dx*dx+dy*dy+dz*dz;
            boolean within=expected<=max*max;
            double observed=live(tx,ty,tz,lx,ly,lz,max);
            ok(within?Double.compare(observed,expected)==0:Double.isNaN(observed),
               "independent live distance/range oracle "+i);
            ok(Double.isFinite(observed)==HudLiveTargetGate.withinRange(
                tx,ty,tz,lx,ly,lz,max),
               "HUD and ESP final distance admission agree "+i);
            if(within) {
                double old=expected;
                // A subsequent move after paint may invalidate the candidate.
                double pushed=live(tx+max*3,ty,tz,lx,ly,lz,max);
                ok(Double.isNaN(pushed) || pushed<=max*max,
                    "draw-time movement still bounded "+i);
                ok(old>=0 && Double.isFinite(observed),
                    "finite distances are never negative "+i);
            }
        }
    }
    public static void main(String[] args) {
        boundaries();randomized();
        ok(checks>250000,"draw-time admission properties deeply exercised");
        System.out.println("ESP_LIVE_PAINT_GATE_PASS checks="+checks);
    }
}
