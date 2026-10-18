package dev.ghostfusion.host;

import dev.ghostfusion.core.BoundedIngress;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.InputRouter;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ProfileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minecraft-independent adapter boundary.
 * All lifecycle/render/key callbacks must happen on the creating client thread;
 * producers on other threads may enqueue immutable GameEvents, never mutate modules.
 */
public final class HostRuntime {
    /** LWJGL Keyboard.KEY_F8 in Minecraft 1.8.9; bindable and persisted. */
    public static final int DEFAULT_STATUS_KEY = 66;
    private static final int MAX_EVENTS_PER_TICK = 128;
    private final ModuleRegistry modules = new ModuleRegistry();
    private final StatusOverlayModule overlay = new StatusOverlayModule();
    private final InputRouter input = new InputRouter(modules);
    private final BoundedIngress ingress = new BoundedIngress(256);
    private final AtomicLong sequence = new AtomicLong();
    private final ProfileStore profiles;
    private volatile boolean closed;
    private boolean worldPresent;
    private String profileWarning = "";

    public HostRuntime(Path configDirectory) {
        if (configDirectory == null) throw new IllegalArgumentException("configDirectory");
        modules.register(overlay, DEFAULT_STATUS_KEY);
        modules.setEnabled(overlay.id(), true);
        profiles = new ProfileStore(configDirectory, modules);
        if (Files.exists(configDirectory.resolve("default.properties"))) {
            try { profiles.load("default"); }
            catch (Exception e) { profileWarning = "Profile load: " + e.getClass().getSimpleName(); }
        }
    }
    public ModuleRegistry modules() { return modules; }
    public String profileWarning() { return profileWarning; }
    public boolean worldPresent() { return worldPresent; }
    public int pendingEvents() { return ingress.pending(); }
    public long droppedEvents() { return ingress.rejected(); }
    public boolean isClosed() { return closed; }
    private GameEvent event(GameEvent.Kind kind) {
        return new GameEvent(kind, sequence.incrementAndGet());
    }
    private boolean active() {
        modules.assertOwnerThread();
        return !closed;
    }
    /** Can be called from network or worker threads; bounded and non-blocking. */
    public boolean enqueue(GameEvent.Kind kind) {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (closed) return false;
        return ingress.submit(event(kind));
    }
    public void tick() {
        if (!active()) return;
        ingress.drain(modules, MAX_EVENTS_PER_TICK);
        modules.dispatch(event(GameEvent.Kind.CLIENT_TICK));
    }
    public boolean render2d() {
        if (!active() || !worldPresent) return false;
        overlay.beginFrame();
        modules.dispatch(event(GameEvent.Kind.RENDER_2D));
        return overlay.frameRequested();
    }
    public void worldPresence(boolean present) {
        if (!active() || worldPresent == present) return;
        worldPresent = present;
        input.clearPressed();
        modules.dispatch(event(GameEvent.Kind.WORLD_CHANGE));
    }
    public void key(int keycode, boolean pressed, boolean textFieldOrMenuFocused) {
        if (!active()) return;
        ModuleRegistry.State before = modules.get(overlay.id()).state();
        input.keyEvent(keycode, pressed, textFieldOrMenuFocused);
        if (modules.get(overlay.id()).state() != before) persist();
    }
    public void disconnect() {
        if (!active()) return;
        worldPresence(false);
        input.clearPressed();
        modules.dispatch(event(GameEvent.Kind.SESSION_DISCONNECT));
    }
    public void persist() {
        if (!active()) return;
        try { profiles.save("default"); profileWarning = ""; }
        catch (IOException | IllegalArgumentException e) {
            profileWarning = "Profile save: " + e.getClass().getSimpleName();
        }
    }
    /** Call from the owning client thread only. */
    public void close() {
        if (!active()) return;
        persist();
        input.clearPressed();
        modules.shutdown();
        closed = true;
    }
}
