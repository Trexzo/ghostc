package dev.ghostfusion.features;

/** Pure gate for client-side sprint request. Native input/vanilla logic remains authoritative. */
public final class SprintPolicy {
    private SprintPolicy() { }
    public static boolean shouldSprint(boolean enabled, boolean focused, boolean onGround,
                                       boolean colliding, boolean sneaking, boolean usingItem,
                                       double forward, double hunger, boolean flying, boolean blind,
                                       boolean sprintKeyDown) {
        if (!enabled || !focused || colliding || sneaking || usingItem || blind || flying ||
            !Double.isFinite(forward) || !Double.isFinite(hunger)) return false;
        // Never force sprint while moving backward/sideways or when normal hunger gates prohibit it.
        return forward >= 0.8 && (hunger > 6 || sprintKeyDown) && onGround;
    }
}
