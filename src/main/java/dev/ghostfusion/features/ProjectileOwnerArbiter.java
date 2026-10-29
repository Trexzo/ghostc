package dev.ghostfusion.features;

/**
 * Single hotbar transaction arbiter. A transaction in progress remains the
 * owner until restore/cancel, even if the other module becomes enabled.
 * A logically impossible double owner is a CONFLICT and must fail closed.
 */
public final class ProjectileOwnerArbiter {
    public enum Owner { NONE, ROD, SNOWBALL, CONFLICT }
    private ProjectileOwnerArbiter() {}
    public static Owner observed(boolean rodOwned,boolean snowballOwned) {
        if(rodOwned && snowballOwned)return Owner.CONFLICT;
        if(rodOwned)return Owner.ROD;
        if(snowballOwned)return Owner.SNOWBALL;
        return Owner.NONE;
    }
    public static boolean mayStartRod(Owner held,boolean manualOverride) {
        return !manualOverride && (held==Owner.NONE || held==Owner.ROD);
    }
    public static boolean mayStartSnowball(Owner held,boolean manualOverride,
                                            boolean rodClaimedThisTick) {
        return !manualOverride && !rodClaimedThisTick &&
            (held==Owner.NONE || held==Owner.SNOWBALL);
    }
}
