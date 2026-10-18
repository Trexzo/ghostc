package dev.ghostfusion.features;

import dev.ghostfusion.services.TargetSelector;
import dev.ghostfusion.services.WorldSnapshot;

/**
 * Deterministic, bounded, local-camera aim suggestion.
 * Does not perform input, network, rotation writes or anticheat evasion.
 */
public final class AimAssistPolicy {
    public static final class Suggestion {
        public final int entityId;
        public final float yaw, pitch;
        public final double angularError;
        Suggestion(int entityId, float yaw, float pitch, double angularError) {
            this.entityId=entityId; this.yaw=yaw; this.pitch=pitch; this.angularError=angularError;
        }
    }
    private final TargetSelector targets = new TargetSelector();
    private static boolean finite(double number) {
        return !Double.isNaN(number) && !Double.isInfinite(number);
    }
    public static double wrapDegrees(double value) {
        if (!finite(value)) throw new IllegalArgumentException("invalid angle");
        double wrapped = ((value % 360.0) + 540.0) % 360.0 - 180.0;
        return wrapped == 0.0 ? 0.0 : wrapped;
    }
    private static float clampPitch(double pitch) {
        return (float)Math.max(-90.0,Math.min(90.0,pitch));
    }
    public Suggestion suggest(WorldSnapshot world, double sourceEyeHeight,
                              double targetHeight, double range, double fov,
                              double maximumDegreesPerTick, boolean visibleOnly) {
        if (world == null || !finite(sourceEyeHeight) || sourceEyeHeight < 0 ||
            !finite(targetHeight) || targetHeight < 0 || !finite(range) || range<=0 ||
            !finite(fov) || fov<=0 || fov>180 || !finite(maximumDegreesPerTick) ||
            maximumDegreesPerTick<=0 || maximumDegreesPerTick>180)
            throw new IllegalArgumentException("aim settings");
        WorldSnapshot.Entity best=null;
        double bestError=Double.POSITIVE_INFINITY, bestDistance=Double.POSITIVE_INFINITY;
        double bestYaw=0,bestPitch=0;
        for (WorldSnapshot.Entity e:targets.select(world,
             new TargetSelector.Query(range,visibleOnly,true,TargetSelector.Order.DISTANCE))) {
            double dx=e.x-world.playerX, dz=e.z-world.playerZ;
            double horizontal=Math.hypot(dx,dz);
            if (horizontal<0.0001) continue;
            double dy=e.y+targetHeight - (world.playerY+sourceEyeHeight);
            double desiredYaw=Math.toDegrees(Math.atan2(dz,dx))-90.0;
            double desiredPitch=-Math.toDegrees(Math.atan2(dy,horizontal));
            double deltaYaw=wrapDegrees(desiredYaw-world.playerYaw);
            double deltaPitch=desiredPitch-world.playerPitch;
            double error=Math.hypot(deltaYaw,deltaPitch);
            if (error>fov) continue;
            double distance=dx*dx+dy*dy+dz*dz;
            if (best==null || error<bestError-0.000000001 ||
                (Math.abs(error-bestError)<=0.000000001 &&
                (distance<bestDistance-0.000000001 ||
                (Math.abs(distance-bestDistance)<=0.000000001 && e.id<best.id)))) {
                best=e;bestError=error;bestDistance=distance;
                bestYaw=deltaYaw;bestPitch=deltaPitch;
            }
        }
        if (best==null) return null;
        double factor=bestError<=maximumDegreesPerTick || bestError==0 ? 1.0 :
            maximumDegreesPerTick/bestError;
        return new Suggestion(best.id, (float)(world.playerYaw+bestYaw*factor),
            clampPitch(world.playerPitch+bestPitch*factor), bestError);
    }
}
