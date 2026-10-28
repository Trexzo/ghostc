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
    // Created on the Host owner thread; values contain no game identifiers.
    private final ModuleActionTelemetry actionTelemetry =
        new ModuleActionTelemetry();
    private final FramePerformance framePerformance = new FramePerformance();
    private final SnapshotTelemetry snapshotTelemetry = new SnapshotTelemetry();
    private SnapshotTelemetry.Mode lastSnapshotMode=SnapshotTelemetry.Mode.OFF;
    private final FeatureFaultGuard featureFaults = new FeatureFaultGuard(modules, new FeatureFaultGuard.OnFault() {
        public void report(String stableId, String exceptionType) {
            diagnostics.record(Counter.FEATURE_FAULT);
            // The guard owns the warning lifecycle. It automatically
            // disappears after a successful retry of this feature.
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
    private final Setting.Bool showPing = new Setting.Bool("show_ping", false);
    private final Setting.Bool keysJump = new Setting.Bool("show_jump", true);
    private final Setting.Bool keysSneak = new Setting.Bool("show_sneak", true);
    private final Setting.Bool cpsRight = new Setting.Bool("show_right", true);
    // Visual track from PR #164; classic behavior remains the default.
    private final Setting.Choice keysAppearance =
        new Setting.Choice("appearance","classic","classic","minimal","nebula");
    private final Setting.Choice cpsAppearance =
        new Setting.Choice("appearance","classic","classic","minimal","nebula");
    private final Setting.Bool targetMotion = new Setting.Bool("motion", true);
    private final Setting.Number targetRange = new Setting.Number("range", 8.0, 2.0, 24.0);
    private final Setting.Bool targetVisible = new Setting.Bool("visible_only", true);
    private final Setting.Bool targetHealth = new Setting.Bool("show_health", true);
    // Off by default: old HUD visuals and profiles are unchanged.
    private final Setting.Bool targetDistance = new Setting.Bool("show_distance", false);
    // Opt-in independent HUD policy; ESP and combat targeting remain unchanged.
    private final Setting.Bool targetIgnoreTeammates =
        new Setting.Bool("ignore_teammates", false);
    // Existing profiles keep Nearest; Crosshair draws only the entity under the reticle.
    private final Setting.Choice targetSelection =
        new Setting.Choice("selection","nearest","nearest","crosshair");
    // Opt-in alternative palette; old profiles remain pixel-identical classic.
    private final Setting.Choice targetSkin =
        new Setting.Choice("skin","classic","classic","glass","minimal","nebula");
    // Old profiles preserve values; percent is an optional narrower readout.
    private final Setting.Choice targetHealthText =
        new Setting.Choice("health_text","values","values","percent");
    private final Setting.Number espRange = new Setting.Number("range", 32.0, 2.0, 80.0);
    private final Setting.Bool espVisible = new Setting.Bool("visible_only", true);
    // Default false preserves old-world player ESP colors, LOS and membership.
    private final Setting.Bool espIgnoreTeammates = new Setting.Bool("ignore_teammates", false);
    private final Setting.Bool espWalls = new Setting.Bool("through_walls", false);
    private final Setting.Bool espHealth = new Setting.Bool("show_health", false);
    private final Setting.Choice espHealthSide =
        new Setting.Choice("health_side", "right", "right", "left");
    private final Setting.Number espHealthGap =
        new Setting.Number("health_gap", 0.18, 0.05, 0.50);
    private final Setting.Number espWidth = new Setting.Number("line_width", 1.5, 1.0, 4.0);
    // Drawing budget only: detection, visibility, and nearest-80 membership
    // remain unchanged. Old profiles draw the same maximum of 80 players.
    private final Setting.Number espDrawLimit =
        new Setting.Number("max_players", 80.0, 1.0, 80.0);
    private final Setting.Number espCornerLength =
        new Setting.Number("corner_length", 0.26, 0.10, 0.50);
    // Independent translucent surface opacity. Existing line alpha is not changed.
    private final Setting.Number espFillOpacity =
        new Setting.Number("fill_opacity", 0.18, 0.04, 0.50);
    // Disabled by default: preserve existing pixel opacity for old profiles.
    private final Setting.Bool espDistanceFade = new Setting.Bool("distance_fade", false);
    private final Setting.Number espFadeStart =
        new Setting.Number("fade_start", 0.50, 0.0, 0.90);
    private final Setting.Number espFadeMinimum =
        new Setting.Number("fade_min_opacity", 0.25, 0.05, 1.0);
    // Normalized independent RGB and alpha controls, not an asset/theme swap.
    // Preserve the exact default original purple (0.62,0.56,1.0,0.82).
    private final Setting.Number espRed = new Setting.Number("red", 0.62, 0.0, 1.0);
    private final Setting.Number espGreen = new Setting.Number("green", 0.56, 0.0, 1.0);
    private final Setting.Number espBlue = new Setting.Number("blue", 1.0, 0.0, 1.0);
    private final Setting.Number espOpacity = new Setting.Number("opacity", 0.82, 0.1, 1.0);
    private final Setting.Choice espStyle = new Setting.Choice("style", "box", "box", "tracer", "both", "skeleton", "box_skeleton",
        "corner_box", "corner_skeleton", "filled_box", "filled_skeleton");
    private final Setting.Number aimRange = new Setting.Number("range", 4.5, 2.0, 6.0);
    private final Setting.Number aimFov = new Setting.Number("fov", 30.0, 5.0, 90.0);
    private final Setting.Number aimSpeed = new Setting.Number("degrees_per_tick", 4.0, 0.5, 15.0);
    private final Setting.Bool aimVisible = new Setting.Bool("visible_only", true);
    private final Setting.Bool aimRequireClick = new Setting.Bool("require_click", true);
    // Default fractions preserve the original combined angular-speed policy.
    private final Setting.Number aimHorizontal = new Setting.Number("horizontal_fraction", 1.0, 0.1, 1.0);
    private final Setting.Number aimVertical = new Setting.Number("vertical_fraction", 1.0, 0.1, 1.0);
    private final Setting.Number aimTargetHeight = new Setting.Number("target_height", 1.25, 0.4, 1.7);
    private final Setting.Bool aimLockTarget = new Setting.Bool("lock_target", false);
    // Optional switching stability; zero exactly preserves the old instant choice.
    private final Setting.Number aimSwitchDelay = new Setting.Number("switch_delay_ticks",0.0,0.0,10.0);
    // Entirely opt-in real mouse-movement priority above synthetic rotation.
    private final Setting.Bool aimRespectMouse = new Setting.Bool("respect_mouse",false);
    private final Setting.Number aimMouseThreshold = new Setting.Number("mouse_delta_threshold",3,1,32);
    private final Setting.Number aimMousePause = new Setting.Number("mouse_pause_ticks",3,1,10);
    private final Setting.Choice aimPriority = new Setting.Choice(
        "target_priority", "angular", "angular", "distance");
    // Opt-in AutoRod: genuine physical attack edge -> rod use -> conditional restore.
    private final Setting.Number rodCooldown = new Setting.Number(
        "cooldown_ticks",30.0,10.0,100.0);
    private final Setting.Number rodRange = new Setting.Number(
        "range",3.25,2.0,4.5);
    private final Setting.Bool rodSword = new Setting.Bool("require_sword",true);
    private final Setting.Bool rodIgnoreTeam = new Setting.Bool("ignore_teammates",true);
    // Opt-in TriggerBot: player crosshair dwell and bounded native click cadence.
    private final Setting.Number triggerAcquire = new Setting.Number("acquire_ticks",2,1,10);
    private final Setting.Number triggerCooldown = new Setting.Number("cooldown_ticks",4,2,20);
    private final Setting.Number triggerRange = new Setting.Number("range",3.0,2.0,4.5);
    private final Setting.Bool triggerSword = new Setting.Bool("require_sword",true);
    private final Setting.Bool triggerIgnoreTeam = new Setting.Bool("ignore_teammates",true);
    private final Setting.Number clickRate = new Setting.Number("cps", 10.0, 4.0, 15.0);
    private final Setting.Bool clickEntitiesOnly = new Setting.Bool("entity_only", true);
    // Fixed mode is the unchanged legacy default; nonfixed uses a bounded
    // minimum/maximum CPS range with no extra queued bursts.
    private final Setting.Choice clickPattern = new Setting.Choice(
        "pattern", "fixed", "fixed", "balanced", "range");
    private final Setting.Number clickMinCps = new Setting.Number("min_cps", 8.0, 4.0, 15.0);
    private final Setting.Number clickMaxCps = new Setting.Number("max_cps", 12.0, 4.0, 15.0);
    private final Setting.Number blockInterval = new Setting.Number("interval_ticks", 4, 2, 10);
    private final Setting.Bool blockRequireTarget = new Setting.Bool("require_target", true);
    private final Setting.Choice blockMode = new Setting.Choice(
        "mode", "interval", "interval", "post_attack", "on_damage");
    private final Setting.Number hitMaxHurt = new Setting.Number("max_hurt_time", 2, 0, 10);
    private final Setting.Number hitGap = new Setting.Number("min_gap_ticks", 2, 1, 8);
    private final Setting.Bool hitRequireTarget = new Setting.Bool("require_target", true);
    private final Setting.Choice hitMode = new Setting.Choice(
        "mode", "normal", "normal", "ready", "trade");
    private final Setting.Number hitTradeMinSelfHurt = new Setting.Number(
        "trade_min_self_hurt", 1, 1, 10);
    // AutoArmor is an opt-in GuiInventory-only vanilla quick-move feature.
    private final Setting.Number autoArmorOpenDelay = new Setting.Number(
        "open_delay_ticks",6.0,2.0,20.0);
    private final Setting.Number autoArmorClickDelay = new Setting.Number(
        "click_delay_ticks",5.0,2.0,20.0);
    private final Setting.Bool autoArmorUpgrade = new Setting.Bool(
        "upgrade_existing",false);
    // Inventory Manager sorts only in GuiInventory, with no trash/drop actions.
    private final Setting.Number inventoryManagerOpenDelay = new Setting.Number(
        "open_delay_ticks",6.0,2.0,20.0);
    private final Setting.Number inventoryManagerClickDelay = new Setting.Number(
        "click_delay_ticks",5.0,2.0,20.0);
    private final Setting.Bool inventoryManagerUpgrade = new Setting.Bool(
        "upgrade_existing",false);
    // Explicitly opt-in GUI inventory automation: no disguised/custom menus
    // unless allowed and all transfers use native single-flight window clicks.
    private final Setting.Number chestOpenDelay = new Setting.Number(
        "open_delay_ticks",6.0,2.0,20.0);
    private final Setting.Number chestClickDelay = new Setting.Number(
        "click_delay_ticks",4.0,2.0,20.0);
    private final Setting.Bool chestAllowCustom = new Setting.Bool(
        "allow_custom_names",false);
    private final Setting.Number wtapDelay = new Setting.Number(
        "reset_ticks",1.0,1.0,3.0);
    // Native hurt-edge jump only. Opt-in and conservative by default.
    private final Setting.Number jumpMinHurt = new Setting.Number(
        "min_hurt_time",8.0,1.0,10.0);
    private final Setting.Number jumpCooldown = new Setting.Number(
        "cooldown_ticks",12.0,2.0,40.0);
    private final Setting.Bool jumpRequireAttack = new Setting.Bool(
        "require_attack_held",true);
    // Existing hurt-edge mode remains default; packet mode is explicit opt-in.
    private final Setting.Choice velocityMode=new Setting.Choice(
        "mode","hurt","hurt","packet");
    // Damage-edge Velocity tuning is disabled and vanilla 100%/100% by default.
    private final Setting.Number velocityHorizontal = new Setting.Number(
        "horizontal_percent",100.0,0.0,100.0);
    private final Setting.Number velocityVertical = new Setting.Number(
        "vertical_percent",100.0,0.0,100.0);
    // Opt-in, native sneak-key-only Bridge Assist. No packet movement edits.
    private final Setting.Number bridgeLookahead = new Setting.Number(
        "lookahead", 0.75, 0.35, 1.20);
    private final Setting.Number bridgeReleaseDelay = new Setting.Number(
        "release_delay_ticks", 2, 0, 8);
    private final Setting.Bool bridgeRequireBlocks = new Setting.Bool(
        "require_blocks", true);
    private final Setting.Bool bridgeAllowSideways = new Setting.Bool(
        "allow_sideways", false);
    // Visual Module List is a real Forge render feature, not only a GUI preview.
    private final Setting.Choice moduleListPosition = new Setting.Choice("position", "top_right",
        "top_right", "top_left", "bottom_right", "bottom_left", "custom");
    private final Setting.Choice moduleListOrder = new Setting.Choice("sort", "width",
        "width", "alphabetical");
    private final Setting.Number moduleListRows = new Setting.Number("max_rows", 10, 1, 24);
    private final Setting.Bool moduleListRender = new Setting.Bool("show_render", false);
    private final Setting.Bool moduleListBackground = new Setting.Bool("background", true);
    private final Setting.Bool moduleListAccent = new Setting.Bool("accent", true);
    private final Setting.Choice moduleListAppearance =
        new Setting.Choice("appearance","classic","classic","minimal","nebula");
    private final Setting.Bool moduleListCompact = new Setting.Bool("compact",false);
    private final UiState ui = new UiState();
    private final UiSettingsStore uiStore;
    private final UiActions uiActions;
    private final InputRouter input = new InputRouter(modules);
    private final BoundedIngress ingress = new BoundedIngress(256);
    // Producer admission and world-transition sequence fences share one short
    // monitor. Never hold it while invoking module or packet callbacks.
    private final Object ingressEpochLock = new Object();
    private boolean ingressEpochTransition;
    private final AtomicLong sequence = new AtomicLong();
    // Exclusive queue fence: late-published events from an older world are
    // discarded even if their producer reserved a queue slot before a switch.
    private long ingressExpiredThroughSequence;
    private final ProfileStore profiles;
    private final Path configDirectory;
    private boolean disconnectReported = true;
    // One host event batch owns lifecycle authority through ingress drain,
    // direct tick/render and transition/disconnect module callbacks.
    private boolean hostDispatching;
    // Visible to producer threads as well as shutdown callbacks. This is an
    // early denial, not a replacement for BoundedIngress's atomic seal.
    private volatile boolean closing;
    private volatile boolean closed;
    private boolean worldPresent;
    private boolean inputBlocked;
    private String profileWarning = "";
    private String uiWarning = "";
    // A failed startup load must not become an implicit reset when hotkeys,
    // GUI actions or close() call persist. Fence each independent file until
    // the operator repairs/removes it and starts a new session.
    private boolean profileLoadFailed, uiLoadFailed;
    private String diagnosticsWarning = "";
 
    public HostRuntime(Path configDirectory) {
        if (configDirectory == null) throw new IllegalArgumentException("configDirectory");
        this.configDirectory = configDirectory;
        modules.register(overlay, DEFAULT_STATUS_KEY, statusScale, statusStyle, statusShadow);
        modules.register(metrics, 0, showFps, showCoordinates, showPing);
        modules.register(new FeatureModule("hud.keystrokes", "Keystrokes", GhostModule.Category.RENDER),
            0, keysJump, keysSneak, keysAppearance);
        modules.register(new FeatureModule("hud.cps", "CPS Counter", GhostModule.Category.RENDER),
            0, cpsRight, cpsAppearance);
        modules.register(new FeatureModule("hud.target", "Target HUD", GhostModule.Category.RENDER),
            0, targetRange, targetVisible, targetHealth, targetHealthText, targetSkin, targetSelection, targetDistance, targetIgnoreTeammates, targetMotion);
        modules.register(new FeatureModule("render.esp", "Player ESP", GhostModule.Category.RENDER),
            0, espRange, espVisible, espIgnoreTeammates, espWalls, espWidth, espStyle, espHealth,
            espHealthSide, espHealthGap,
            espRed, espGreen, espBlue, espOpacity, espCornerLength, espFillOpacity,
            espDistanceFade, espFadeStart, espFadeMinimum, espDrawLimit);
        modules.register(new FeatureModule("movement.sprint", "Auto Sprint", GhostModule.Category.MOVEMENT),
            0);
        modules.register(new FeatureModule("movement.bridgeassist", "Bridge Assist",
            GhostModule.Category.MOVEMENT), 0,
            bridgeLookahead, bridgeReleaseDelay, bridgeRequireBlocks, bridgeAllowSideways);
        modules.register(new FeatureModule("combat.aimassist", "Aim Assist", GhostModule.Category.COMBAT),
            0, aimRange, aimFov, aimSpeed, aimVisible, aimRequireClick,
            aimHorizontal, aimVertical, aimTargetHeight, aimLockTarget, aimPriority,
            aimSwitchDelay, aimRespectMouse, aimMouseThreshold, aimMousePause);
        modules.register(new FeatureModule("combat.clicker", "AutoClicker", GhostModule.Category.COMBAT),
            0, clickRate, clickEntitiesOnly, clickPattern, clickMinCps, clickMaxCps);
        modules.register(new FeatureModule("combat.triggerbot", "TriggerBot", GhostModule.Category.COMBAT),
            0, triggerAcquire,triggerCooldown,triggerRange,triggerSword,triggerIgnoreTeam);
        modules.register(new FeatureModule("combat.autorod", "AutoRod", GhostModule.Category.COMBAT),
            0, rodCooldown,rodRange,rodSword,rodIgnoreTeam);
        modules.register(new FeatureModule("combat.blockhit", "Block Hit", GhostModule.Category.COMBAT),
            0, blockInterval, blockRequireTarget, blockMode);
        modules.register(new FeatureModule("combat.hitselect", "Hit Select", GhostModule.Category.COMBAT),
            0, hitMaxHurt, hitGap, hitRequireTarget, hitMode, hitTradeMinSelfHurt);
        modules.register(new FeatureModule("combat.velocity", "Velocity", GhostModule.Category.COMBAT),
            0, velocityHorizontal, velocityVertical, velocityMode);
        modules.register(new FeatureModule("combat.wtap", "WTap / Sprint Reset", GhostModule.Category.COMBAT),
            0, wtapDelay);
        modules.register(new FeatureModule("combat.jumpreset", "Jump Reset", GhostModule.Category.COMBAT),
            0, jumpMinHurt, jumpCooldown, jumpRequireAttack);
        modules.register(new FeatureModule("inventory.autoarmor", "AutoArmor",
            GhostModule.Category.UTILITY), 0,
            autoArmorOpenDelay,autoArmorClickDelay,autoArmorUpgrade);
        modules.register(new FeatureModule("inventory.manager", "Inventory Manager",
            GhostModule.Category.UTILITY), 0,
            inventoryManagerOpenDelay,inventoryManagerClickDelay,inventoryManagerUpgrade);
        modules.register(new FeatureModule("inventory.cheststealer", "Chest Stealer",
            GhostModule.Category.UTILITY), 0,
            chestOpenDelay, chestClickDelay, chestAllowCustom);
        modules.register(new FeatureModule("hud.diagnostics", "Runtime Diagnostics", GhostModule.Category.RENDER),
            0);
        modules.register(new FeatureModule("hud.modulelist", "Module List", GhostModule.Category.RENDER),
            0, moduleListPosition, moduleListOrder, moduleListRows,
            moduleListRender, moduleListBackground, moduleListAccent,
            moduleListAppearance, moduleListCompact);
        modules.setEnabled(overlay.id(), true);
        profiles = new ProfileStore(configDirectory, modules);
        uiStore = new UiSettingsStore(configDirectory, ui);
        uiActions = new UiActions(modules, new Runnable() {
            public void run() { persist(); }
        });
        try {
            if(uiStore.loadRecovering())
                uiWarning="UI preferences recovered from backup";
        } catch(Exception e) {
            uiLoadFailed=true;
            uiWarning="UI preferences: "+e.getClass().getSimpleName()+" (save blocked)";
        }
        if (Files.exists(configDirectory.resolve("default.properties")) ||
            Files.exists(configDirectory.resolve("default.properties.bak"))) {
            try {
                if(profiles.loadRecovering("default"))
                    profileWarning="Profile recovered from backup";
            } catch(Exception e) {
                profileLoadFailed=true;
                profileWarning="Profile load: "+e.getClass().getSimpleName()+" (save blocked)";
            }
        }
    }
    public ModuleRegistry modules() { return modules; }
    public SessionServices services() { modules.assertOwnerThread(); return services; }
    public RuntimeDiagnostics diagnostics() { modules.assertOwnerThread(); return diagnostics; }
    /** Native controller outcome, counted only with explicit diagnostics opt-in.
     * Return false for duplicate/missing pending completion, so a skipped
     * server/local callback cannot inflate successes. */
    public boolean nativeAction(ModuleActionTelemetry.Lane lane,
                                ModuleActionTelemetry.Event event) {
        modules.assertOwnerThread();
        if(lane==null||event==null)throw new IllegalArgumentException("native action");
        if(!diagnosticsEnabled())return false;
        if(!actionTelemetry.record(lane,event))return false;
        diagnostics.record(RuntimeDiagnostics.Counter.valueOf(
            "ACTION_"+lane.name()+"_"+event.name()));
        return true;
    }
    public String nativeActionLatest() {
        modules.assertOwnerThread();
        return diagnosticsEnabled()?actionTelemetry.latest():"";
    }
    public long nativeActionCount(ModuleActionTelemetry.Lane lane,
                                  ModuleActionTelemetry.Event event) {
        modules.assertOwnerThread();
        return actionTelemetry.count(lane,event);
    }
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
        // Shutdown callbacks must not add samples after the final
        // diagnostics checkpoint or query live snapshot work.
        return active() && !closing && worldPresent && !inputBlocked &&
            diagnosticsEnabled();
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
        // The close checkpoint is captured before onDisable. Late render
        // callbacks cannot mutate or restart FPS/heap measurement there.
        if(!active() || closing) return;
        if(!focused || inputBlocked || !worldPresent || !diagnosticsEnabled()) {
            framePerformance.suspend();
            return;
        }
        framePerformance.frame(nowNanos,usedHeapBytes);
    }
    public String faultWarning() { modules.assertOwnerThread(); return featureFaults.latestFaultWarning(); }
    /** Visual/sprint callbacks are isolated per module; a failing module is disabled and persisted. */
    public boolean runFeature(String id, Runnable callback) {
        // onDisable callbacks run before closed becomes true. Never execute
        // another feature through the still-enabled part of that snapshot.
        if (!active() || closing || !worldPresent) return false;
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
    public boolean metricsFrameRequested() {
        modules.assertOwnerThread();
        return !closed && !closing && worldPresent && metrics.frameRequested();
    }
    public boolean showFps() { return showFps.get(); }
    public boolean showCoordinates() { return showCoordinates.get(); }
    public boolean showPing() { modules.assertOwnerThread(); return showPing.get(); }
    /** Owner-thread read-only settings bridge for Forge features. */
    public boolean featureEnabled(String id) {
        modules.assertOwnerThread();
        // During onDisable the registry still exposes an ENABLED entry
        // until that callback returns; never advertise a closing feature.
        return !closed && !closing && worldPresent &&
            modules.get(id).state() == ModuleRegistry.State.ENABLED;
    }
    public boolean keysJump() { modules.assertOwnerThread(); return keysJump.get(); }
    public boolean keysSneak() { modules.assertOwnerThread(); return keysSneak.get(); }
    public boolean cpsRight() { modules.assertOwnerThread(); return cpsRight.get(); }
    public String keysAppearance() { modules.assertOwnerThread(); return keysAppearance.get(); }
    public String cpsAppearance() { modules.assertOwnerThread(); return cpsAppearance.get(); }
    public String moduleListPosition() { modules.assertOwnerThread(); return moduleListPosition.get(); }
    public String moduleListOrder() { modules.assertOwnerThread(); return moduleListOrder.get(); }
    public int moduleListMaxRows() { modules.assertOwnerThread(); return moduleListRows.get().intValue(); }
    public boolean moduleListShowRender() { modules.assertOwnerThread(); return moduleListRender.get(); }
    public boolean moduleListBackground() { modules.assertOwnerThread(); return moduleListBackground.get(); }
    public boolean moduleListAccent() { modules.assertOwnerThread(); return moduleListAccent.get(); }
    public String moduleListAppearance() { modules.assertOwnerThread(); return moduleListAppearance.get(); }
    public boolean moduleListCompact() { modules.assertOwnerThread(); return moduleListCompact.get(); }
    public double targetRange() { modules.assertOwnerThread(); return targetRange.get(); }
    public boolean targetVisible() { modules.assertOwnerThread(); return targetVisible.get(); }
    public boolean targetHealth() { modules.assertOwnerThread(); return targetHealth.get(); }
    public boolean targetDistance() { modules.assertOwnerThread(); return targetDistance.get(); }
    public boolean targetIgnoreTeammates() {
        modules.assertOwnerThread(); return targetIgnoreTeammates.get();
    }
    public String targetHealthText() {
        modules.assertOwnerThread(); return targetHealthText.get();
    }
    public boolean targetMotion() { modules.assertOwnerThread(); return targetMotion.get(); }
    public String targetSkin() {
        modules.assertOwnerThread(); return targetSkin.get();
    }
    public String targetSelection() {
        modules.assertOwnerThread(); return targetSelection.get();
    }
    public double espRange() { modules.assertOwnerThread(); return espRange.get(); }
    public boolean espVisible() { modules.assertOwnerThread(); return espVisible.get(); }
    public boolean espIgnoreTeammates() {
        modules.assertOwnerThread(); return espIgnoreTeammates.get();
    }
    public boolean espThroughWalls() { modules.assertOwnerThread(); return espWalls.get(); }
    public boolean espShowHealth() { modules.assertOwnerThread(); return espHealth.get(); }
    public dev.ghostfusion.features.EspHealthBarGeometry.Side espHealthSide(){
        modules.assertOwnerThread();
        return dev.ghostfusion.features.EspHealthBarGeometry.Side.of(espHealthSide.get());
    }
    public double espHealthGap(){
        modules.assertOwnerThread();return espHealthGap.get();
    }
    public float espLineWidth() { modules.assertOwnerThread(); return espWidth.get().floatValue(); }
    public int espMaxPlayers() {
        modules.assertOwnerThread(); return espDrawLimit.get().intValue();
    }
    public double espCornerLength() {
        modules.assertOwnerThread(); return espCornerLength.get();
    }
    public float espFillOpacity() {
        modules.assertOwnerThread(); return espFillOpacity.get().floatValue();
    }
    public boolean espDistanceFade() {
        modules.assertOwnerThread(); return espDistanceFade.get();
    }
    public double espFadeStart() {
        modules.assertOwnerThread(); return espFadeStart.get();
    }
    public double espFadeMinimum() {
        modules.assertOwnerThread(); return espFadeMinimum.get();
    }
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
    public double aimHorizontalFraction() { modules.assertOwnerThread(); return aimHorizontal.get(); }
    public double aimVerticalFraction() { modules.assertOwnerThread(); return aimVertical.get(); }
    public double aimTargetHeight() { modules.assertOwnerThread(); return aimTargetHeight.get(); }
    public boolean aimLockTarget() { modules.assertOwnerThread(); return aimLockTarget.get(); }
    public int aimSwitchDelayTicks() { modules.assertOwnerThread(); return aimSwitchDelay.get().intValue(); }
    public boolean aimRespectMouse() { modules.assertOwnerThread(); return aimRespectMouse.get(); }
    public int aimMouseThreshold() { modules.assertOwnerThread(); return aimMouseThreshold.get().intValue(); }
    public int aimMousePauseTicks() { modules.assertOwnerThread(); return aimMousePause.get().intValue(); }
    public String aimTargetPriority() { modules.assertOwnerThread(); return aimPriority.get(); }
    public int rodCooldownTicks() { modules.assertOwnerThread(); return rodCooldown.get().intValue(); }
    public double rodRange() { modules.assertOwnerThread(); return rodRange.get(); }
    public boolean rodRequireSword() { modules.assertOwnerThread(); return rodSword.get(); }
    public boolean rodIgnoreTeammates() { modules.assertOwnerThread(); return rodIgnoreTeam.get(); }
    public int triggerAcquireTicks() { modules.assertOwnerThread(); return triggerAcquire.get().intValue(); }
    public int triggerCooldownTicks() { modules.assertOwnerThread(); return triggerCooldown.get().intValue(); }
    public double triggerRange() { modules.assertOwnerThread(); return triggerRange.get(); }
    public boolean triggerRequireSword() { modules.assertOwnerThread(); return triggerSword.get(); }
    public boolean triggerIgnoreTeammates() { modules.assertOwnerThread(); return triggerIgnoreTeam.get(); }
    public double clickCps() { modules.assertOwnerThread(); return clickRate.get(); }
    public boolean clickEntityOnly() { modules.assertOwnerThread(); return clickEntitiesOnly.get(); }
    public String clickPattern() { modules.assertOwnerThread(); return clickPattern.get(); }
    public double clickMinCps() { modules.assertOwnerThread(); return clickMinCps.get(); }
    public double clickMaxCps() { modules.assertOwnerThread(); return clickMaxCps.get(); }
    public int blockIntervalTicks() { modules.assertOwnerThread(); return (int)Math.round(blockInterval.get()); }
    public boolean blockRequireTarget() { modules.assertOwnerThread(); return blockRequireTarget.get(); }
    public String blockMode() { modules.assertOwnerThread(); return blockMode.get(); }
    public int hitMaxHurtTime() { modules.assertOwnerThread(); return (int)Math.round(hitMaxHurt.get()); }
    public int hitGapTicks() { modules.assertOwnerThread(); return (int)Math.round(hitGap.get()); }
    public boolean hitRequireTarget() { modules.assertOwnerThread(); return hitRequireTarget.get(); }
    public int autoArmorOpenDelay() {
        modules.assertOwnerThread();return (int)Math.round(autoArmorOpenDelay.get());
    }
    public int autoArmorClickDelay() {
        modules.assertOwnerThread();return (int)Math.round(autoArmorClickDelay.get());
    }
    public boolean autoArmorUpgrade() {
        modules.assertOwnerThread();return autoArmorUpgrade.get();
    }
    public int inventoryManagerOpenDelay() {
        modules.assertOwnerThread();return (int)Math.round(inventoryManagerOpenDelay.get());
    }
    public int inventoryManagerClickDelay() {
        modules.assertOwnerThread();return (int)Math.round(inventoryManagerClickDelay.get());
    }
    public boolean inventoryManagerUpgrade() {
        modules.assertOwnerThread();return inventoryManagerUpgrade.get();
    }
    public int chestStealerOpenDelay() {
        modules.assertOwnerThread();return (int)Math.round(chestOpenDelay.get());
    }
    public int chestStealerClickDelay() {
        modules.assertOwnerThread();return (int)Math.round(chestClickDelay.get());
    }
    public boolean chestStealerAllowCustom() {
        modules.assertOwnerThread();return chestAllowCustom.get();
    }
    public int wtapDelayTicks() {
        modules.assertOwnerThread();return (int)Math.round(wtapDelay.get());
    }
    public int jumpResetMinHurtTime() {
        modules.assertOwnerThread();return jumpMinHurt.get().intValue();
    }
    public int jumpResetCooldownTicks() {
        modules.assertOwnerThread();return jumpCooldown.get().intValue();
    }
    public boolean jumpResetRequireAttackHeld() {
        modules.assertOwnerThread();return jumpRequireAttack.get();
    }
    public String velocityMode() {
        modules.assertOwnerThread();return velocityMode.get();
    }
    public double velocityHorizontalPercent() {
        modules.assertOwnerThread();return velocityHorizontal.get();
    }
    public double velocityVerticalPercent() {
        modules.assertOwnerThread();return velocityVertical.get();
    }
    public double bridgeLookahead() {
        modules.assertOwnerThread();return bridgeLookahead.get();
    }
    public int bridgeReleaseDelayTicks() {
        modules.assertOwnerThread();return (int)Math.round(bridgeReleaseDelay.get());
    }
    public boolean bridgeRequireBlocks() {
        modules.assertOwnerThread();return bridgeRequireBlocks.get();
    }
    public boolean bridgeAllowSideways() {
        modules.assertOwnerThread();return bridgeAllowSideways.get();
    }
    public String hitMode() { modules.assertOwnerThread(); return hitMode.get(); }
    public int hitTradeMinSelfHurt() {
        modules.assertOwnerThread(); return (int)Math.round(hitTradeMinSelfHurt.get());
    }
    public String profileWarning() { return profileWarning; }
    public boolean worldPresent() { return worldPresent; }
    public int pendingEvents() { return ingress.pending(); }
    public long droppedEvents() { return ingress.rejected(); }
    public boolean isClosed() { return closed; }
    /** Reserve consecutive positive event IDs atomically across worker producers.
     * Exhaustion must not wrap into negative sequences: those could silently
     * evade or corrupt the ingress world-transition fence. */
    private long reserveEventIds(int count) {
        if(count<1 || count>2)throw new IllegalArgumentException("event reservation size");
        long observed,next;
        do {
            observed=sequence.get();
            if(observed<0 || observed>Long.MAX_VALUE-count)
                throw new IllegalStateException("host event sequence overflow");
            next=observed+count;
        } while(!sequence.compareAndSet(observed,next));
        return observed;
    }
    private GameEvent event(GameEvent.Kind kind) {
        return new GameEvent(kind,reserveEventIds(1)+1);
    }
    private boolean active() {
        modules.assertOwnerThread();
        return !closed;
    }
    /** Non-owner producers may enqueue ordinary events only. World-change and
     * disconnect notifications must go through their owner-thread lifecycle
     * methods, which atomically rotate the session epoch and ingress fence.
     * Dispatching a queued lifecycle *signal* cannot perform that transition. */
    public boolean enqueue(GameEvent.Kind kind) {
        if (kind != GameEvent.Kind.CLIENT_TICK && kind != GameEvent.Kind.RENDER_2D)
            throw new IllegalArgumentException("owner-only lifecycle event");
        synchronized (ingressEpochLock) {
            // Stamp and publish together. Otherwise a worker can reserve a
            // NEW sequence just after the world fence but before that world
            // finishes transitioning, leaking an old-world event into the
            // next session. Reject during the bounded transition window.
            if (closed || closing || ingressEpochTransition) return false;
            return ingress.submit(event(kind));
        }
    }
    private void beginIngressEpochTransition() {
        synchronized (ingressEpochLock) {
            if (ingressEpochTransition)
                throw new IllegalStateException("Ingress epoch already transitioning");
            ingressEpochTransition = true;
            // Every admitted producer published before this fence. New
            // producer events are refused until the lifecycle completes.
            ingressExpiredThroughSequence = sequence.get();
        }
    }
    private void endIngressEpochTransition() {
        synchronized (ingressEpochLock) {
            ingressEpochTransition = false;
        }
    }
    private void assertHostEventBoundary() {
        if(hostDispatching || closing)
            throw new IllegalStateException("Host lifecycle reentry");
    }
    public void tick() {
        if (!active()) return;
        assertHostEventBoundary();
        // Reserve before advancing the session tick or draining producer work.
        GameEvent tickEvent=event(GameEvent.Kind.CLIENT_TICK);
        hostDispatching=true;
        try {
            services.tickAdvance();
            diagnostics.record(Counter.CLIENT_TICK);
            // The guard covers ALL dequeued producer events as well as the
            // final synchronous tick callback, not just the latter.
            ingress.drainAfter(modules, MAX_EVENTS_PER_TICK, ingressExpiredThroughSequence);
            modules.dispatch(tickEvent);
        } finally {
            hostDispatching=false;
        }
    }
    public boolean render2d() {
        if (!active()) return false;
        assertHostEventBoundary();
        if(!worldPresent)return false;
        GameEvent renderEvent=event(GameEvent.Kind.RENDER_2D);
        hostDispatching=true;
        try {
            overlay.beginFrame();
            metrics.beginFrame();
            modules.dispatch(renderEvent);
            return overlay.frameRequested();
        } finally {
            hostDispatching=false;
        }
    }
    public void worldPresence(boolean present) {
        if (!active()) return;
        assertHostEventBoundary();
        if(worldPresent==present)return;
        GameEvent transition=event(GameEvent.Kind.WORLD_CHANGE);
        beginIngressEpochTransition();
        try {
            applyWorldPresence(present,transition);
        } finally {
            endIngressEpochTransition();
        }
    }
    /** The caller reserves the event before any irreversible session mutation.
     * Disconnect uses this path with an atomically reserved two-event pair. */
    private void applyWorldPresence(boolean present,GameEvent worldEvent) {
        hostDispatching=true;
        try {
            // The ingress epoch fence was captured before this lifecycle
            // callback, while producer publication was atomically embargoed.
            services.world(present);
            framePerformance.suspend();
            diagnostics.record(present ? Counter.WORLD_ENTER : Counter.WORLD_LEAVE);
            if (present) disconnectReported = false;
            worldPresent = present;
            input.clearPressed();
            modules.dispatch(worldEvent);
        } finally {
            hostDispatching=false;
        }
    }
    /** Focus transition resets stale key-down state (GUI, chat, alt-tab). */
    public void inputFocus(boolean blocked) {
        if (!active() || closing) return;
        if (blocked != inputBlocked) {
            diagnostics.record(blocked ? Counter.FOCUS_LOST : Counter.FOCUS_GAINED);
            if (blocked) input.clearPressed();
        }
        // END-tick continues through GUIs even without HUD callbacks.
        if(blocked) framePerformance.suspend();
        inputBlocked = blocked;
    }
    public void key(int keycode, boolean pressed, boolean textFieldOrMenuFocused) {
        if (!active() || closing) return;
        inputFocus(textFieldOrMenuFocused);
        // No fresh full-registry List on every physical key press/release.
        ModuleRegistry.Entry changed = modules.boundToKey(keycode);
        ModuleRegistry.State before = changed == null ? null : changed.state();
        input.keyEvent(keycode, pressed, inputBlocked);
        if (changed != null && changed.state() != before) persist();
    }
    /** Idempotent even when WorldEvent leaves before network-disconnect arrives. */
    public void disconnect() {
        if (!active()) return;
        assertHostEventBoundary();
        if(disconnectReported)return;
        // A connected world needs TWO owner notifications. Reserve both
        // before leaving the world so exhaustion cannot strand a half-
        // completed disconnect between WORLD_CHANGE and SESSION_DISCONNECT.
        boolean leaving=worldPresent;
        long prior=reserveEventIds(leaving?2:1);
        GameEvent worldEvent=leaving?
            new GameEvent(GameEvent.Kind.WORLD_CHANGE,prior+1):null;
        GameEvent disconnectEvent=new GameEvent(GameEvent.Kind.SESSION_DISCONNECT,
            prior+(leaving?2:1));
        // Cover BOTH world-leave and disconnect callbacks with a single
        // ingress embargo; a worker may otherwise publish between them.
        beginIngressEpochTransition();
        try {
            if(leaving)applyWorldPresence(false,worldEvent);
            hostDispatching=true;
            try {
                disconnectReported = true;
                input.clearPressed();
                services.disconnect();
                diagnostics.record(Counter.DISCONNECT);
                modules.dispatch(disconnectEvent);
                exportDiagnostics();
            } finally {
                hostDispatching=false;
            }
        } finally {
            endIngressEpochTransition();
        }
    }
    /** External saves are forbidden while shutdown callbacks execute.
     * close() itself takes one trusted checkpoint before disabling modules. */
    public void persist() {
        if (!active() || closing) return;
        saveProfileFile();
    }
    private void saveProfileFile() {
        // Do not overwrite an unreadable primary with fallback defaults.
        if (profileLoadFailed) return;
        try { profiles.save("default"); profileWarning = ""; }
        catch (IOException | IllegalArgumentException e) {
            profileWarning = "Profile save: " + e.getClass().getSimpleName();
        }
    }
    public void persistUi() {
        if (!active() || closing) return;
        saveUiFile();
    }
    private void saveUiFile() {
        if (uiLoadFailed) return;
        try { uiStore.save(); uiWarning = ""; }
        catch (IOException | IllegalArgumentException e) {
            uiWarning = "UI save: " + e.getClass().getSimpleName();
        }
    }
    /** Call from the owning client thread only. */
    public void close() {
        if (!active()) return;
        assertHostEventBoundary();
        // Abort before ANY mutation when called from an onEnable/onDisable
        // callback. Without this preflight, ingress was sealed and configs
        // were saved before ModuleRegistry.shutdown finally rejected close.
        modules.assertShutdownAllowed();
        closing=true;
        // closed is set only after persistence and module shutdown. Seal the
        // cross-thread producer queue before any onDisable callback executes,
        // so no event can be admitted during the shutdown window.
        ingress.seal();
        try {
            // One final owner checkpoint while modules are still enabled.
            // Do not route this through public persistence: those entry
            // points are fenced from reentrant onDisable callbacks.
            saveProfileFile();
            saveUiFile();
            exportDiagnostics();
            input.clearPressed();
            modules.shutdown();
            services.close();
            closed = true;
        } finally {
            closing=false;
        }
    }
}
