package dev.ghostfusion.features;

/**
 * Owner-tick handoff between hurt-mode Velocity and Jump Reset.
 *
 * Only a positive native vertical velocity adjustment represents an exclusive
 * damage-motion write. Horizontal-only changes and packet-mode observations
 * cannot reserve Jump Reset's vertical output. Pure local policy: no packets,
 * input writes, entity references, timing cache or server-hit claims.
 */
public final class DamageMotionHandoff {
    private DamageMotionHandoff() { }

    /** True only when the approved hurt-mode adjustment changes local Y. */
    public static boolean verticalChanged(double beforeY,
                                          VelocityPolicy.Adjustment adjustment) {
        return adjustment!=null && adjustment.y!=beforeY;
    }

    /** A real earlier native vertical mutation takes this frame's priority. */
    public static boolean jumpAvailable(boolean granted,
                                        boolean verticalMotionOwned) {
        return granted && !verticalMotionOwned;
    }
}
