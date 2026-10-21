package dev.ghostfusion.tests;

import dev.ghostfusion.ui.NumericSliderScale;
import dev.ghostfusion.ui.UiActions;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.features.FeatureModule;
import java.util.Random;

/** Pure finite-number -> slider -> setting roundtrip and overflow regression. */
public final class NumericSliderScaleTests {
    private static long checks;
    private static void ok(boolean success,String reason) {
        checks++;if(!success)throw new AssertionError(reason);
    }
    private static void rejects(Runnable test,String reason) {
        boolean rejected=false;
        try{test.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,reason);
    }
    private static void boundaries(){
        double max=Double.MAX_VALUE;
        ok(NumericSliderScale.value(-max,max,0.0)==-max,"negative huge exact endpoint");
        ok(NumericSliderScale.value(-max,max,1.0)==max,"positive huge exact endpoint");
        ok(NumericSliderScale.value(-max,max,0.5)==0.0,"cross-zero wide midpoint finite");
        ok(Math.abs(NumericSliderScale.value(-max,max,0.25)+max/2)<=4*Math.ulp(max),
           "cross-zero first quarter accurate within floating-point rounding");
        ok(Math.abs(NumericSliderScale.value(-max,max,0.75)-max/2)<=4*Math.ulp(max),
           "cross-zero last quarter accurate within floating-point rounding");
        ok(NumericSliderScale.fraction(0.0,-max,max)==0.5,
           "extreme finite endpoints have meaningful drawn midpoint");
        ok(NumericSliderScale.fraction(-max,-max,max)==0.0,"minimum empty fill");
        ok(NumericSliderScale.fraction(max,-max,max)==1.0,"maximum full fill");
        ok(NumericSliderScale.fraction(2.0,2.0,2.0)==0.0,
           "constant range avoids zero denominator");
        ok(NumericSliderScale.value(2.0,2.0,0.75)==2.0,
           "constant range never changes the setting");
        ok(NumericSliderScale.value(0.25,0.75,0.5)==0.5,
           "ordinary configured slider midpoint unchanged");
        ok(NumericSliderScale.fraction(0.5,0.25,0.75)==0.5,
           "ordinary configured slider fill unchanged");
        ok(NumericSliderScale.value(-max,max,-5)==-max &&
           NumericSliderScale.value(-max,max,5)==max,
           "out-of-track values remain clamped");
        rejects(()->new Setting.Number("bad",Double.NaN,0,1),"reject NaN initial");
        rejects(()->new Setting.Number("bad",Double.POSITIVE_INFINITY,0,1),
                "reject infinite initial");
        rejects(()->new Setting.Number("bad",Double.NEGATIVE_INFINITY,-max,max),
                "reject negative infinite initial");
        rejects(()->NumericSliderScale.value(-max,max,Double.NaN),
                "reject NaN pointer fraction");
        rejects(()->NumericSliderScale.value(-max,max,Double.NEGATIVE_INFINITY),
                "reject infinite pointer fraction");
        rejects(()->NumericSliderScale.fraction(Double.NaN,0,1),
                "reject NaN displayed current");
        rejects(()->NumericSliderScale.fraction(0,Double.NaN,1),
                "reject nonfinite min");
        rejects(()->NumericSliderScale.value(2,1,0.5),"reject inverted numeric range");
        rejects(()->NumericSliderScale.value(0,Double.POSITIVE_INFINITY,0.5),
                "reject nonfinite maximum");
        Setting.Number fixed=new Setting.Number("fixed",8,8,8);
        ok(fixed.get()==8.0,"constant numeric setting remains legal");
    }
    private static void uiIntegration(){
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Number wide=new Setting.Number("wide",0,-Double.MAX_VALUE,Double.MAX_VALUE);
        Setting.Number constant=new Setting.Number("constant",5.0,5.0,5.0);
        registry.register(new FeatureModule("render.numeric","Numeric",GhostModule.Category.RENDER),
                          0,wide,constant);
        final int[] saved={0};
        UiActions actions=new UiActions(registry,()->saved[0]++);
        ok(actions.previewFraction("render.numeric","wide",0.25),
           "wide setting can be moved to negative finite value");
        ok(Math.abs(wide.get()+Double.MAX_VALUE/2)<=4*Math.ulp(Double.MAX_VALUE),
           "wide setting negative quarter value");
        ok(actions.previewFraction("render.numeric","wide",0.75),
           "wide setting can be moved to positive finite value");
        ok(Math.abs(wide.get()-Double.MAX_VALUE/2)<=4*Math.ulp(Double.MAX_VALUE),
           "wide setting positive quarter value");
        ok(Math.abs(NumericSliderScale.fraction(wide.get(),wide.min(),wide.max())-0.75)<=1e-15,
           "Forge fill fraction roundtrips extreme setting");
        ok(!actions.previewFraction("render.numeric","constant",0.25),
           "constant setting is a no-op");
        ok(constant.get()==5.0 && saved[0]==0,
           "no configuration writes during numeric preview");
        actions.commitSlider();
        ok(saved[0]==1,"drag completion commits once");
        actions.fraction("render.numeric","wide",0.5);
        ok(wide.get()==0.0 && saved[0]==2,
           "legacy numeric action persists once for extreme range");
        rejects(()->actions.previewFraction("render.numeric","wide",Double.NaN),
                "invalid mouse fraction still rejected");
        registry.shutdown();
    }
    private static void randomized(){
        final double huge=Double.MAX_VALUE;
        final double tiny=Double.MIN_VALUE;
        final double[] edges={
            -huge,-1e308,-1e250,-1e50,-1.0,-tiny,-0.0,0.0,
             tiny,1e-300,1e-30,1.0,1e30,1e250,1e308,huge
        };
        Random random=new Random(2432026L);
        for(int i=0;i<140000;i++){
            double a=edges[random.nextInt(edges.length)];
            double b=edges[random.nextInt(edges.length)];
            double min=Math.min(a,b),max=Math.max(a,b);
            double f=random.nextDouble();
            double mapped=NumericSliderScale.value(min,max,f);
            ok(Double.isFinite(mapped) && mapped>=min && mapped<=max,
               "finite convex mapping under every extreme range "+i);
            double display=NumericSliderScale.fraction(mapped,min,max);
            ok(Double.isFinite(display) && display>=0 && display<=1,
               "drawn fill is finite, clamped and in bounds "+i);
            double rebuilt=NumericSliderScale.value(min,max,display);
            // Near a cancellation point, a one-ULP fraction error represents
            // endpoint-scale error even if the mapped value is near zero.
            double scale=Math.max(Math.abs(min),Math.abs(max));
            double tolerance=16*Math.ulp(scale);
            ok(Math.abs(rebuilt-mapped)<=tolerance ||
               Double.doubleToLongBits(rebuilt)==Double.doubleToLongBits(mapped),
               "mapping roundtrip within floating-point precision "+i);
            ok(NumericSliderScale.value(min,max,0)==min &&
               NumericSliderScale.value(min,max,1)==max,
               "exact ends never overshoot "+i);
        }
        for(int j=0;j<1000;j++) {
            double a=edges[random.nextInt(edges.length)];
            double b=edges[random.nextInt(edges.length)];
            double lo=Math.min(a,b),hi=Math.max(a,b);
            double prev=lo;
            for(int k=0;k<=100;k++){
                double next=NumericSliderScale.value(lo,hi,k/100.0);
                ok(next>=prev && next<=hi,
                   "drag to the right never decreases the setting "+j+"/"+k);
                prev=next;
            }
        }
    }
    public static void main(String[] args){
        boundaries();uiIntegration();randomized();
        ok(checks>600000,"extensive numeric slider extremes and monotonicity");
        System.out.println("NUMERIC_SLIDER_SCALE_PASS assertions="+checks);
    }
}
