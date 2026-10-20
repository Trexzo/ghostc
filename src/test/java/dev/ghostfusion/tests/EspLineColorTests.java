package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspLineColor;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.20: normalized RGBA ESP styling, invalid settings and profile roundtrip. */
public final class EspLineColorTests {
    private static int checks;
    private static void ok(boolean yes,String what){
        checks++;if(!yes)throw new AssertionError(what);
    }
    private static void reject(double r,double g,double b,double a){
        boolean threw=false;
        try{EspLineColor.of(r,g,b,a);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"nonfinite/out-of-range color component rejected");
    }
    private static void policy(){
        EspLineColor legacy=EspLineColor.of(.62,.56,1.0,.82);
        ok(Math.abs(legacy.red-.62f)<.000001,"original red preserved");
        ok(Math.abs(legacy.green-.56f)<.000001,"original green preserved");
        ok(legacy.blue==1f,"original blue preserved");
        ok(Math.abs(legacy.opacity-.82f)<.000001,"original alpha preserved");
        EspLineColor dark=EspLineColor.of(0,0,0,.1);
        ok(dark.red==0&&dark.green==0&&dark.blue==0&&
           Math.abs(dark.opacity-.1f)<.000001,"minimum valid components");
        EspLineColor bright=EspLineColor.of(1,1,1,1);
        ok(bright.red==1&&bright.green==1&&bright.blue==1&&
           bright.opacity==1,"maximum valid components");
        for(double invalid:new double[]{-1,-.00001,1.000001,
                                        Double.NaN,Double.POSITIVE_INFINITY,
                                        Double.NEGATIVE_INFINITY}){
            reject(invalid,0,0,.82);
            reject(0,invalid,0,.82);
            reject(0,0,invalid,.82);
            reject(0,0,0,invalid);
        }
        reject(0,0,0,0);
        reject(0,0,0,.09999);
        Random r=new Random(2202026L);
        for(int i=0;i<25000;i++){
            double red=r.nextDouble(),green=r.nextDouble(),
                   blue=r.nextDouble(),opacity=.1+.9*r.nextDouble();
            EspLineColor col=EspLineColor.of(red,green,blue,opacity);
            ok(col.red>=0&&col.red<=1,"red finite and in GL range "+i);
            ok(col.green>=0&&col.green<=1,"green finite and in GL range "+i);
            ok(col.blue>=0&&col.blue<=1,"blue finite and in GL range "+i);
            ok(col.opacity>=.1f&&col.opacity<=1f,"alpha finite and bounded "+i);
            ok(Math.abs(col.red-red)<.000001,"red precision "+i);
            ok(Math.abs(col.green-green)<.000001,"green precision "+i);
            ok(Math.abs(col.blue-blue)<.000001,"blue precision "+i);
            ok(Math.abs(col.opacity-opacity)<.000001,"alpha precision "+i);
        }
    }
    private static void persistence()throws Exception{
        Path p=Files.createTempDirectory("ghostc-r220-color");
        HostRuntime host=new HostRuntime(p);
        ok(Math.abs(host.espLineColor().red-.62f)<.000001,
            "legacy profile red default");
        ok(Math.abs(host.espLineColor().opacity-.82f)<.000001,
            "legacy profile alpha default");
        host.uiActions().fraction("render.esp","red",0.2);
        host.uiActions().fraction("render.esp","green",0.8);
        host.uiActions().fraction("render.esp","blue",0.05);
        host.uiActions().fraction("render.esp","opacity",0.75);
        host.persist();
        host.close();
        HostRuntime restored=new HostRuntime(p);
        EspLineColor c=restored.espLineColor();
        ok(Math.abs(c.red-.2f)<.000001&&Math.abs(c.green-.8f)<.000001&&
           Math.abs(c.blue-.05f)<.000001&&Math.abs(c.opacity-.775f)<.000001,
           "RGBA restored from existing profile serializer");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try{restored.espLineColor();}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        worker.start();worker.join();
        ok(blocked.get(),"settings require owner-thread");
        restored.close();
    }
    public static void main(String[] args)throws Exception{
        policy();persistence();
        ok(checks>170000,"substantial deterministic RGBA validation");
        System.out.println("ESP_LINE_COLOR_PASS assertions="+checks);
    }
}
