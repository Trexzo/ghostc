package dev.ghostfusion.features;

/**
 * Original low-allocation player stick-figure geometry. Emission is in world
 * coordinates; yaw rotates local joints around the Y axis. The model is a
 * schematic pose, NOT exact Minecraft model-bone or animation tracking.
 */
public final class EspSkeletonGeometry {
    public interface Sink {
        void line(double x1,double y1,double z1,double x2,double y2,double z2);
    }
    private EspSkeletonGeometry() { }

    public static boolean valid(double x,double y,double z,double yaw,double height,
                                double limbPhase,double limbWeight) {
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z) &&
            Double.isFinite(yaw)&&Double.isFinite(height) &&
            Double.isFinite(limbPhase)&&Double.isFinite(limbWeight) &&
            height>=0.5 && height<=3.0;
    }
    /** Interpolate body yaw along its shortest wrapped arc, including ±180°. */
    public static double interpolateYaw(double previous,double current,double alpha) {
        if(!Double.isFinite(previous)||!Double.isFinite(current)||
            !Double.isFinite(alpha)||alpha<0||alpha>1)
            throw new IllegalArgumentException("Invalid yaw interpolation");
        double delta=((current-previous+540.0)%360.0+360.0)%360.0-180.0;
        return previous+delta*alpha;
    }
    /**
     * A small normalized figure with arms and legs slightly responsive to the
     * native movement cycle; drawing does not allocate a per-joint object.
     */
    public static int emit(double x,double y,double z,double yawDegrees,
                           double height,boolean crouching,double limbPhase,
                           double limbWeight,Sink sink) {
        if(sink==null||!valid(x,y,z,yawDegrees,height,limbPhase,limbWeight))
            throw new IllegalArgumentException("Invalid skeleton geometry");
        final double radians=Math.toRadians(yawDegrees);
        final double cos=Math.cos(radians),sin=Math.sin(radians);
        final double h=height;
        final double lowered=crouching?0.10*h:0.0;
        final double swing=Math.cos(limbPhase*0.6662)*Math.min(1.0,Math.max(0.0,limbWeight))*0.14*h;
        // Skeleton bones: spine/head; shoulders, elbows/hands; hips/knees/feet.
        double[][] segments={
            {0,.51,0,0,.73,0},
            {0,.73,0,0,.84,0},
            {0,.84,0,0,.98,0},
            {0,.77,0,-.23,.77,0},
            {0,.77,0,.23,.77,0},
            {-.23,.77,0,-.33,.63,-.04},
            {-.33,.63,-.04,-.39,.45,-swing/h},
            {.23,.77,0,.33,.63,.04},
            {.33,.63,.04,.39,.45,swing/h},
            {0,.51,0,-.14,.49,0},
            {0,.51,0,.14,.49,0},
            {-.14,.49,0,-.16,.26,-swing/h},
            {-.16,.26,-swing/h,-.16,.02,-swing/h},
            {.14,.49,0,.16,.26,swing/h},
            {.16,.26,swing/h,.16,.02,swing/h}
        };
        for(double[] bone:segments) {
            double a=bone[0]*h,b=bone[1]*h-lowered,c=bone[2]*h;
            double d=bone[3]*h,e=bone[4]*h-lowered,f=bone[5]*h;
            sink.line(x+a*cos-c*sin,y+b,z+a*sin+c*cos,
                      x+d*cos-f*sin,y+e,z+d*sin+f*cos);
        }
        return segments.length;
    }
}
