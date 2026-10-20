package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspCornerBoxGeometry;
import dev.ghostfusion.features.EspRenderMode;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** R2.21: first-party 3D corner-line topology, validation and persistence. */
public final class EspCornerBoxTests {
    private static int checks;
    private static void ok(boolean good,String what){
        checks++;if(!good)throw new AssertionError(what);
    }
    private static final class Capture implements EspCornerBoxGeometry.Sink {
        int count;
        final double[][] segments=new double[24][6];
        public void line(double x1,double y1,double z1,
                         double x2,double y2,double z2) {
            if(count>=segments.length)throw new AssertionError("extra corner lines");
            double[] entry=segments[count++];
            entry[0]=x1;entry[1]=y1;entry[2]=z1;
            entry[3]=x2;entry[4]=y2;entry[5]=z2;
        }
    }
    private static Capture capture(double x,double y,double z,
                                   double w,double h,double d,double f){
        Capture c=new Capture();
        ok(EspCornerBoxGeometry.emit(x,y,z,x+w,y+h,z+d,f,c),"valid box emitted");
        ok(c.count==24,"8 corners times 3 perpendicular arms");
        for(int i=0;i<24;i++){
            double[] a=c.segments[i];
            int changes=0;
            for(int axis=0;axis<3;axis++){
                ok(Double.isFinite(a[axis])&&Double.isFinite(a[axis+3]),
                   "finite GL line vertices");
                if(Math.abs(a[axis]-a[axis+3])>1.e-9)changes++;
                double origin=(axis==0?x:axis==1?y:z);
                double length=(axis==0?w:axis==1?h:d);
                ok(a[axis]>=origin-1e-7&&a[axis]<=origin+length+1e-7,
                   "segment start stays within bounding box");
                ok(a[axis+3]>=origin-1e-7&&a[axis+3]<=origin+length+1e-7,
                   "segment end stays within bounding box");
            }
            ok(changes==1,"each corner arm runs along one axis only");
            ok(i%3==0 ? Math.abs(a[3]-a[0])>0 :
               i%3==1 ? Math.abs(a[4]-a[1])>0 :
                        Math.abs(a[5]-a[2])>0,
               "axis order consistent with corner triplets");
        }
        return c;
    }
    private static void topology(){
        Capture a=capture(-.3,63,-.3,.6,1.8,.6,.25);
        ok(Math.abs(a.segments[0][3]-(-.15))<1e-8,
           "first low corner X arm one-quarter width");
        ok(Math.abs(a.segments[1][4]-63.45)<1e-8,
           "first low corner Y arm one-quarter height");
        ok(Math.abs(a.segments[2][5]-(-.15))<1e-8,
           "first low corner Z arm one-quarter depth");
        Capture half=capture(-.3,63,-.3,.6,1.8,.6,.5);
        ok(half.segments[0][3]>a.segments[0][3],
           "longer corner setting lengthens same world-space arm");
        ok(half.segments[0][0]==a.segments[0][0],
           "changing setting never moves corner start");
        for(int i=0;i<8;i++){
            int base=i*3;
            for(int j=1;j<3;j++)
                for(int k=0;k<3;k++)
                    ok(a.segments[base][k]==a.segments[base+j][k],
                       "all three legs share the same corner");
        }
    }
    private static void malformed(){
        ok(!EspCornerBoxGeometry.valid(0,0,0,0,1,1,.25),"degenerate X rejected");
        ok(!EspCornerBoxGeometry.valid(0,0,0,1,0,1,.25),"degenerate Y rejected");
        ok(!EspCornerBoxGeometry.valid(0,0,0,1,1,-1,.25),"negative Z rejected");
        ok(!EspCornerBoxGeometry.valid(0,0,0,12,1,1,.25),"oversized entity rejected");
        ok(!EspCornerBoxGeometry.valid(Double.NaN,0,0,1,1,1,.25),
           "nonfinite location rejected");
        for(double invalid:new double[]{-.01,.0999,.5001,2.0,
                                        Double.NaN,Double.POSITIVE_INFINITY}){
            ok(!EspCornerBoxGeometry.valid(0,0,0,1,2,1,invalid),
               "invalid length rejected");
            Capture c=new Capture();
            ok(!EspCornerBoxGeometry.emit(0,0,0,1,2,1,invalid,c),
               "invalid geometry never calls GL sink");
            ok(c.count==0,"invalid corner style produces no partial lines");
        }
        boolean nullThrown=false;
        try{EspCornerBoxGeometry.emit(0,0,0,1,2,1,.25,null);}
        catch(IllegalArgumentException expected){nullThrown=true;}
        ok(nullThrown,"null GL sink rejected");
    }
    private static void replay(){
        Random r=new Random(2212026L);
        for(int i=0;i<15000;i++){
            double x=r.nextDouble()*1200-600,y=r.nextDouble()*260-20,
                   z=r.nextDouble()*1200-600;
            double w=.10+r.nextDouble()*2.5,h=.3+r.nextDouble()*2.8,
                   d=.10+r.nextDouble()*2.5,f=.1+.4*r.nextDouble();
            Capture a=capture(x,y,z,w,h,d,f);
            ok(a.count==24,"stable 24 lines across randomized worlds");
        }
    }
    private static void persistence()throws Exception{
        Path p=Files.createTempDirectory("ghostc-r221-corners");
        HostRuntime host=new HostRuntime(p);
        ok(Math.abs(host.espCornerLength()-.26)<1e-8,
           "existing profile gets new setting default");
        ok("box".equals(host.espStyle()),"old default style unchanged");
        host.uiActions().fraction("render.esp","corner_length",1);
        ok(Math.abs(host.espCornerLength()-.5)<1e-8,
           "GUI full slider increases corner arm to half edge");
        for(int i=0;i<5;i++)host.uiActions().cycle("render.esp","style");
        ok("corner_box".equals(host.espStyle()),
           "new mode follows five preexisting style choices");
        host.persist();host.close();
        HostRuntime loaded=new HostRuntime(p);
        ok(Math.abs(loaded.espCornerLength()-.5)<1e-8,"length persisted");
        ok("corner_box".equals(loaded.espStyle()),"new style persisted");
        loaded.uiActions().cycle("render.esp","style");
        ok("corner_skeleton".equals(loaded.espStyle()),"combined mode selectable");
        loaded.close();
    }
    public static void main(String[] args)throws Exception{
        topology();malformed();replay();persistence();
        ok(checks>1000000,"substantial 3D corner geometry regression");
        System.out.println("ESP_CORNER_BOX_PASS assertions="+checks);
    }
}
