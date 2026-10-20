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
    // Reused immutable local-space bones (no allocations per rendered player).
    // Local Z offsets for moving limbs are injected while emitting.
    private static final double[][] BONES={
        {0,.51,0,0,.73,0}, {0,.73,0,0,.84,0}, {0,.84,0,0,.98,0},
        {0,.77,0,-.23,.77,0}, {0,.77,0,.23,.77,0},
        {-.23,.77,0,-.33,.63,-.04},
        {-.33,.63,-.04,-.39,.45,0},
        {.23,.77,0,.33,.63,.04},
        {.33,.63,.04,.39,.45,0},
        {0,.51,0,-.14,.49,0}, {0,.51,0,.14,.49,0},
        {-.14,.49,0,-.16,.26,0}, {-.16,.26,0,-.16,.02,0},
        {.14,.49,0,.16,.26,0}, {.16,.26,0,.16,.02,0}
    };

    /**
     * Emit 15 world-space bone segments. No per-frame joint arrays, and GL
     * state/line batches remain entirely owned by the Forge renderer.
     */
    public static int emit(double x,double y,double z,double yawDegrees,
                           double height,boolean crouching,double limbPhase,
                           double limbWeight,Sink sink) {
        if(sink==null||!valid(x,y,z,yawDegrees,height,limbPhase,limbWeight))
            throw new IllegalArgumentException("Invalid skeleton geometry");
        final double radians=Math.toRadians(yawDegrees);
        final double cos=Math.cos(radians),sin=Math.sin(radians);
        if(!Double.isFinite(cos)||!Double.isFinite(sin))
            throw new IllegalArgumentException("Unrenderable skeleton yaw");
        final double swing=Math.cos(limbPhase*0.6662)*
            Math.min(1.0,Math.max(0.0,limbWeight))*.14;
        for(int i=0;i<BONES.length;i++) {
            double[] bone=BONES[i];
            double z1=bone[2],z2=bone[5];
            // Alternate front/back swing for opposite arms/legs.
            if(i==6||i==11||i==12)z2-=swing;
            else if(i==8||i==13||i==14)z2+=swing;
            if(i==12)z1-=swing;
            else if(i==14)z1+=swing;
            double ax=bone[0]*height, az=z1*height;
            double bx=bone[3]*height, bz=z2*height;
            // Crouching lowers the upper joints more than the feet.
            double ay=height*(bone[1]-(crouching?.12*Math.min(1.0,bone[1]/.5):0));
            double by=height*(bone[4]-(crouching?.12*Math.min(1.0,bone[4]/.5):0));
            sink.line(x+ax*cos-az*sin,y+ay,z+ax*sin+az*cos,
                      x+bx*cos-bz*sin,y+by,z+bx*sin+bz*cos);
        }
        return BONES.length;
    }
}
