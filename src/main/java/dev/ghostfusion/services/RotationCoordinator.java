package dev.ghostfusion.services;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single rotation arbitration point. Produces intent ONLY, never writes player angles. */
public final class RotationCoordinator {
    public enum Scope { CAMERA, OUTBOUND }
    public static final class Decision {
        public final String owner;
        public final float yaw, pitch;
        public final Scope scope;
        private Decision(String owner, float yaw, float pitch, Scope scope) {
            this.owner = owner; this.yaw = yaw; this.pitch = pitch; this.scope = scope;
        }
    }
    private static final class Request {
        final String owner; final Scope scope;
        final float yaw, pitch; final int priority;
        final long expiry, generation, ordinal;
        Request(String owner, Scope scope, float yaw, float pitch, int priority,
                long expiry, long generation, long ordinal) {
            this.owner=owner; this.scope=scope; this.yaw=yaw; this.pitch=pitch;
            this.priority=priority; this.expiry=expiry;
            this.generation=generation; this.ordinal=ordinal;
        }
    }
    private final LinkedHashMap<String, Request> requests = new LinkedHashMap<String, Request>();
    private final Thread ownerThread = Thread.currentThread();
    private long nextOrdinal;
    private boolean stopped;
    public void assertOwner() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("rotation owner thread");
        if (stopped) throw new IllegalStateException("rotation stopped");
    }
    public void request(String owner, Scope scope, float yaw, float pitch, int priority,
                        long now, int lifetimeTicks, long generation) {
        assertOwner(); ServiceValidation.request(owner, now, lifetimeTicks, generation);
        if (scope == null || !WorldSnapshot.finite(yaw) || !WorldSnapshot.finite(pitch) ||
            pitch < -90 || pitch > 90 || lifetimeTicks > 200) throw new IllegalArgumentException("rotation");
        requests.put(owner, new Request(owner, scope, yaw, pitch, priority,
            now + lifetimeTicks, generation, ++nextOrdinal));
    }
    public void cancel(String owner) {
        assertOwner(); ServiceValidation.owner(owner); requests.remove(owner);
    }
    public Decision decide(Scope scope, long tick, long generation, float actualYaw, float actualPitch) {
        assertOwner();
        if (scope == null || tick < 0 || generation < 0 ||
            !WorldSnapshot.finite(actualYaw) || !WorldSnapshot.finite(actualPitch))
            throw new IllegalArgumentException("decision");
        Request best = null;
        for (Request r : requests.values()) {
            if (r.scope != scope || r.generation != generation || tick >= r.expiry) continue;
            if (best == null || r.priority > best.priority ||
                (r.priority == best.priority && r.ordinal < best.ordinal))
                best = r;
        }
        // Fallback always restores the host-provided original camera/packet view.
        return best == null ? new Decision(null, actualYaw, actualPitch, scope) :
            new Decision(best.owner, best.yaw, best.pitch, scope);
    }
    public int pending() { assertOwner(); return requests.size(); }
    public void clear() { assertOwner(); requests.clear(); }
    public void shutdown() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("rotation owner thread");
        if (stopped) return;
        requests.clear(); stopped = true;
    }
}
