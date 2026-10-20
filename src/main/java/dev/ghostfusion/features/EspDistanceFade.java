package dev.ghostfusion.features;

/**
 * Optional, per-target alpha attenuation from an already validated distance.
 * Zero gameplay/selection effects: visible lines and fill only.
 * Minecraft-independent Java8 policy; no frame/target allocations.
 */
public final class EspDistanceFade {
    private EspDistanceFade(){}
    /**
     * @param distanceSq squared target distance in world units
     * @param range current finite, positive configured ESP distance
     * @param enabled legacy compatibility toggle (defaults to false)
     * @param start fraction of range at which fading starts (0..0.90)
     * @param minimum multiplier at the range boundary (0.05..1)
     */
    public static float factor(double distanceSq,double range,boolean enabled,
                               double start,double minimum) {
        if(!enabled)return 1f;
        if(!Double.isFinite(distanceSq)||distanceSq<0 ||
           !Double.isFinite(range)||range<=0 ||
           !Double.isFinite(start)||start<0||start>0.90 ||
           !Double.isFinite(minimum)||minimum<0.05||minimum>1)
            throw new IllegalArgumentException("Invalid ESP distance fade input");
        // Work with distance fractions to avoid multiplying large world
        // coordinates. Invalid or out-of-range candidates are never admitted
        // by the Forge renderer; clamp solely for numeric robustness.
        double ratio=Math.sqrt(distanceSq)/range;
        if(ratio<=start)return 1f;
        if(ratio>=1)return (float)minimum;
        double t=(ratio-start)/(1-start);
        double value=1-(1-minimum)*t;
        return (float)Math.max(minimum,Math.min(1,value));
    }
}
