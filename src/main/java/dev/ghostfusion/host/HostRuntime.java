package dev.ghostfusion.host;

import dev.ghostfusion.core.BoundedIngress;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.InputRouter;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ProfileStore;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.UiSettingsStore;
import dev.ghostfusion.ui.UiActions;
import dev.ghostfusion.services.SessionServices;

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
    private final SessionServices services = new SessionServices();
    private final StatusOverlayModule overlay = new StatusOverlayModule();
    private final MetricsOverlayModule metrics = new MetricsOverlayModule();
    private final Setting.Number statusScale = new Setting.Number("scale", 1.0, 0.75, 1.75);
    private final Setting.Choice statusStyle = new Setting.Choice("style", "detailed", "minimal", "detailed");
    private final Setting.Bool statusShadow = new Setting.Bool("shadow", true);
    private final Setting.Bool showFps = new Setting.Bool("show_fps", true);
    private final Setting.Bool showCoordinates = new Setting.Bool("show_coordinates", false);
    private final UiState ui = new UiState();
    private final UiSettingsStore uiStore;
    private final UiActions uiActions;
    private final InputRouter input = new InputRouter(modules);
    private final BoundedIngress ingress = new BoundedIngress(256);
    private final AtomicLong sequence = new AtomicLong();
    private final ProfileStore profiles;
    private volatile boolean closed;
    private boolean worldPresent;
    private boolean inputBlocked;
    private String profileWarning = "";
    private String uiWarning = "";

    public HostRuntime(Path configDirectory) {
        if (configDirectory == null) throw new IllegalArgumentException("configDirectory");
        modules.register(overlay, DEFAULT_STATUS_KEY, statusScale, statusStyle, statusShadow);
        modules.register(metrics, 0, showFps, showCoordinates);
        modules.setEnabled(overlay.id(), true);
        profiles = new ProfileStore(configDirectory, modules);
        uiStore = new UiSettingsStore(configDirectory, ui);
        uiActions = new UiActions(modules, new Runnable() {
            public void run() { persist(); }
        });
        try { uiStore.load(); }
        catch (Exception e) { uiWarning = "UI preferences: " + e.getClass().getSimpleName(); }
        if (Files.exists(configDirectory.resolve("default.properties"))) {
            try { profiles.load("default"); }
            catch (Exception e) { profileWarning = "Profile load: " + e.getClass().getSimpleName(); }
        }
    }
    public ModuleRegistry modules() { return modules; }
    public SessionServices services() { modules.assertOwnerThread(); return services; }
    public UiState ui() { modules.assertOwnerThread(); return ui; }
    public UiActions uiActions() { modules.assertOwnerThread(); return uiActions; }
    public String uiWarning() { return uiWarning; }
    public float statusScale() { return statusScale.get().floatValue(); }
    public boolean statusShadow() { return statusShadow.get(); }
    public boolean detailedStatus() { return "detailed".equals(statusStyle.get()); }
    public boolean metricsFrameRequested() { return worldPresent && metrics.frameRequested(); }
    public boolean showFps() { return showFps.get(); }
    public boolean showCoordinates() { return showCoordinates.get(); }
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
        services.tickAdvance();
        ingress.drain(modules, MAX_EVENTS_PER_TICK);
        modules.dispatch(event(GameEvent.Kind.CLIENT_TICK));
    }
    public boolean render2d() {
        if (!active() || !worldPresent) return false;
        overlay.beginFrame();
        metrics.beginFrame();
        modules.dispatch(event(GameEvent.Kind.RENDER_2D));
        return overlay.frameRequested();
    }
    public void worldPresence(boolean present) {
        if (!active() || worldPresent == present) return;
        services.world(present);
        worldPresent = present;
        input.clearPressed();
        modules.dispatch(event(GameEvent.Kind.WORLD_CHANGE));
    }
    /** Focus transition resets stale key-down state (GUI, chat, alt-tab). */
    public void inputFocus(boolean blocked) {
        if (!active()) return;
        if (blocked && !inputBlocked) input.clearPressed();
        inputBlocked = blocked;
    }
    public void key(int keycode, boolean pressed, boolean textFieldOrMenuFocused) {
        if (!active()) return;
        inputFocus(textFieldOrMenuFocused);
        ModuleRegistry.State before = modules.get(overlay.id()).state();
        input.keyEvent(keycode, pressed, inputBlocked);
        if (modules.get(overlay.id()).state() != before) persist();
    }
    public void disconnect() {
        if (!active()) return;
        worldPresence(false);
        input.clearPressed();
        services.disconnect();
        modules.dispatch(event(GameEvent.Kind.SESSION_DISCONNECT));
    }
    public void persist() {
        if (!active()) return;
        try { profiles.save("default"); profileWarning = ""; }
        catch (IOException | IllegalArgumentException e) {
            profileWarning = "Profile save: " + e.getClass().getSimpleName();
        }
    }
    public void persistUi() {
        if (!active()) return;
        try { uiStore.save(); uiWarning = ""; }
        catch (IOException | IllegalArgumentException e) {
            uiWarning = "UI save: " + e.getClass().getSimpleName();
        }
    }
    /** Call from the owning client thread only. */
    public void close() {
        if (!active()) return;
        persist();
        persistUi();
        input.clearPressed();
        modules.shutdown();
        services.close();
        closed = true;
    }
}
