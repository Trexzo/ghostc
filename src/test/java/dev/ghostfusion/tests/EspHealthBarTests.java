package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspHealthBarGeometry;
import java.util.Random;

/** First-party ESP health gauge world-space geometry, independent of Minecraft. */
public final class EspHealthBarTests {
    private static int checks;
    private static void ok(boolean condition,String label) {
        checks++;
        if(!condition)throw new AssertionError(label);
    }
    private static void rejected(Runnable action,String label) {
        boolean yes=false;
        try {action.run();}catch(IllegalArgumentException expected){yes=true;}
        ok(yes,label);
    }
    private static final class Capture implements EspHealthBarGeometry.Sink {
        int count;
        final double[][] ends=new double[2][6];
        final double[] fractions=new double[2];
        final boolean[] fill=new boolean[2];
        public void segment(boolean filled,double fraction,double x1,double y1,double z1,
                            double x2,double y2,double z2) {
            if(count>=2)throw new AssertionError("Too many health segments");
            double[] v={x1,y1,z1,x2,y2,z2};
            for(int i=0;i<6;i++) {
                ok(Double.isFinite(v[i]),"finite GL segment");
                ends[count][i]=v[i];
            }
            fill[count]=filled;
            fractions[count]=fraction;
            count++;
        }
    }
    private static Capture emit(double x,double y,double z,double height,double width,
                                double viewerX,double viewerZ,double health,double maximum) {
        Capture c=new Capture();
        ok(EspHealthBarGeometry.emit(x,y,z,height,width,viewerX,viewerZ,
            health,maximum,c),"expected health overlay");
        return c;
    }
    private static void typical() {
        Capture full=emit(0,64,0,1.8,.6,0,5,20,20);
        Capture half=emit(0,64,0,1.8,.6,0,5,10,20);
        Capture empty=emit(0,64,0,1.8,.6,0,5,0,20);
        Capture over=emit(0,64,0,1.8,.6,0,5,40,20);
        ok(full.count==2&&full.fill[1],"background and full foreground");
        ok(half.count==2&&half.fill[1],"background and half foreground");
        ok(empty.count==1&&!empty.fill[0],"zero health draws background only");
        ok(over.count==2&&over.fractions[1]==1.0,"overhealed clamped");
        ok(Math.abs(half.ends[1][4]-half.ends[0][1]-
           (half.ends[0][4]-half.ends[0][1])*.5)<.000001,
           "health rises from bottom by exactly half");
        ok(Math.abs(full.ends[1][4]-full.ends[0][4])<.000001,
           "full health fills bar");
        ok(Math.abs(half.ends[0][0]-.48)<.000001,
           "billboard right side from viewer along positive Z");
        Capture rotated=emit(0,64,0,1.8,.6,5,0,10,20);
        ok(Math.abs(rotated.ends[0][2]+.48)<.000001,
           "camera rotation moves billboard into XZ-facing plane");
        Capture moved=emit(100,20,-200,1.8,.6,100,0,10,20);
        ok(Math.abs(moved.ends[0][0]-100.48)<.000001,
           "translation-invariant local horizontal offset");
        rejected(()->EspHealthBarGeometry.emit(0,0,0,1.8,.6,0,5,10,20,null),
           "null sink refused");
        double[][] invalid={
            {Double.NaN,0,0,1.8,.6,0,5,10,20},
            {0,Double.POSITIVE_INFINITY,0,1.8,.6,0,5,10,20},
            {0,0,Double.NaN,1.8,.6,0,5,10,20},
            {0,0,0,.01,.6,0,5,10,20},
            {0,0,0,1.8,Double.NaN,0,5,10,20},
            {0,0,0,1.8,.6,0,0,10,20},
            {0,0,0,1.8,.6,0,5,Double.NaN,20},
            {0,0,0,1.8,.6,0,5,10,0},
            {0,0,0,1.8,.6,0,5,-1,20},
            {0,0,0,1.8,.6,0,5,10,Double.POSITIVE_INFINITY},
            {0,0,0,1.8,.6,0,5,10,-5}
        };
        for(double[] input:invalid) {
            Capture c=new Capture();
            ok(!EspHealthBarGeometry.emit(input[0],input[1],input[2],
                input[3],input[4],input[5],input[6],input[7],input[8],c),
                "invalid health data rejected");
            ok(c.count==0,"no partially emitted vertices on invalid health");
        }
    }
    private static void replay() {
        Random r=new Random(20261020L);
        for(int i=0;i<14000;i++) {
            double x=r.nextDouble()*4000-2000,y=r.nextDouble()*256-60;
            double z=r.nextDouble()*4000-2000;
            double h=.5+r.nextDouble()*2.5,w=.1+r.nextDouble()*2.9;
            double angle=r.nextDouble()*Math.PI*2;
            double vx=x+Math.cos(angle)*3,vz=z+Math.sin(angle)*3;
            double health=r.nextDouble()*40,max=1+r.nextDouble()*39;
            Capture a=emit(x,y,z,h,w,vx,vz,health,max);
            Capture b=emit(x+10,y+20,z-10,h,w,vx+10,vz-10,health,max);
            ok(a.count==2 && b.count==2,"positive health has both segments");
            ok(a.fractions[1]>=0&&a.fractions[1]<=1,
               "fraction bounded "+i);
            ok(Math.abs(a.ends[0][1]-a.ends[1][1])<.000001,
               "foreground/background share bottom");
            ok(a.ends[1][4]<=a.ends[0][4]+.000001,
               "foreground never exceeds full bar");
            for(int j=0;j<6;j++) {
                double shift=(j==1||j==4)?20:(j==0||j==3)?10:-10;
                ok(Math.abs(b.ends[0][j]-a.ends[0][j]-shift)<.000001,
                   "translation invariance "+i);
            }
        }
    }
    public static void main(String[] args) {
        typical();replay();
        ok(checks>100000,"substantial ESP gauge geometry replay");
        System.out.println("ESP_HEALTH_BAR_PASS assertions="+checks);
    }
}
