package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspRenderMode;
import dev.ghostfusion.features.EspSkeletonGeometry;
import java.util.Random;

/** Original schematic ESP skeleton policies: no OpenGL or Minecraft required. */
public final class EspSkeletonTests {
    private static int checks;
    private static void ok(boolean value,String label) {
        checks++;
        if(!value)throw new AssertionError(label);
    }
    private static void rejects(Runnable action,String label) {
        boolean rejected=false;
        try {action.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,label);
    }
    private static final class Capture implements EspSkeletonGeometry.Sink {
        int count;
        final double[][] lines=new double[15][6];
        public void line(double x1,double y1,double z1,double x2,double y2,double z2) {
            if(count>=lines.length)throw new AssertionError("Unexpected extra bones");
            double[] values={x1,y1,z1,x2,y2,z2};
            for(int i=0;i<values.length;i++) {
                if(!Double.isFinite(values[i]))throw new AssertionError("Nonfinite bone vertex");
                lines[count][i]=values[i];
            }
            count++;
        }
    }
    private static Capture render(double x,double y,double z,double yaw,double height,
                                  boolean crouch,double phase,double weight) {
        Capture capture=new Capture();
        int emitted=EspSkeletonGeometry.emit(x,y,z,yaw,height,crouch,phase,weight,capture);
        ok(emitted==15&&capture.count==15,"stable 15 bone topology");
        return capture;
    }
    private static void modes() {
        String[] expected={"box","tracer","both","skeleton","box_skeleton"};
        boolean[][] flags={{true,false,false},{false,true,false},{true,true,false},
                           {false,false,true},{true,false,true}};
        ok(EspRenderMode.values().length==expected.length,"no hidden or duplicate modes");
        for(int i=0;i<expected.length;i++) {
            EspRenderMode mode=EspRenderMode.of(expected[i]);
            ok(mode.id.equals(expected[i]),"exact roundtrip "+expected[i]);
            ok(mode.boxes==flags[i][0]&&mode.tracers==flags[i][1]&&
               mode.skeleton==flags[i][2],"explicit mode gates "+expected[i]);
        }
        rejects(()->EspRenderMode.of("unknown"),"unknown mode fails closed");
        rejects(()->EspRenderMode.of(null),"null mode fails closed");
    }
    private static void geometry() {
        Capture straight=render(0,64,0,0,1.8,false,0,0);
        Capture rotated=render(0,64,0,90,1.8,false,0,0);
        Capture crouched=render(0,64,0,0,1.8,true,0,0);
        ok(straight.lines[0][1]>64&&straight.lines[0][4]>straight.lines[0][1],
           "spine extends up");
        ok(crouched.lines[0][4]<straight.lines[0][4],
           "crouch lowers torso");
        ok(Math.abs(rotated.lines[3][2]-straight.lines[3][0])<.000001,
           "shoulders yaw rotate local X into world Z");
        ok(Math.abs(EspSkeletonGeometry.interpolateYaw(179,-179,.5)-180)<.00001,
           "positive wrapped yaw interpolation");
        ok(Math.abs(EspSkeletonGeometry.interpolateYaw(-179,179,.5)+180)<.00001,
           "negative wrapped yaw interpolation");
        rejects(()->EspSkeletonGeometry.interpolateYaw(0,1,Double.NaN),"bad interpolation refused");
        rejects(()->EspSkeletonGeometry.interpolateYaw(0,1,2),"extrapolation refused");
        rejects(()->EspSkeletonGeometry.emit(0,0,0,0,1.8,false,0,0,null),
           "null sink refused");
        rejects(()->EspSkeletonGeometry.emit(0,0,0,0,.01,false,0,0,new Capture()),
           "implausible tiny skeleton refused");
        rejects(()->EspSkeletonGeometry.emit(Double.NaN,0,0,0,1.8,false,0,0,new Capture()),
           "nan position refused");
        rejects(()->EspSkeletonGeometry.emit(0,0,0,Double.POSITIVE_INFINITY,1.8,
           false,0,0,new Capture()),"infinite orientation refused");

        Random random=new Random(20261020L);
        for(int i=0;i<14000;i++) {
            double x=random.nextDouble()*2000-1000;
            double y=random.nextDouble()*260-50;
            double z=random.nextDouble()*2000-1000;
            double yaw=random.nextDouble()*1440-720;
            double height=.5+random.nextDouble()*2.5;
            boolean crouch=random.nextBoolean();
            double phase=random.nextDouble()*80-40,weight=random.nextDouble()*2.0;
            Capture a=render(x,y,z,yaw,height,crouch,phase,weight);
            Capture b=render(x,y,z,yaw+360,height,crouch,phase,weight);
            for(int bone=0;bone<15;bone++) {
                for(int component=0;component<6;component++) {
                    double value=a.lines[bone][component];
                    ok(Math.abs(value-b.lines[bone][component])<.000001,
                       "full-turn yaw parity "+i);
                    if(component==1||component==4) {
                        ok(value>=y-.00001&&value<=y+height+.00001,
                           "skeleton height remains within player bounds "+i);
                    } else if(component==0||component==3) {
                        ok(Math.abs(value-x)<=height+.00001,"bounded world X "+i);
                    } else {
                        ok(Math.abs(value-z)<=height+.00001,"bounded world Z "+i);
                    }
                }
            }
        }
    }
    public static void main(String[] args) {
        modes();geometry();
        ok(checks>100000,"substantial skeleton geometry regression");
        System.out.println("ESP_SKELETON_PASS assertions="+checks);
    }
}
