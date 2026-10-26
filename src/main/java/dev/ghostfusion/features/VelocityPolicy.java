package dev.ghostfusion.features;

/**
 * Local damage-edge knockback tuning for the Forge 1.8.9 player. This is
 * deliberately NOT an incoming S12 packet hook: only a newly observed native
 * hurt-time edge can authorize one owner-thread local-motion adjustment.
 */
public final class VelocityPolicy {
    public static final class Adjustment {
        public final double x, y, z;
        private Adjustment(double x,double y,double z) {
            this.x=x;this.y=y;this.z=z;
        }
    }
    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1;
    private int previousHurt=-1;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("velocity policy owner thread");
    }
    /**
     * Returns null except when one fresh native hurt edge authorizes a motion
     * change. Default 100% axes never change vanilla motion.
     * The downward Y component is never scaled, preserving gravity/falling.
     */
    public Adjustment sample(boolean active,boolean safePlayer,
                             int hurtTime,double motionX,double motionY,double motionZ,
                             double horizontalPercent,double verticalPercent,
                             long tick,long epoch) {
        checkOwner();
        if(tick<0 || epoch<0 || hurtTime<0 || hurtTime>20 ||
           !Double.isFinite(motionX) || !Double.isFinite(motionY) ||
           !Double.isFinite(motionZ) ||
           !Double.isFinite(horizontalPercent) || !Double.isFinite(verticalPercent) ||
           horizontalPercent<0 || horizontalPercent>100 ||
           verticalPercent<0 || verticalPercent>100)
            throw new IllegalArgumentException("invalid velocity sample");
        if(generation!=epoch) {
            generation=epoch;lastTick=-1;previousHurt=-1;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("nonmonotonic velocity tick");
        lastTick=tick;
        if(!active || !safePlayer) {
            previousHurt=-1;
            return null;
        }
        if(previousHurt<0) {
            // Starting in the middle of a hurt animation is not a new hit.
            previousHurt=hurtTime;
            return null;
        }
        final boolean rising=hurtTime>0 && hurtTime>previousHurt;
        previousHurt=hurtTime;
        if(!rising || (horizontalPercent==100 && verticalPercent==100))
            return null;
        // Restrict operation to plausible single-tick native motion values;
        // invalid or extreme numbers must never be made more extreme.
        if(Math.abs(motionX)>4 || Math.abs(motionY)>4 ||
           Math.abs(motionZ)>4) return null;
        double h=horizontalPercent/100.0,v=verticalPercent/100.0;
        return new Adjustment(motionX*h,
            motionY>0?motionY*v:motionY,
            motionZ*h);
    }
    public void reset() {
        checkOwner();
        generation=-1;lastTick=-1;previousHurt=-1;
    }
}
