package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspHealthBarGeometry;
import dev.ghostfusion.features.EspHealthBarGeometry.Side;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.26 original health gauge parity with configurable camera-facing sides. */
public final class EspHealthPlacementTests {
    private static int assertions;
    private static void ok(boolean pass,String why){
        assertions++;if(!pass)throw new AssertionError(why);
    }
    private static final class Capture implements EspHealthBarGeometry.Sink {
        int count;
        final double[][] points=new double[2][6];
        final double[] fractions=new double[2];
        final boolean[] filled=new boolean[2];
        public void segment(boolean fill,double fraction,double x1,double y1,double z1,
                            double x2,double y2,double z2){
            if(count>=2)throw new AssertionError("excess health line");
            double[] p=points[count];
            p[0]=x1;p[1]=y1;p[2]=z1;
            p[3]=x2;p[4]=y2;p[5]=z2;
            fractions[count]=fraction;filled[count]=fill;count++;
        }
    }
    private static void same(double a,double b,String reason){
        ok(Math.abs(a-b)<1e-7,reason);
    }
    private static void compare(double x,double y,double z,double height,double width,
                                double viewerX,double viewerZ,double current,double maximum,
                                double gap){
        Capture right=new Capture(),left=new Capture(),legacy=new Capture();
        ok(EspHealthBarGeometry.emitStyled(x,y,z,height,width,viewerX,viewerZ,
           current,maximum,Side.RIGHT,gap,right),"right side finite geometry");
        ok(EspHealthBarGeometry.emitStyled(x,y,z,height,width,viewerX,viewerZ,
           current,maximum,Side.LEFT,gap,left),"left side finite geometry");
        ok(right.count==left.count,"symmetric line count");
        for(int i=0;i<right.count;i++){
            same((right.points[i][0]+left.points[i][0])/2,x,"X offset mirrors");
            same((right.points[i][2]+left.points[i][2])/2,z,"Z offset mirrors");
            same(right.points[i][1],left.points[i][1],"vertical start identical");
            same(right.points[i][4],left.points[i][4],"vertical end identical");
            same(right.fractions[i],left.fractions[i],"health percentage unchanged");
            ok(right.filled[i]==left.filled[i],"foreground semantics unchanged");
            for(int j:new int[]{0,2,3,5})
                ok(Double.isFinite(right.points[i][j])&&Double.isFinite(left.points[i][j]),
                   "finite endpoint XZ coordinates");
        }
        double span=Math.hypot(right.points[0][0]-x,right.points[0][2]-z);
        same(span,width*.5+gap,"side offset matches configured spacing");
        if(Math.abs(gap-.18)<1e-9){
            ok(EspHealthBarGeometry.emit(x,y,z,height,width,viewerX,viewerZ,
               current,maximum,legacy),"old health API valid");
            ok(legacy.count==right.count,"original API line count");
            for(int i=0;i<legacy.count;i++)for(int j=0;j<6;j++)
                ok(legacy.points[i][j]==right.points[i][j],
                   "original exact world-space coordinate preserved");
        }
    }
    private static void boundaries(){
        ok(Side.of("right")==Side.RIGHT,"original side ID");
        ok(Side.of("left")==Side.LEFT,"left side ID");
        boolean threw=false;
        try{Side.of("some-unknown-side");}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"unknown profile side must not silently switch");
        threw=false;
        try{Side.of(null);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"null side fails validation");
        compare(0,64,0,1.8,.6,0,5,10,20,.18);
        compare(0,64,0,1.8,.6,5,0,10,20,.50);
        compare(0,64,0,1.8,.6,-3,-4,0,20,.05);
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,
                                    Double.NEGATIVE_INFINITY,-.1,.049999,.500001,1}){
            Capture c=new Capture();
            ok(!EspHealthBarGeometry.emitStyled(0,64,0,1.8,.6,0,5,
               10,20,Side.LEFT,bad,c),"invalid gap rejected");
            ok(c.count==0,"invalid gap never partially emits");
        }
        Capture c=new Capture();
        ok(!EspHealthBarGeometry.emitStyled(0,64,0,1.8,.6,0,5,
            10,20,null,.18,c),"null side rejected");
        ok(c.count==0,"invalid side no output");
        ok(!EspHealthBarGeometry.emitStyled(0,64,0,1.8,.6,0,5,
            Double.NaN,20,Side.LEFT,.18,c),"invalid health rejected");
        ok(c.count==0,"invalid health no output");
        threw=false;
        try{EspHealthBarGeometry.emitStyled(0,64,0,1.8,.6,0,5,
            10,20,Side.LEFT,.18,null);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"null segment sink rejected");
    }
    private static void randomized(){
        Random r=new Random(2262026L);
        for(int i=0;i<14000;i++){
            double x=r.nextDouble()*1000-500,y=r.nextDouble()*250-60;
            double z=r.nextDouble()*1000-500,h=.5+r.nextDouble()*2.5;
            double w=.1+r.nextDouble()*2.9;
            double theta=r.nextDouble()*Math.PI*2;
            double vx=x+Math.cos(theta)*5,vz=z+Math.sin(theta)*5;
            double health=r.nextDouble()*35,max=1+r.nextDouble()*34;
            double gap=i%2==0?.18:.05+r.nextDouble()*.45;
            compare(x,y,z,h,w,vx,vz,health,max,gap);
        }
    }
    private static void profiles()throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r226-hp-side");
        HostRuntime host=new HostRuntime(dir);
        ok(host.espHealthSide()==Side.RIGHT,"right remains legacy default");
        same(host.espHealthGap(),.18,"original .18 health spacing");
        host.uiActions().cycle("render.esp","health_side");
        host.uiActions().fraction("render.esp","health_gap",1);
        ok(host.espHealthSide()==Side.LEFT,"GUI side cycle works");
        same(host.espHealthGap(),.5,"GUI maximum spacing works");
        host.persist();host.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok(loaded.espHealthSide()==Side.LEFT,"left placement persisted");
        same(loaded.espHealthGap(),.5,"spacing persisted");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try{loaded.espHealthSide();}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        foreign.start();foreign.join();
        ok(blocked.get(),"GUI settings read requires client owner thread");
        loaded.uiActions().cycle("render.esp","health_side");
        ok(loaded.espHealthSide()==Side.RIGHT,"switching back to old mode works");
        loaded.close();
    }
    public static void main(String[] args)throws Exception {
        boundaries();randomized();profiles();
        ok(assertions>130000,"wide left/right health geometry parity");
        System.out.println("ESP_HEALTH_PLACEMENT_PASS assertions="+assertions);
    }
}
