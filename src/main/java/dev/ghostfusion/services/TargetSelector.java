package dev.ghostfusion.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deterministic, read-only target selection; explicit eligibility and stable ID tie-breaks. */
public final class TargetSelector {
    public enum Order { DISTANCE, HEALTH, ENTITY_ID }
    public static final class Query {
        public final double maxDistance;
        public final boolean visibleOnly, playersOnly;
        public final Order order;
        public Query(double maxDistance, boolean visibleOnly, boolean playersOnly, Order order) {
            if (!WorldSnapshot.finite(maxDistance) || maxDistance <= 0 || order == null)
                throw new IllegalArgumentException("query");
            this.maxDistance = maxDistance;
            this.visibleOnly = visibleOnly;
            this.playersOnly = playersOnly;
            this.order = order;
        }
    }
    public List<WorldSnapshot.Entity> select(final WorldSnapshot world, final Query query) {
        if (world == null || query == null) throw new IllegalArgumentException("world/query");
        final double maxSquare = query.maxDistance * query.maxDistance;
        ArrayList<WorldSnapshot.Entity> selected = new ArrayList<WorldSnapshot.Entity>();
        for (WorldSnapshot.Entity e : world.entities()) {
            if (!e.alive || e.health == 0 || (query.visibleOnly && !e.visible) ||
                (query.playersOnly && !e.player)) continue;
            double distance = squareDistance(world, e);
            if (WorldSnapshot.finite(distance) && distance <= maxSquare) selected.add(e);
        }
        Collections.sort(selected, new Comparator<WorldSnapshot.Entity>() {
            public int compare(WorldSnapshot.Entity a, WorldSnapshot.Entity b) {
                int c = 0;
                if (query.order == Order.DISTANCE)
                    c = Double.compare(squareDistance(world, a), squareDistance(world, b));
                else if (query.order == Order.HEALTH) c = Double.compare(a.health, b.health);
                return c != 0 ? c : Integer.compare(a.id, b.id);
            }
        });
        return Collections.unmodifiableList(selected);
    }
    private static double squareDistance(WorldSnapshot w, WorldSnapshot.Entity e) {
        double x = e.x-w.playerX, y = e.y-w.playerY, z = e.z-w.playerZ;
        return x*x + y*y + z*z;
    }
}
