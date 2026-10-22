package dev.ghostfusion.features;

/**
 * Final draw-time ESP distance admission. Candidate selection is permitted to
 * use earlier owner-frame data, but paint and fade must use current positions.
 * No Minecraft dependencies, allocations, gameplay writes or LOS rays.
 */
public final class EspLivePaintGate {
    private EspLivePaintGate() {}
    /**
     * Return live squared distance only when the player remains in range.
     * NaN means reject: unknown/invalid positions, overflow, or moved out of
     * range. The configured distance is positive and validated by Settings.
     */
    public static double distanceSquared(double tx,double ty,double tz,
                                         double lx,double ly,double lz,
                                         double maximum) {
        if(!Double.isFinite(tx) || !Double.isFinite(ty) ||
           !Double.isFinite(tz) || !Double.isFinite(lx) ||
           !Double.isFinite(ly) || !Double.isFinite(lz) ||
           !Double.isFinite(maximum) || maximum<=0) return Double.NaN;
        double dx=tx-lx,dy=ty-ly,dz=tz-lz;
        double squared=dx*dx+dy*dy+dz*dz;
        if(!Double.isFinite(squared) || squared>maximum*maximum)
            return Double.NaN;
        return squared;
    }
}
