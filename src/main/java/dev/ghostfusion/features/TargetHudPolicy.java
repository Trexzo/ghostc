package dev.ghostfusion.features;

import dev.ghostfusion.services.TargetSelector;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.List;

/** Read-only nearby-player HUD selection, no target-side effect or combat action. */
public final class TargetHudPolicy {
    private final TargetSelector selector = new TargetSelector();
    public WorldSnapshot.Entity select(WorldSnapshot snapshot, double range, boolean visibleOnly) {
        if (snapshot == null) return null;
        List<WorldSnapshot.Entity> eligible = selector.select(snapshot,
            new TargetSelector.Query(range, visibleOnly, true, TargetSelector.Order.DISTANCE));
        return eligible.isEmpty() ? null : eligible.get(0);
    }
}
