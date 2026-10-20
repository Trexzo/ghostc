package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspFilledBoxGeometry;
import dev.ghostfusion.features.EspRenderMode;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Exact six-face AABB volume topology and persisted styling contract. */
public final class EspFilledBoxTests {
    private static int checks;
    private static void ok(boolean yes,String why){
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static final class Capture implements EspFilledBoxGeometry.Sink {
        int count;
        final double[][] faces=new double[6][12];
        public void quad(double ax,double ay,double az,
                         double bx,double by,double bz,
                         double cx,double cy,double cz,
                         double dx,double dy,double dz){
            if(count==6)throw new AssertionError("extra quad");
            double[] a=faces[count++];
            a[0]=ax;a[1]=ay;a[2]=az;
            a[3]=bx;a[4]=by;a[5]=bz;
            a[6]=cx;a[7]=cy;a[8]=cz;
            a[9]=dx;a[10]=dy;a[11]=dz;
        }
    }
    private static void geometry(double x,double y,double z,
                                 double w,double h,double d) {
        Capture c=new Capture();
        ok(EspFilledBoxGeometry.valid(x,y,z,x+w,y+h,z+d),"valid finite box");
        ok(EspFilledBoxGeometry.emit(x,y,z,x+w,y+h,z+d,c),"valid box emitted");
        ok(c.count==6,"six quad faces emitted");
        boolean[] planes=new boolean[6];
        for(int i=0;i<6;i++){
            double[] f=c.faces[i];
            boolean plane=false;
            int fixed=-1;
            for(int axis=0;axis<3;axis++){
                double v=f[axis];
                if(f[3+axis]==v && f[6+axis]==v && f[9+axis]==v){
                    fixed=axis;
                    double min=axis==0?x:axis==1?y:z;
                    double len=axis==0?w:axis==1?h:d;
                    plane=Math.abs(v-min)<=1.e-8||
                          Math.abs(v-(min+len))<=1.e-8;
                    if(plane){
                        int index=axis*2+(Math.abs(v-min)<=1.e-8?0:1);
                        ok(!planes[index],"no duplicate plane "+index);
                        planes[index]=true;
                    }
                    break;
                }
            }
            ok(fixed>=0&&plane,"quad occupies a boundary plane");
            for(int j=0;j<4;j++){
                for(int axis=0;axis<3;axis++){
                    double v=f[j*3+axis];
                    double start=axis==0?x:axis==1?y:z;
                    double length=axis==0?w:axis==1?h:d;
                    ok(Double.isFinite(v)&&v>=start-1e-7&&v<=start+length+1e-7,
                       "finite quad vertex within selected AABB");
                }
            }
            // Each face must have nonzero area, even with extreme fractions.
            double ax=f[3]-f[0],ay=f[4]-f[1],az=f[5]-f[2];
            double bx=f[6]-f[0],by=f[7]-f[1],bz=f[8]-f[2];
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
            ok(nx*nx+ny*ny+nz*nz>1.e-12,"nondegenerate polygon face");
        }
        for(int i=0;i<6;i++)ok(planes[i],"all six min/max AABB faces present");
    }
    private static void invalid(){
        double[] bad={Double.NaN,Double.POSITIVE_INFINITY,
                      Double.NEGATIVE_INFINITY};
        for(double n:bad){
            Capture c=new Capture();
            ok(!EspFilledBoxGeometry.emit(n,0,0,1,1,1,c),
               "nonfinite min vertex rejected");
            ok(c.count==0,"invalid volume cannot emit partial GL vertices");
            ok(!EspFilledBoxGeometry.valid(0,0,0,n,1,1),
               "nonfinite max vertex rejected");
        }
        double[][] badBox={
            {0,0,0,0,2,1},{0,0,0,1,0,1},{0,0,0,1,2,0},
            {2,0,0,1,2,1},{0,3,0,1,2,1},{0,0,2,1,2,1},
            {0,0,0,9,2,1},{0,0,0,1,9,1},{0,0,0,1,2,9}
        };
        for(double[] b:badBox){
            Capture c=new Capture();
            ok(!EspFilledBoxGeometry.emit(b[0],b[1],b[2],b[3],b[4],b[5],c),
               "degenerate/inverted/oversized box rejected");
            ok(c.count==0,"rejected box emits zero faces");
        }
        boolean threw=false;
        try{EspFilledBoxGeometry.emit(0,0,0,1,2,1,null);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"null face sink refused");
    }
    private static void replay(){
        geometry(-.3,63,-.3,.6,1.8,.6);
        Random random=new Random(2222026L);
        for(int i=0;i<12000;i++){
            double x=random.nextDouble()*1600-800;
            double y=random.nextDouble()*260-40;
            double z=random.nextDouble()*1600-800;
            double w=.07+random.nextDouble()*3;
            double h=.07+random.nextDouble()*3;
            double d=.07+random.nextDouble()*3;
            geometry(x,y,z,w,h,d);
        }
    }
    private static void persistence()throws Exception{
        Path p=Files.createTempDirectory("ghostc-r222-fill");
        HostRuntime host=new HostRuntime(p);
        ok(Math.abs(host.espFillOpacity()-.18f)<.000001,
           "original profiles get defined 18-percent face transparency");
        ok("box".equals(host.espStyle()),"old default remains box");
        host.uiActions().fraction("render.esp","fill_opacity",1.0);
        ok(Math.abs(host.espFillOpacity()-.5f)<.000001,
           "slider covers 50-percent maximum");
        for(int i=0;i<7;i++)host.uiActions().cycle("render.esp","style");
        ok("filled_box".equals(host.espStyle()),"filled box follows seven older modes");
        host.persist();host.close();
        HostRuntime loaded=new HostRuntime(p);
        ok(Math.abs(loaded.espFillOpacity()-.5f)<.000001,"fill alpha persisted");
        ok("filled_box".equals(loaded.espStyle()),"fill style persisted");
        loaded.uiActions().cycle("render.esp","style");
        ok("filled_skeleton".equals(loaded.espStyle()),
           "filled and skeleton combo selectable");
        loaded.uiActions().cycle("render.esp","style");
        ok("box".equals(loaded.espStyle()),"old style cycle wraps after appended modes");
        loaded.close();
    }
    public static void main(String[] args)throws Exception{
        invalid();replay();persistence();
        ok(checks>250000,"substantial deterministic filled-face validation");
        System.out.println("ESP_FILLED_BOX_PASS assertions="+checks);
    }
}
