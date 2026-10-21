package dev.ghostfusion.tests;

import dev.ghostfusion.features.HudLiveTargetGate;
import java.util.Random;

/** Read-only HUD redraw gate for player state changing between tick and paint. */
public final class HudLiveTargetGateTests {
    private static int assertions;
    private static void ok(boolean passed,String message){
        assertions++;if(!passed)throw new AssertionError(message);
    }
    private static void basics(){
        Object local=new Object(),target=new Object();
        ok(HudLiveTargetGate.eligible(target,local,false,20,false,true),
           "live current-world visible target accepted");
        ok(!HudLiveTargetGate.eligible(null,local,false,20,false,true),
           "missing live target rejected");
        ok(!HudLiveTargetGate.eligible(local,local,false,20,false,true),
           "local player never receives own target card");
        ok(!HudLiveTargetGate.eligible(target,local,true,20,false,true),
           "death since snapshot suppresses HUD even with positive cached health");
        ok(!HudLiveTargetGate.eligible(target,local,false,0,false,false),
           "depleted live health suppresses stale snapshot card");
        ok(!HudLiveTargetGate.eligible(target,local,false,20,true,true),
           "invisibility since snapshot suppresses visible-only HUD");
        ok(HudLiveTargetGate.eligible(target,local,false,20,true,false),
           "unrestricted target card may still display invisible live entity");
        for(double v:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,
                                  Double.POSITIVE_INFINITY,-2.0,-0.0}) {
            ok(!HudLiveTargetGate.eligible(target,local,false,v,false,false),
               "bad live health rejected "+v);
        }
        ok(HudLiveTargetGate.eligible(target,local,false,Double.MIN_VALUE,false,true),
           "any finite positive health can display");
    }
    private static void randomized(){
        Random rnd=new Random(2422026L);
        Object local=new Object(),target=new Object(),other=new Object();
        for(int i=0;i<120000;i++){
            Object candidate;
            switch(rnd.nextInt(4)){
                case 0: candidate=null;break;
                case 1: candidate=local;break;
                case 2: candidate=other;break;
                default:candidate=target;break;
            }
            boolean dead=rnd.nextBoolean();
            boolean invisible=rnd.nextBoolean(),visibleOnly=rnd.nextBoolean();
            double live;
            switch(rnd.nextInt(9)){
                case 0: live=Double.NaN;break;
                case 1: live=Double.POSITIVE_INFINITY;break;
                case 2: live=Double.NEGATIVE_INFINITY;break;
                case 3: live=0.0;break;
                case 4: live=-rnd.nextDouble();break;
                default:live=0.01+rnd.nextDouble()*150.0;break;
            }
            boolean expected=candidate!=null && candidate!=local && !dead &&
                Double.isFinite(live) && live>0 && (!visibleOnly || !invisible);
            boolean actual=HudLiveTargetGate.eligible(candidate,local,dead,live,invisible,visibleOnly);
            ok(actual==expected,"live target state parity "+i);
            if(actual)ok(HudLiveTargetGate.eligible(candidate,local,dead,live,invisible,false),
               "disabling visible-only cannot hide otherwise valid target "+i);
        }
        // Snapshot health must NOT affect display admission; current health
        // alone determines the read-only card at paint time.
        double cached=20.0;
        for(int i=0;i<18000;i++){
            double current=i%3==0?0:i%3==1?5.0:Double.NaN;
            boolean expected=current==5.0;
            ok(HudLiveTargetGate.eligible(target,local,false,current,false,true)==expected,
               "snapshot="+cached+" must not override current health "+i);
        }
    }
    private static void liveRange(){
        ok(HudLiveTargetGate.withinRange(3,4,0,0,0,0,5),
            "exact edge remains admitted");
        ok(!HudLiveTargetGate.withinRange(3,4,0,0,0,0,4.999),
            "target outside the selected range is suppressed");
        ok(!HudLiveTargetGate.withinRange(3,4,0,0,0.1,0,5),
            "movement of local player can invalidate previous selection");
        ok(HudLiveTargetGate.withinRange(3,4,0,0,0.1,0,5.1),
            "live range is re-evaluated without an arbitrary displacement cutoff");
        ok(HudLiveTargetGate.withinRange(0,0,0,0,0,0,Double.MIN_VALUE),
            "coincident entities remain in any positive range");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,
                                    Double.NEGATIVE_INFINITY}){
            ok(!HudLiveTargetGate.withinRange(bad,0,0,0,0,0,5),
                "invalid target position refused");
            ok(!HudLiveTargetGate.withinRange(0,0,0,bad,0,0,5),
                "invalid local position refused");
            ok(!HudLiveTargetGate.withinRange(0,0,0,0,0,0,bad),
                "invalid range refused");
        }
        ok(!HudLiveTargetGate.withinRange(0,0,0,0,0,0,0),"zero range refused");
        ok(!HudLiveTargetGate.withinRange(0,0,0,0,0,0,-1),"negative range refused");
        ok(!HudLiveTargetGate.withinRange(Double.MAX_VALUE,0,0,
            -Double.MAX_VALUE,0,0,24),"overflowed distance refused");
        Random rnd=new Random(2482026L);
        for(int i=0;i<120000;i++){
            double localX=rnd.nextDouble()*200-100;
            double localY=rnd.nextDouble()*200-100;
            double localZ=rnd.nextDouble()*200-100;
            double dx=rnd.nextDouble()*60-30;
            double dy=rnd.nextDouble()*60-30;
            double dz=rnd.nextDouble()*60-30;
            double maxRange=1+rnd.nextDouble()*24;
            double distanceSq=dx*dx+dy*dy+dz*dz;
            boolean expected=distanceSq<=maxRange*maxRange;
            boolean actual=HudLiveTargetGate.withinRange(
                localX+dx,localY+dy,localZ+dz,
                localX,localY,localZ,maxRange);
            ok(actual==expected,"live range randomized replay "+i);
        }
    }
    public static void main(String[] args){
        basics();randomized();liveRange();
        ok(assertions>138000,"substantial HUD redraw admission replay");
        System.out.println("HUD_LIVE_TARGET_GATE_PASS assertions="+assertions);
    }
}
