package dev.ghostfusion.features;

/**
 * Read-only Target HUD final admission. A snapshot identifies a candidate,
 * but the on-screen entity's current state wins when deciding whether and
 * how to render the card. No game classes or writes belong in this policy.
 */
public final class HudLiveTargetGate {
    private HudLiveTargetGate() {}
    public static boolean eligible(Object currentPlayer,Object localPlayer,
                                   boolean dead,double liveHealth,
                                   boolean invisible,boolean visibleOnly) {
        return currentPlayer!=null && currentPlayer!=localPlayer && !dead &&
            Double.isFinite(liveHealth) && liveHealth>0 &&
            (!visibleOnly || !invisible);
    }
    /**
     * A tick-snapshot target can leave range before the overlay is drawn.
     * Re-evaluate against both entities' current coordinates without
     * mutating the target or the saved selection policy.
     */
    public static boolean withinRange(double targetX,double targetY,double targetZ,
                                      double localX,double localY,double localZ,
                                      double maximumRange) {
        if (!Double.isFinite(maximumRange) || maximumRange<=0 ||
            !Double.isFinite(targetX) || !Double.isFinite(targetY) ||
            !Double.isFinite(targetZ) || !Double.isFinite(localX) ||
            !Double.isFinite(localY) || !Double.isFinite(localZ)) return false;
        double dx=targetX-localX,dy=targetY-localY,dz=targetZ-localZ;
        double distanceSq=dx*dx+dy*dy+dz*dz;
        return Double.isFinite(distanceSq) && distanceSq<=maximumRange*maximumRange;
    }
}
