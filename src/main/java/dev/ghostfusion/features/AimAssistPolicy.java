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
        private final double targetX,targetY,targetZ;
        private final double sourceX,sourceY,sourceZ,sourceEyeHeight;
        private final float sourceYaw,sourcePitch;
        Suggestion(WorldSnapshot world,WorldSnapshot.Entity target,
                   double eyeHeight,float yaw,float pitch,double angularError) {
            this.entityId=target.id;this.yaw=yaw;this.pitch=pitch;
            this.angularError=angularError;
            this.targetX=target.x;this.targetY=target.y;this.targetZ=target.z;
            this.sourceX=world.playerX;this.sourceY=world.playerY;
            this.sourceZ=world.playerZ;
            this.sourceYaw=world.playerYaw;this.sourcePitch=world.playerPitch;
            this.sourceEyeHeight=eyeHeight;
        }
        /** The desired angles came from this exact tick's target pose.
         * A position change before the camera write invalidates them. */
        public boolean sameTargetPosition(double x,double y,double z) {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                x==targetX && y==targetY && z==targetZ;
        }
        /** A local move, camera turn, or eye-height change after the tick
         * snapshot makes this absolute yaw/pitch suggestion stale. A strict
         * same-pose fence prevents overwriting a newer camera viewpoint. */
        public boolean sameSourcePose(double x,double y,double z,
                                      float yaw,float pitch,double eyeHeight) {
            return Double.isFinite(x) && Double.isFinite(y) &&
                Double.isFinite(z) && Float.isFinite(yaw) &&
                Float.isFinite(pitch) && Double.isFinite(eyeHeight) &&
                x==sourceX && y==sourceY && z==sourceZ &&
                yaw==sourceYaw && pitch==sourcePitch &&
                eyeHeight==sourceEyeHeight;
        }
    }
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
        return suggest(world, sourceEyeHeight, targetHeight, range, fov,
            maximumDegreesPerTick, visibleOnly, 1.0, 1.0);
    }

    /**
     * Independently controls yaw/pitch participation in the already bounded
     * camera step. Fractions cannot overshoot the intended target angle.
     * The default 1.0/1.0 exactly retains the original selection/geometry.
     */
    public Suggestion suggest(WorldSnapshot world, double sourceEyeHeight,
                              double targetHeight, double range, double fov,
                              double maximumDegreesPerTick, boolean visibleOnly,
                              double horizontalFraction, double verticalFraction) {
        if (!finite(horizontalFraction) || !finite(verticalFraction) ||
            horizontalFraction <= 0 || horizontalFraction > 1.0 ||
            verticalFraction <= 0 || verticalFraction > 1.0)
            throw new IllegalArgumentException("aim axis fractions");
        if (world == null || !finite(sourceEyeHeight) || sourceEyeHeight < 0 ||
            !finite(targetHeight) || targetHeight < 0 || !finite(range) || range<=0 ||
            !finite(fov) || fov<=0 || fov>180 || !finite(maximumDegreesPerTick) ||
            maximumDegreesPerTick<=0 || maximumDegreesPerTick>180)
            throw new IllegalArgumentException("aim settings");
        WorldSnapshot.Entity best=null;
        double bestError=Double.POSITIVE_INFINITY, bestDistance=Double.POSITIVE_INFINITY;
        double bestYaw=0,bestPitch=0;
        // Evaluate target policy directly: sorting and materializing a list
        // of as many as 512 candidates each client tick is unnecessary.
        final double maximumSquare=range*range;
        for (WorldSnapshot.Entity e:world.entities()) {
            double dx=e.x-world.playerX, dz=e.z-world.playerZ;
            double vertical=e.y-world.playerY;
            // Preserve the original selector's x+y+z sum order at range edges.
            double distanceSq=dx*dx+vertical*vertical+dz*dz;
            if(!TargetSelector.eligible(e,distanceSq,maximumSquare,visibleOnly,true))
                continue;
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
        double stepYaw = bestYaw * horizontalFraction;
        double stepPitch = bestPitch * verticalFraction;
        double stepLength = Math.hypot(stepYaw, stepPitch);
        double factor = stepLength <= maximumDegreesPerTick || stepLength == 0 ?
            1.0 : maximumDegreesPerTick / stepLength;
        return new Suggestion(world,best,sourceEyeHeight,
            (float)(world.playerYaw+stepYaw*factor),
            clampPitch(world.playerPitch+stepPitch*factor), bestError);
    }
}
