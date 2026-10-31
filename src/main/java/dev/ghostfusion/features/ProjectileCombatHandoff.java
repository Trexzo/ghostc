package dev.ghostfusion.features;

/**
 * The native hotbar owner at the START of an END tick remains exclusive
 * for the full frame, even if its evaluate() relinquishes ownership early.
 * Newly acquired SELECT/USE/RESTORE ownership is exclusive as well.
 *
 * This is a pure admission guard; it never writes game state or adds a
 * projectile/attack/camera cooldown after the first truly unowned tick.
 */
public final class ProjectileCombatHandoff {
    private ProjectileCombatHandoff() {}

    public static boolean reserved(ProjectileOwnerArbiter.Owner atStart,
                                   boolean rodNow,boolean snowballNow) {
        if(atStart==null)
            throw new IllegalArgumentException("unknown hotbar owner");
        return atStart!=ProjectileOwnerArbiter.Owner.NONE ||
            rodNow || snowballNow;
    }
}
