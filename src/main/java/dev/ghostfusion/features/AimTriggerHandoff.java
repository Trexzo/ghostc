package dev.ghostfusion.features;

/**
 * Arbitration rule between independent Aim Assist camera output and an already
 * verified, native, visible player under the crosshair.
 *
 * A passive TriggerBot never steals camera ownership. When the player is
 * already physically aiming at a valid TriggerBot target, however, Aim Assist
 * yields its camera write for that tick. TriggerBot keeps its own UUID-based
 * multi-tick acquisition state and the ActionCoordinator remains sole attack
 * arbiter. An ordinary held manual attack retains Aim Assist behavior.
 */
public final class AimTriggerHandoff {
    private AimTriggerHandoff() {}
    public static boolean yieldCamera(boolean triggerEnabled,
                                      boolean attackDown,
                                      boolean useDown,
                                      boolean usingItem,
                                      boolean attackBindingValid,
                                      boolean verifiedNativeCrosshairPlayer) {
        return triggerEnabled && !attackDown && !useDown && !usingItem &&
            attackBindingValid && verifiedNativeCrosshairPlayer;
    }
}
