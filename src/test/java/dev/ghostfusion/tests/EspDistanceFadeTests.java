package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspDistanceFade;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Optional ESP distance-dependent alpha, with opt-out legacy parity. */
public final class EspDistanceFadeTests {
    private static int checks;
    private static void ok(boolean good,String reason) {
        checks++; if(!good)throw new AssertionError(reason);
    }
    private static void rejects(double distance,double range,double start,double minimum){
        boolean rejected=false;
        try{EspDistanceFade.factor(distance,range,true,start,minimum);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"invalid fade input rejected");
    }
    private static void boundaries(){
        ok(EspDistanceFade.factor(Double.NaN,Double.NaN,false,
            Double.NaN,Double.NaN)==1f,"disabled fade never changes legacy output");
        ok(EspDistanceFade.factor(0,32,true,.5,.25)==1f,"near target is fully opaque");
        ok(EspDistanceFade.factor(256,32,true,.5,.25)==1f,"start boundary");
        ok(Math.abs(EspDistanceFade.factor(576,32,true,.5,.25)-.625f)<1e-6,
           "linear midpoint");
        ok(Math.abs(EspDistanceFade.factor(1024,32,true,.5,.25)-.25f)<1e-6,
           "minimum on far boundary");
        ok(EspDistanceFade.factor(1600,32,true,.5,.25)==.25f,
           "beyond limit cannot fall below visibility floor");
        ok(EspDistanceFade.factor(24*24,24,true,.9,1)==1f,
           "100 percent minimum makes no visible change");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,-1}) {
            rejects(bad,32,.5,.25);
            rejects(4,bad,.5,.25);
            rejects(4,32,bad,.25);
            rejects(4,32,.5,bad);
        }
        rejects(4,0,.5,.25);
        rejects(4,32,-.0001,.25);
        rejects(4,32,.90001,.25);
        rejects(4,32,.5,.0499);
        rejects(4,32,.5,1.0001);
    }
    private static void seeded(){
        Random random=new Random(2242026L);
        for(int i=0;i<18000;i++) {
            double range=2+random.nextDouble()*78;
            double start=random.nextDouble()*.9;
            double floor=.05+random.nextDouble()*.95;
            double fraction=random.nextDouble()*1.25;
            double square=fraction*fraction*range*range;
            float actual=EspDistanceFade.factor(square,range,true,start,floor);
            double expected=fraction<=start?1:fraction>=1?floor:
                1-(1-floor)*((fraction-start)/(1-start));
            ok(Float.isFinite(actual),"finite normalized alpha");
            ok(actual>=floor-1e-6&&actual<=1,"bounded by floor and original alpha");
            ok(Math.abs(actual-expected)<1e-6,"independent fade equation");
            double later=Math.min(1.25,fraction+.01);
            float farther=EspDistanceFade.factor(
                later*later*range*range,range,true,start,floor);
            ok(farther<=actual+1e-6,"monotonic distance fade");
            ok(EspDistanceFade.factor(square,range,false,start,floor)==1f,
               "disabled fade preserves all nine ESP modes");
            double lineAlpha=.1+random.nextDouble()*.9;
            double fillAlpha=.04+random.nextDouble()*.46;
            ok(lineAlpha*actual*fillAlpha<=lineAlpha*actual &&
               lineAlpha*actual<=lineAlpha+1e-6,
               "fill and wire opacity attenuate without mutating RGB");
        }
    }
    private static void profile()throws Exception {
        Path path=Files.createTempDirectory("ghostc-esp-fade");
        HostRuntime runtime=new HostRuntime(path);
        ok(!runtime.espDistanceFade(),"disabled for old profiles");
        ok(Math.abs(runtime.espFadeStart()-.5)<1e-9,"50% start default");
        ok(Math.abs(runtime.espFadeMinimum()-.25)<1e-9,"25% floor default");
        runtime.uiActions().bool("render.esp","distance_fade");
        runtime.uiActions().fraction("render.esp","fade_start",1.0);
        runtime.uiActions().fraction("render.esp","fade_min_opacity",0.0);
        ok(runtime.espDistanceFade()&&Math.abs(runtime.espFadeStart()-.9)<1e-9 &&
           Math.abs(runtime.espFadeMinimum()-.05)<1e-9,"GUI controls change settings");
        runtime.persist();runtime.close();
        HostRuntime reload=new HostRuntime(path);
        ok(reload.espDistanceFade(),"toggle persisted");
        ok(Math.abs(reload.espFadeStart()-.9)<1e-9 &&
           Math.abs(reload.espFadeMinimum()-.05)<1e-9,"sliders persisted");
        reload.uiActions().bool("render.esp","distance_fade");
        ok(!reload.espDistanceFade(),"toggle back off");
        reload.close();
    }
    public static void main(String[] args)throws Exception {
        boundaries();seeded();profile();
        ok(checks>108000,"wide distance fade regression");
        System.out.println("ESP_DISTANCE_FADE_PASS assertions="+checks);
    }
}
