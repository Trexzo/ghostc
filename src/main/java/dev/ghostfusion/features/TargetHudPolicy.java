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
}
