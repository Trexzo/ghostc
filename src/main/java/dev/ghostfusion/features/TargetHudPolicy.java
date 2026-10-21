package dev.ghostfusion.features;

import dev.ghostfusion.services.TargetSelector;
import dev.ghostfusion.services.WorldSnapshot;

/** Read-only nearby-player HUD selection, no target-side effect or combat action. */
public final class TargetHudPolicy {
    private final TargetSelector selector = new TargetSelector();
    public WorldSnapshot.Entity select(WorldSnapshot snapshot, double range, boolean visibleOnly) {
        if (snapshot == null) return null;
        return selector.nearest(snapshot,range,visibleOnly,true);
    }

    /**
     * Crosshair is an explicit opt-in target *selection* policy. The Forge
     * adapter must first prove exact live-world identity for the hit object.
     * A crosshair-only HUD never silently falls back to the nearest player.
     * Both policies still use the same tick snapshot and range/visibility gate.
     */
    public WorldSnapshot.Entity select(WorldSnapshot snapshot,double range,
                                       boolean visibleOnly,String mode,
                                       Integer crosshairEntityId) {
        if(!"nearest".equals(mode) && !"crosshair".equals(mode))
            throw new IllegalArgumentException("Target HUD selection mode");
        if("nearest".equals(mode))return select(snapshot,range,visibleOnly);
        if(snapshot==null || crosshairEntityId==null)return null;
        if(!Double.isFinite(range) || range<=0)
            throw new IllegalArgumentException("Target HUD range");
        double limit=range*range;
        for(WorldSnapshot.Entity entity:snapshot.entities()) {
            if(entity.id!=crosshairEntityId.intValue())continue;
            double dx=entity.x-snapshot.playerX;
            double dy=entity.y-snapshot.playerY;
            double dz=entity.z-snapshot.playerZ;
            double sq=dx*dx+dy*dy+dz*dz;
            return TargetSelector.eligible(entity,sq,limit,visibleOnly,true)
                ?entity:null;
        }
        return null;
    }
}
