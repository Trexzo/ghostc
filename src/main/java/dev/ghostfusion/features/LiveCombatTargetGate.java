package dev.ghostfusion.features;

/**
 * Final read-only admission for a cached Forge crosshair entity.
 *
 * The renderer's hit result is not authoritative after despawn/respawn.
 * Minecraft's current world must resolve the EXACT SAME object reference,
 * not merely another living entity or a reused numeric entity ID.
 */
public final class LiveCombatTargetGate {
    private LiveCombatTargetGate() {}
    public static boolean eligible(Object cached,Object inCurrentWorld,
                                   Object localPlayer,boolean dead,double health) {
        return cached!=null && cached==inCurrentWorld && cached!=localPlayer &&
            !dead && Double.isFinite(health) && health>0;
    }
}
