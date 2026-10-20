package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspFilledBoxGeometry;
import java.util.Random;

/** R2.23: independently verify painter order and legacy six-face equivalence. */
public final class EspBlendOrderTests {
    private static int assertions;
    private static void ok(boolean value,String why) {
        assertions++; if(!value)throw new AssertionError(why);
    }
    private static final class Capture implements EspFilledBoxGeometry.Sink {
        int count;
        final double[][] faces=new double[6][12];
        public void quad(double ax,double ay,double az,double bx,double by,double bz,
                         double cx,double cy,double cz,double dx,double dy,double dz) {
            if(count==6)throw new AssertionError("More than six quads");
            double[] p=faces[count++];
            p[0]=ax;p[1]=ay;p[2]=az;p[3]=bx;p[4]=by;p[5]=bz;
            p[6]=cx;p[7]=cy;p[8]=cz;p[9]=dx;p[10]=dy;p[11]=dz;
        }
    }
    private static double squared(double[] face,double x,double y,double z) {
        double mx=0,my=0,mz=0;
        for(int p=0;p<4;p++) {
            mx+=face[p*3]/4.0;my+=face[p*3+1]/4.0;mz+=face[p*3+2]/4.0;
        }
        double dx=mx-x,dy=my-y,dz=mz-z;
        return dx*dx+dy*dy+dz*dz;
    }
    private static void compare(double minX,double minY,double minZ,
                                double maxX,double maxY,double maxZ,
                                double cameraX,double cameraY,double cameraZ) {
        Capture original=new Capture();
        Capture sorted=new Capture();
        ok(EspFilledBoxGeometry.emit(minX,minY,minZ,maxX,maxY,maxZ,original),
           "Original fixed-order faces still available");
        ok(EspFilledBoxGeometry.emitBackToFront(minX,minY,minZ,
            maxX,maxY,maxZ,cameraX,cameraY,cameraZ,sorted),
            "Sorted emission accepted valid camera");
        ok(original.count==6&&sorted.count==6,"six faces retained");
        boolean[] seen=new boolean[6];
        double previous=Double.POSITIVE_INFINITY;
        for(int i=0;i<6;i++){
            double[] face=sorted.faces[i];
            int matching=-1;
            for(int j=0;j<6;j++){
                boolean same=true;
                for(int k=0;k<12;k++){
                    if(original.faces[j][k]!=face[k]) {same=false;break;}
                }
                if(same)matching=j;
            }
            ok(matching>=0,"Sorted face exactly matches legacy quad vertices");
            ok(!seen[matching],"Each face emitted exactly once");
            seen[matching]=true;
            double depth=squared(face,cameraX,cameraY,cameraZ);
            ok(depth<=previous+1.e-7,"Farther face drawn before nearer face");
            previous=depth;
            for(int j=0;j<matching;j++){
                if(seen[j])continue;
                double candidate=squared(original.faces[j],cameraX,cameraY,cameraZ);
                ok(candidate<=depth+1.e-7,
                   "Earlier unselected face has no greater camera distance");
            }
        }
        for(boolean completed:seen)ok(completed,"Every original face preserved");
    }
    private static void randomized(){
        compare(-.3,63,-.3,.3,64.8,.3,0,65,-4);
        compare(0,0,0,2,2,2,1,1,1);
        compare(0,0,0,2,2,2,9,1,1);
        Random random=new Random(2232026L);
        for(int i=0;i<11000;i++){
            double x=random.nextDouble()*1000-500;
            double y=random.nextDouble()*256-30;
            double z=random.nextDouble()*1000-500;
            double dx=.03+random.nextDouble()*4;
            double dy=.03+random.nextDouble()*4;
            double dz=.03+random.nextDouble()*4;
            double cameraX=x+(random.nextDouble()*12-6);
            double cameraY=y+(random.nextDouble()*12-6);
            double cameraZ=z+(random.nextDouble()*12-6);
            compare(x,y,z,x+dx,y+dy,z+dz,cameraX,cameraY,cameraZ);
        }
    }
    private static void malformed(){
        Capture c=new Capture();
        ok(!EspFilledBoxGeometry.emitBackToFront(0,0,0,0,1,1,2,3,4,c),
           "Degenerate AABB rejected");
        ok(!EspFilledBoxGeometry.emitBackToFront(0,0,0,1,1,1,
            Double.NaN,3,4,c),"Nonfinite camera rejected");
        ok(!EspFilledBoxGeometry.emitBackToFront(0,0,0,1,1,1,
            Double.MAX_VALUE,0,0,c),"Overflowing camera distances rejected");
        ok(c.count==0,"All invalid-input rejection before partial GL output");
        boolean threw=false;
        try{EspFilledBoxGeometry.emitBackToFront(0,0,0,1,1,1,0,0,0,null);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"Null sink explicitly rejected");
    }
    public static void main(String[] args) {
        randomized();malformed();
        ok(assertions>280000,"Substantial seeded face ordering replay");
        System.out.println("ESP_BLEND_ORDER_PASS assertions="+assertions);
    }
}
