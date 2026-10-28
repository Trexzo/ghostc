package dev.ghostfusion.features;
/**
 * Client-thread-only, allocation-free Nebula animation. Never caches player
 * metadata or paints removed targets. Reset when the Forge world changes.
 */
public final class TargetHudMotion {
    private Object identity;
    private long lastNanos;
    private boolean sampled;
    private double health, opacity=1.0;
    public void reset(){identity=null;sampled=false;health=0;opacity=1.0;lastNanos=0;}
    private static double unit(double v) {
        return Double.isFinite(v)?Math.max(0,Math.min(1,v)):0;
    }
    public void update(Object target,long now,double healthFraction,boolean animate) {
        if(target==null)throw new IllegalArgumentException("target");
        double value=unit(healthFraction);
        if(!animate){identity=target;health=value;opacity=1;lastNanos=now;sampled=true;return;}
        if(!sampled || identity!=target) {
            identity=target;health=value;opacity=0.4;lastNanos=now;sampled=true;return;
        }
        long delta=now-lastNanos;
        if(delta<0)delta=0;
        delta=Math.min(delta,250_000_000L);
        lastNanos=now;
        health=unit(health+(value-health)*Math.min(1,(double)delta/170_000_000.0));
        opacity=unit(opacity+(double)delta/210_000_000.0);
    }
    public double health(){return health;}
    public double opacity(){return opacity;}
    public static int fade(int argb,double fade) {
        int alpha=(int)Math.round(((argb>>>24)&255)*unit(fade));
        return (alpha<<24)|(argb&0xFFFFFF);
    }
}
