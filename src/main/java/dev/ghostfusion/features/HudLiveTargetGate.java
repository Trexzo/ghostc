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
}
