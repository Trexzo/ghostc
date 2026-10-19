package dev.ghostfusion.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable copy of the minimal world and entity data that a module may inspect. */
public final class WorldSnapshot {
    public static final class Entity {
        public final int id;
        public final double x, y, z, health;
        public final boolean alive, visible, player;
        public Entity(int id, double x, double y, double z, double health,
                      boolean alive, boolean visible, boolean player) {
            if (!finite(x) || !finite(y) || !finite(z) || !finite(health) || health < 0)
                throw new IllegalArgumentException("invalid entity");
            this.id = id; this.x = x; this.y = y; this.z = z;
            this.health = health; this.alive = alive; this.visible = visible; this.player = player;
        }
    }
    public final long generation, tick;
    public final double playerX, playerY, playerZ;
    public final float playerYaw, playerPitch;
    private final List<Entity> entities;
    public WorldSnapshot(long generation, long tick, double playerX, double playerY, double playerZ,
                         float playerYaw, float playerPitch, List<Entity> entities) {
        if (generation < 0 || tick < 0 || !finite(playerX) || !finite(playerY) ||
            !finite(playerZ) || !finite(playerYaw) || !finite(playerPitch) ||
            playerPitch < -90 || playerPitch > 90 || entities == null)
            throw new IllegalArgumentException("invalid snapshot");
        ArrayList<Entity> copy = new ArrayList<Entity>();
        Set<Integer> ids = new HashSet<Integer>();
        if (entities.size() > 4096) throw new IllegalArgumentException("too many entities");
        for (Entity e : entities) {
            if (e == null || !ids.add(e.id)) throw new IllegalArgumentException("null/duplicate entity");
            copy.add(e);
        }
        this.generation = generation; this.tick = tick;
        this.playerX = playerX; this.playerY = playerY; this.playerZ = playerZ;
        this.playerYaw = playerYaw; this.playerPitch = playerPitch;
        this.entities = Collections.unmodifiableList(copy);
    }
    /** Gate the local pose before constructing or publishing any world snapshot. */
    public static boolean validPlayerPose(double x,double y,double z,float yaw,float pitch) {
        return finite(x) && finite(y) && finite(z) &&
            finite(yaw) && finite(pitch) && pitch>=-90f && pitch<=90f;
    }
    public static boolean finitePublic(double v) { return finite(v); }
    static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    public List<Entity> entities() { return entities; }
}
