package dev.ghostfusion.features;

import dev.ghostfusion.services.TargetSelector;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.function.IntPredicate;

/** Read-only nearby-player HUD selection, no target-side effect or combat action. */
public final class TargetHudPolicy {
    private final TargetSelector selector = new TargetSelector();
    /**
     * Optional live-world candidate admission for the Target HUD. The
     * predicate is only invoked on snapshot-eligible IDs that could improve
     * the nearest result, avoiding a second list/sort and excess world lookups.
     * It must be owned by the Forge render thread; no Minecraft types enter
     * this headless policy.
     */
    public WorldSnapshot.Entity select(WorldSnapshot snapshot,double range,
                                       boolean visibleOnly,String mode,
                                       Integer crosshairEntityId,IntPredicate allowId) {
        if(allowId==null)throw new IllegalArgumentException("missing target gate");
        if(!"nearest".equals(mode) && !"crosshair".equals(mode))
            throw new IllegalArgumentException("Target HUD selection mode");
        if("crosshair".equals(mode)) {
            WorldSnapshot.Entity hit=select(snapshot,range,visibleOnly,mode,crosshairEntityId);
            return hit!=null && allowId.test(hit.id)?hit:null;
        }
        if(snapshot==null)return null;
        if(!Double.isFinite(range)||range<=0)
            throw new IllegalArgumentException("Target HUD range");
        final double maximum=range*range;
        double nearest=Double.POSITIVE_INFINITY;
        WorldSnapshot.Entity best=null;
        for(WorldSnapshot.Entity candidate:snapshot.entities()) {
            double dx=candidate.x-snapshot.playerX;
            double dy=candidate.y-snapshot.playerY;
            double dz=candidate.z-snapshot.playerZ;
            double squared=dx*dx+dy*dy+dz*dz;
            if(!TargetSelector.eligible(candidate,squared,maximum,visibleOnly,true))
                continue;
            if(best!=null && !(squared<nearest ||
                (squared==nearest && candidate.id<best.id)))continue;
            if(!allowId.test(candidate.id))continue;
            best=candidate;
            nearest=squared;
        }
        return best;
    }

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
