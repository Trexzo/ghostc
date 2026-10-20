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
import dev.ghostfusion.features.FeatureModule;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;

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
    private final RuntimeDiagnostics diagnostics = new RuntimeDiagnostics();
    private final FramePerformance framePerformance = new FramePerformance();
    private final SnapshotTelemetry snapshotTelemetry = new SnapshotTelemetry();
    private SnapshotTelemetry.Mode lastSnapshotMode=SnapshotTelemetry.Mode.OFF;
    private final FeatureFaultGuard featureFaults = new FeatureFaultGuard(modules, new FeatureFaultGuard.OnFault() {
        public void report(String stableId, String exceptionType) {
            diagnostics.record(Counter.FEATURE_FAULT);
            faultWarning = "Module disabled: " + stableId + " (" + exceptionType + ")";
            persist();
        }
    });
    private final StatusOverlayModule overlay = new StatusOverlayModule();
    private final MetricsOverlayModule metrics = new MetricsOverlayModule();
    private final Setting.Number statusScale = new Setting.Number("scale", 1.0, 0.75, 1.75);
    private final Setting.Choice statusStyle = new Setting.Choice("style", "detailed", "minimal", "detailed");
    private final Setting.Bool statusShadow = new Setting.Bool("shadow", true);
    private final Setting.Bool showFps = new Setting.Bool("show_fps", true);
    private final Setting.Bool showCoordinates = new Setting.Bool("show_coordinates", false);
    private final Setting.Bool keysJump = new Setting.Bool("show_jump", true);
    private final Setting.Bool keysSneak = new Setting.Bool("show_sneak", true);
    private final Setting.Bool cpsRight = new Setting.Bool("show_right", true);
    private final Setting.Number targetRange = new Setting.Number("range", 8.0, 2.0, 24.0);
    private final Setting.Bool targetVisible = new Setting.Bool("visible_only", true);
    private final Setting.Bool targetHealth = new Setting.Bool("show_health", true);
    private final Setting.Number espRange = new Setting.Number("range", 32.0, 2.0, 80.0);
    private final Setting.Bool espVisible = new Setting.Bool("visible_only", true);
    private final Setting.Bool espWalls = new Setting.Bool("through_walls", false);
    private final Setting.Bool espHealth = new Setting.Bool("show_health", false);
    private final Setting.Number espWidth = new Setting.Number("line_width", 1.5, 1.0, 4.0);
    // Normalized independent RGB and alpha controls, not an asset/theme swap.
    // Preserve the exact default original purple (0.62,0.56,1.0,0.82).
    private final Setting.Number espRed = new Setting.Number("red", 0.62, 0.0, 1.0);
    private final Setting.Number espGreen = new Setting.Number("green", 0.56, 0.0, 1.0);
    private final Setting.Number espBlue = new Setting.Number("blue", 1.0, 0.0, 1.0);
    private final Setting.Number espOpacity = new Setting.Number("opacity", 0.82, 0.1, 1.0);
    private final Setting.Choice espStyle = new Setting.Choice("style", "box", "box", "tracer", "both", "skeleton", "box_skeleton");
    private final Setting.Number aimRange = new Setting.Number("range", 4.5, 2.0, 6.0);
    private final Setting.Number aimFov = new Setting.Number("fov", 30.0, 5.0, 90.0);
    private final Setting.Number aimSpeed = new Setting.Number("degrees_per_tick", 4.0, 0.5, 15.0);
    private final Setting.Bool aimVisible = new Setting.Bool("visible_only", true);
    private final Setting.Bool aimRequireClick = new Setting.Bool("require_click", true);
    private final Setting.Number clickRate = new Setting.Number("cps", 10.0, 4.0, 15.0);
    private final Setting.Bool clickEntitiesOnly = new Setting.Bool("entity_only", true);
    private final Setting.Number blockInterval = new Setting.Number("interval_ticks", 4, 2, 10);
    private final Setting.Bool blockRequireTarget = new Setting.Bool("require_target", true);
    private final Setting.Number hitMaxHurt = new Setting.Number("max_hurt_time", 2, 0, 10);
    private final Setting.Number hitGap = new Setting.Number("min_gap_ticks", 2, 1, 8);
    private final Setting.Bool hitRequireTarget = new Setting.Bool("require_target", true);
    private final UiState ui = new UiState();
    private final UiSettingsStore uiStore;
    private final UiActions uiActions;
    private final InputRouter input = new InputRouter(modules);
    private final BoundedIngress ingress = new BoundedIngress(256);
    private final AtomicLong sequence = new AtomicLong();
    private final ProfileStore profiles;
    private final Path configDirectory;
    private boolean disconnectReported = true;
    private volatile boolean closed;
    private boolean worldPresent;
    private boolean inputBlocked;
    private String profileWarning = "";
    private String uiWarning = "";
    private String diagnosticsWarning = "";
    private String faultWarning = "";

    public HostRuntime(Path configDirectory) {
        if (configDirectory == null) throw new IllegalArgumentException("configDirectory");
        this.configDirectory = configDirectory;
        modules.register(overlay, DEFAULT_STATUS_KEY, statusScale, statusStyle, statusShadow);
        modules.register(metrics, 0, showFps, showCoordinates);
        modules.register(new FeatureModule("hud.keystrokes", "Keystrokes", GhostModule.Category.RENDER),
            0, keysJump, keysSneak);
        modules.register(new FeatureModule("hud.cps", "CPS Counter", GhostModule.Category.RENDER),
            0, cpsRight);
        modules.register(new FeatureModule("hud.target", "Target HUD", GhostModule.Category.RENDER),
            0, targetRange, targetVisible, targetHealth);
        modules.register(new FeatureModule("render.esp", "Player ESP", GhostModule.Category.RENDER),
            0, espRange, espVisible, espWalls, espWidth, espStyle, espHealth,
            espRed, espGreen, espBlue, espOpacity);
        modules.register(new FeatureModule("movement.sprint", "Auto Sprint", GhostModule.Category.MOVEMENT),
            0);
        modules.register(new FeatureModule("combat.aimassist", "Aim Assist", GhostModule.Category.COMBAT),
            0, aimRange, aimFov, aimSpeed, aimVisible, aimRequireClick);
        modules.register(new FeatureModule("combat.clicker", "AutoClicker", GhostModule.Category.COMBAT),
            0, clickRate, clickEntitiesOnly);
        modules.register(new FeatureModule("combat.blockhit", "Block Hit", GhostModule.Category.COMBAT),
            0, blockInterval, blockRequireTarget);
        modules.register(new FeatureModule("combat.hitselect", "Hit Select", GhostModule.Category.COMBAT),
            0, hitMaxHurt, hitGap, hitRequireTarget);
        modules.register(new FeatureModule("hud.diagnostics", "Runtime Diagnostics", GhostModule.Category.RENDER),
            0);
        modules.setEnabled(overlay.id(), true);
        profiles = new ProfileStore(configDirectory, modules);
        uiStore = new UiSettingsStore(configDirectory, ui);
        uiActions = new UiActions(modules, new Runnable() {
            public void run() { persist(); }
        });
        try {
            if(uiStore.loadRecovering())
                uiWarning="UI preferences recovered from backup";
        } catch(Exception e) {uiWarning="UI preferences: "+e.getClass().getSimpleName();}
        if (Files.exists(configDirectory.resolve("default.properties")) ||
            Files.exists(configDirectory.resolve("default.properties.bak"))) {
            try {
                if(profiles.loadRecovering("default"))
                    profileWarning="Profile recovered from backup";
            } catch(Exception e) {profileWarning="Profile load: "+e.getClass().getSimpleName();}
        }
    }
    public ModuleRegistry modules() { return modules; }
    public SessionServices services() { modules.assertOwnerThread(); return services; }
    public RuntimeDiagnostics diagnostics() { modules.assertOwnerThread(); return diagnostics; }
    public FramePerformance.Snapshot performanceSnapshot() {
        modules.assertOwnerThread(); return framePerformance.snapshot();
    }
    public SnapshotTelemetry.Snapshot snapshotTelemetry() {
        modules.assertOwnerThread(); return snapshotTelemetry.snapshot();
    }
    public SnapshotTelemetry.Mode lastSnapshotMode(){
        modules.assertOwnerThread();return lastSnapshotMode;
    }
    public SnapshotTelemetry.Snapshot snapshotTelemetry(SnapshotTelemetry.Mode mode){
        modules.assertOwnerThread();return snapshotTelemetry.snapshot(mode);
    }
    /** Tick-side focus fence avoids collecting menu/alt-tab samples. */
    public boolean snapshotProfilingEnabled(){
        return active() && worldPresent && !inputBlocked && diagnosticsEnabled();
    }
    /** Legacy aggregate-only API remains available for core consumers. */
    public void recordSnapshotWork(long elapsedNanos,int scanned,int inRange,
                                   int losQueries,int published) {
        if(!snapshotProfilingEnabled())return;
        snapshotTelemetry.record(elapsedNanos,scanned,inRange,losQueries,published);
    }
    /** Record successful Forge snapshot publication in one demand group. */
    public void recordSnapshotWork(long elapsedNanos,int scanned,int inRange,
                                   int losQueries,int published,
                                   boolean needsVisible,boolean needsUnfiltered) {
        if(!snapshotProfilingEnabled())return;
        SnapshotTelemetry.Mode mode=SnapshotTelemetry.Mode.from(needsVisible,needsUnfiltered);
        snapshotTelemetry.record(elapsedNanos,scanned,inRange,losQueries,published,mode);
        lastSnapshotMode=mode;
    }
    /** Remove cadence reference when opt-in metrics are switched off. */
    public void performancePause() {
        if(active()) framePerformance.suspend();
    }
    /** Only record focused in-world frames with explicit diagnostics opt-in. */
    public void performanceFrame(long nowNanos,long usedHeapBytes,boolean focused) {
        if(!active()) return;
        if(!focused || inputBlocked || !worldPresent || !diagnosticsEnabled()) {
            framePerformance.suspend();
            return;
        }
        framePerformance.frame(nowNanos,usedHeapBytes);
    }
    public String faultWarning() { modules.assertOwnerThread(); return faultWarning; }
    /** Visual/sprint callbacks are isolated per module; a failing module is disabled and persisted. */
    public boolean runFeature(String id, Runnable callback) {
        if (!active() || !worldPresent) return false;
        return featureFaults.execute(id, callback);
    }
    public FeatureFaultGuard featureFaults() { modules.assertOwnerThread(); return featureFaults; }
    public boolean diagnosticsEnabled() {
        modules.assertOwnerThread();
        return modules.get("hud.diagnostics").state() == ModuleRegistry.State.ENABLED;
    }
    public String diagnosticsWarning() { return diagnosticsWarning; }
    public void exportDiagnostics() {
        if (!active() || !diagnosticsEnabled()) return;
        try { diagnostics.saveWithSnapshotModes(configDirectory,framePerformance.snapshot(),snapshotTelemetry); diagnosticsWarning = ""; }
        catch (IOException e) { diagnosticsWarning = "Diagnostics: " + e.getClass().getSimpleName(); }
    }
    public UiState ui() { modules.assertOwnerThread(); return ui; }
    public UiActions uiActions() { modules.assertOwnerThread(); return uiActions; }
    public String uiWarning() { return uiWarning; }
    public float statusScale() { return statusScale.get().floatValue(); }
    public boolean statusShadow() { return statusShadow.get(); }
    public boolean detailedStatus() { return "detailed".equals(statusStyle.get()); }
    public boolean metricsFrameRequested() { return worldPresent && metrics.frameRequested(); }
    public boolean showFps() { return showFps.get(); }
    public boolean showCoordinates() { return showCoordinates.get(); }
    /** Owner-thread read-only settings bridge for Forge features. */
    public boolean featureEnabled(String id) {
        modules.assertOwnerThread();
        return !closed && worldPresent &&
            modules.get(id).state() == ModuleRegistry.State.ENABLED;
    }
    public boolean keysJump() { modules.assertOwnerThread(); return keysJump.get(); }
    public boolean keysSneak() { modules.assertOwnerThread(); return keysSneak.get(); }
    public boolean cpsRight() { modules.assertOwnerThread(); return cpsRight.get(); }
    public double targetRange() { modules.assertOwnerThread(); return targetRange.get(); }
    public boolean targetVisible() { modules.assertOwnerThread(); return targetVisible.get(); }
    public boolean targetHealth() { modules.assertOwnerThread(); return targetHealth.get(); }
    public double espRange() { modules.assertOwnerThread(); return espRange.get(); }
    public boolean espVisible() { modules.assertOwnerThread(); return espVisible.get(); }
    public boolean espThroughWalls() { modules.assertOwnerThread(); return espWalls.get(); }
    public boolean espShowHealth() { modules.assertOwnerThread(); return espHealth.get(); }
    public float espLineWidth() { modules.assertOwnerThread(); return espWidth.get().floatValue(); }
    public dev.ghostfusion.features.EspLineColor espLineColor() {
        modules.assertOwnerThread();
        return dev.ghostfusion.features.EspLineColor.of(
            espRed.get(),espGreen.get(),espBlue.get(),espOpacity.get());
    }
    public String espStyle() { modules.assertOwnerThread(); return espStyle.get(); }
    public double aimRange() { modules.assertOwnerThread(); return aimRange.get(); }
    public double aimFov() { modules.assertOwnerThread(); return aimFov.get(); }
    public double aimDegreesPerTick() { modules.assertOwnerThread(); return aimSpeed.get(); }
    public boolean aimVisibleOnly() { modules.assertOwnerThread(); return aimVisible.get(); }
    public boolean aimRequireClick() { modules.assertOwnerThread(); return aimRequireClick.get(); }
    public double clickCps() { modules.assertOwnerThread(); return clickRate.get(); }
    public boolean clickEntityOnly() { modules.assertOwnerThread(); return clickEntitiesOnly.get(); }
    public int blockIntervalTicks() { modules.assertOwnerThread(); return (int)Math.round(blockInterval.get()); }
    public boolean blockRequireTarget() { modules.assertOwnerThread(); return blockRequireTarget.get(); }
    public int hitMaxHurtTime() { modules.assertOwnerThread(); return (int)Math.round(hitMaxHurt.get()); }
    public int hitGapTicks() { modules.assertOwnerThread(); return (int)Math.round(hitGap.get()); }
    public boolean hitRequireTarget() { modules.assertOwnerThread(); return hitRequireTarget.get(); }
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
        diagnostics.record(Counter.CLIENT_TICK);
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
        framePerformance.suspend(); // Never include menu/world transition time as a frame.
        diagnostics.record(present ? Counter.WORLD_ENTER : Counter.WORLD_LEAVE);
        if (present) disconnectReported = false;
        worldPresent = present;
        input.clearPressed();
        modules.dispatch(event(GameEvent.Kind.WORLD_CHANGE));
    }
    /** Focus transition resets stale key-down state (GUI, chat, alt-tab). */
    public void inputFocus(boolean blocked) {
        if (!active()) return;
        if (blocked != inputBlocked) {
            diagnostics.record(blocked ? Counter.FOCUS_LOST : Counter.FOCUS_GAINED);
            if (blocked) input.clearPressed();
        }
        // END-tick continues through GUIs even without HUD callbacks.
        if(blocked) framePerformance.suspend();
        inputBlocked = blocked;
    }
    public void key(int keycode, boolean pressed, boolean textFieldOrMenuFocused) {
        if (!active()) return;
        inputFocus(textFieldOrMenuFocused);
        ModuleRegistry.Entry changed = null;
        ModuleRegistry.State before = null;
        if (keycode != 0) for (ModuleRegistry.Entry entry : modules.all()) {
            if (entry.keybind() == keycode) { changed = entry; before = entry.state(); break; }
        }
        input.keyEvent(keycode, pressed, inputBlocked);
        if (changed != null && changed.state() != before) persist();
    }
    /** Idempotent even when WorldEvent leaves before network-disconnect arrives. */
    public void disconnect() {
        if (!active() || disconnectReported) return;
        worldPresence(false);
        disconnectReported = true;
        input.clearPressed();
        services.disconnect();
        diagnostics.record(Counter.DISCONNECT);
        modules.dispatch(event(GameEvent.Kind.SESSION_DISCONNECT));
        exportDiagnostics();
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
        exportDiagnostics();
        input.clearPressed();
        modules.shutdown();
        services.close();
        closed = true;
    }
}
