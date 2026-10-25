package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.UiActions;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.NumericSliderScale;
import dev.ghostfusion.ui.UiSettingsStore;
import dev.ghostfusion.ui.UiState;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/** Headless UI regression tests: model, coordinates, settings, profile/UI persistence. */
public final class UiTests {
    private static int count;
    private static void ok(boolean yes, String what) {
        count++;
        if (!yes) throw new AssertionError(what);
    }
    private static void fails(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (RuntimeException ex) { rejected = true; }
        ok(rejected, "invalid operation rejected");
    }
    private static final class Dummy implements GhostModule {
        final String id, name;
        final Category category;
        int events;
        Dummy(String id, String name, Category category) {
            this.id = id; this.name = name; this.category = category;
        }
        public String id() { return id; }
        public String displayName() { return name; }
        public Category category() { return category; }
        public void onEnable() { }
        public void onDisable() { }
        public void onEvent(GameEvent event) { events++; }
    }
    private static void modelAndActions() {
        ModuleRegistry r = new ModuleRegistry();
        Setting.Bool shadow = new Setting.Bool("shadow", true);
        Setting.Choice style = new Setting.Choice("style", "minimal", "minimal", "modern", "classic");
        Setting.Number opacity = new Setting.Number("opacity", 0.5, 0.25, 0.75);
        r.register(new Dummy("hud.alpha", "Alpha HUD", GhostModule.Category.RENDER), 12, shadow, style, opacity);
        r.register(new Dummy("hud.beta", "Beta", GhostModule.Category.RENDER), 13);
        r.register(new Dummy("combat.gamma", "Gamma", GhostModule.Category.COMBAT), 14);
        UiState ui = new UiState();
        ok(ui.filtered(r).size() == 3, "all modules");
        ui.query("ALPHA");
        ok(ui.filtered(r).size() == 1, "case-fold search");
        ok("hud.alpha".equals(ui.filtered(r).get(0).module().id()), "search matches display name");
        ui.query("combat.g");
        ok(ui.filtered(r).size() == 1, "search matches IDs");
        ui.query("");
        ui.category(GhostModule.Category.RENDER);
        ok(ui.filtered(r).size() == 2, "render category");
        ui.category(GhostModule.Category.WORLD);
        ok(ui.filtered(r).isEmpty(), "empty category");
        ui.category(null);
        ok(ui.filtered(r).size() == 3, "restore all category");
        ui.query("a");
        ok(ui.filtered(r).size() == 3, "multi-match");
        ui.query("nothing");
        ok(ui.filtered(r).isEmpty(), "no-match search");
        fails(() -> ui.query(null));
        ui.query(new String(new char[250]).replace('\0', 'x'));
        ok(ui.query().length() == 100, "query length bounded");
        final int[] persisted = {0};
        UiActions actions = new UiActions(r, () -> persisted[0]++);
        actions.toggle("hud.alpha");
        ok(r.get("hud.alpha").state() == ModuleRegistry.State.ENABLED, "GUI toggle enabled");
        actions.toggle("hud.alpha");
        ok(r.get("hud.alpha").state() == ModuleRegistry.State.DISABLED, "GUI toggle disabled");
        actions.bool("hud.alpha", "shadow");
        ok(!shadow.get(), "boolean toggled");
        actions.cycle("hud.alpha", "style");
        ok("modern".equals(style.get()), "choice forward");
        actions.cycle("hud.alpha", "style");
        ok("classic".equals(style.get()), "choice cycles");
        actions.cycle("hud.alpha", "style");
        ok("minimal".equals(style.get()), "choice wraps");
        ok(style.options().size() == 3, "choice options readable");
        fails(() -> style.options().add("injected"));
        actions.fraction("hud.alpha", "opacity", -10);
        ok(opacity.get() == 0.25, "number clamped at minimum");
        actions.fraction("hud.alpha", "opacity", 100);
        ok(opacity.get() == 0.75, "number clamped at maximum");
        actions.fraction("hud.alpha", "opacity", 0.5);
        ok(opacity.get() == 0.5, "number midpoint");
        fails(() -> actions.fraction("hud.alpha", "opacity", Double.NaN));
        fails(() -> actions.bool("hud.alpha", "style"));
        fails(() -> actions.cycle("hud.alpha", "shadow"));
        fails(() -> actions.toggle("unknown"));
        actions.bind("hud.alpha", 0);
        ok(r.get("hud.alpha").keybind() == 0, "key unbound");
        fails(() -> actions.bind("hud.alpha", 13));
        actions.bind("hud.alpha", 55);
        ok(r.get("hud.alpha").keybind() == 55, "key rebound");
        ok(persisted[0] == 11, "only successful actions persisted");
        r.shutdown();
    }
    /** A normalized numeric no-op must not write a profile file. */
    private static void numericNoopWrites() {
        ModuleRegistry r=new ModuleRegistry();
        Setting.Number amount=new Setting.Number("amount",50.0,0.0,100.0);
        Setting.Number fixed=new Setting.Number("fixed",7.0,7.0,7.0);
        r.register(new Dummy("hud.numeric","Numeric HUD",GhostModule.Category.RENDER),
            0,amount,fixed);
        final int[] saves={0};
        UiActions actions=new UiActions(r,()->saves[0]++);
        actions.fraction("hud.numeric","amount",0.5);
        ok(saves[0]==0 && amount.get()==50.0,
            "fraction selecting current numeric value does not persist");
        actions.fraction("hud.numeric","amount",1.0);
        ok(saves[0]==1 && amount.get()==100.0,
            "changed fraction commits exactly once");
        actions.fraction("hud.numeric","amount",1.0);
        actions.fraction("hud.numeric","amount",999.0);
        ok(saves[0]==1,
            "repeated and clamped endpoint fractions do not write");
        actions.fraction("hud.numeric","amount",0.0);
        ok(saves[0]==2 && amount.get()==0.0,
            "moving away from endpoint persists once");
        actions.fraction("hud.numeric","fixed",0.0);
        actions.fraction("hud.numeric","fixed",1.0);
        ok(saves[0]==2 && fixed.get()==7.0,
            "constant numeric range never triggers profile writes");
        fails(()->actions.fraction("hud.numeric","amount",Double.NaN));
        fails(()->actions.fraction("hud.numeric","unknown",0.5));
        ok(saves[0]==2,"rejected fractions never persist");

        java.util.Random random=new java.util.Random(2722026L);
        int writes=saves[0];
        for(int i=0;i<65000;i++) {
            double fraction=random.nextDouble()*1.6-0.3;
            double next=NumericSliderScale.value(0.0,100.0,fraction);
            boolean change=Double.compare(amount.get(),next)!=0;
            if(change)writes++;
            actions.fraction("hud.numeric","amount",fraction);
            ok(Double.compare(amount.get(),next)==0,
                "numeric fraction maps through validated slider scale "+i);
            ok(saves[0]==writes,
                "numeric profile write count matches actual changes "+i);
        }
        r.shutdown();
    }
    private static void geometry() {
        UiState ui = new UiState();
        int[][] screens = {{320,180}, {427,240}, {854,480}, {1280,720}, {1920,1080}, {3840,2160}};
        for (int[] screen : screens) {
            int width = screen[0], height = screen[1];
            for (int x = -200; x <= width + 200; x += 101) {
                for (int y = -200; y <= height + 200; y += 111) {
                    ui.positionPixels(x,y,width,height,110,20);
                    int px = ui.pixelX(width,110), py = ui.pixelY(height,20);
                    ok(px >= 0 && px <= width-110, "horizontal bounds");
                    ok(py >= 0 && py <= height-20, "vertical bounds");
                }
            }
        }
        ui.position(0, 0);
        ok(ui.pixelX(320,100) == 0 && ui.pixelY(180,20) == 0, "top left anchor");
        ui.position(1,1);
        ok(ui.pixelX(320,100) == 220 && ui.pixelY(180,20) == 160, "bottom right anchor");
        ui.position(-8,8);
        ok(ui.hudX() == 0 && ui.hudY() == 1, "normalization clamps");
        ok(ui.pixelX(20,100) == 0 && ui.pixelY(15,100) == 0, "oversized content safe");
        fails(() -> ui.position(Double.NaN, 0));
        fails(() -> ui.position(0, Double.POSITIVE_INFINITY));
        fails(() -> ui.pixelX(0,1));
        fails(() -> ui.positionPixels(2,2,0,100,1,1));
    }
    private static void preferences() throws Exception {
        UiState ui = new UiState();
        Path dir = Files.createTempDirectory("ghostc-ui-test-");
        UiSettingsStore store = new UiSettingsStore(dir,ui);
        store.load();
        ok(ui.layout() == UiState.Layout.WIDE, "absent profile defaults");
        ui.layout(UiState.Layout.COMPACT);
        ui.theme(UiState.Theme.GRAPHITE);
        ui.position(0.8,0.3);
        store.save();
        ok(Files.exists(dir.resolve("ui.properties")), "saved UI file");
        ui.layout(UiState.Layout.WIDE);
        ui.theme(UiState.Theme.INDIGO);
        ui.position(0.1,0.2);
        store.load();
        ok(ui.layout() == UiState.Layout.COMPACT, "layout roundtrip");
        ok(ui.theme() == UiState.Theme.GRAPHITE, "theme roundtrip");
        ok(ui.hudX() == 0.8 && ui.hudY() == 0.3, "normalized position roundtrip");
        store.save();
        ok(Files.exists(dir.resolve("ui.properties.bak")), "backup emitted");
        Properties bad = new Properties();
        Path file = dir.resolve("ui.properties");
        try(InputStream in = Files.newInputStream(file)) { bad.load(in); }
        bad.setProperty("hud.x", "NaN");
        try(OutputStream out = Files.newOutputStream(file)) { bad.store(out, "tampered"); }
        boolean rejected = false;
        try { store.load(); } catch (IllegalArgumentException e) { rejected = true; }
        ok(rejected, "nonfinite coordinate rejected");
        ok(ui.layout() == UiState.Layout.COMPACT && ui.hudX() == 0.8, "corrupt file no partial mutation");
        Files.copy(dir.resolve("ui.properties.bak"),file,StandardCopyOption.REPLACE_EXISTING);
        try(InputStream in = Files.newInputStream(file)) { bad.load(in); }
        bad.setProperty("theme","WRONG");
        try(OutputStream out = Files.newOutputStream(file)) { bad.store(out,"invalid theme"); }
        rejected = false;
        try { store.load(); } catch (IllegalArgumentException e) { rejected = true; }
        ok(rejected, "unknown theme rejected");
        ok(ui.theme() == UiState.Theme.GRAPHITE, "corrupt theme leaves state");
    }
    /**
     * R3.19: missing legacy layout/theme/status-HUD anchor fields cannot
     * inherit live settings from a previously loaded profile.
     */
    private static void sparsePreferences() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-ui-r319-sparse");
        Path primary=dir.resolve("ui.properties");
        Path backup=dir.resolve("ui.properties.bak");
        UiState ui=new UiState();
        UiSettingsStore store=new UiSettingsStore(dir,ui);
        HudPositions.Widget widget=HudPositions.Widget.CPS;
        for(int i=0;i<1024;i++){
            int mask=i&15;
            Properties p=new Properties();
            p.setProperty("schema","1");
            if((mask&1)!=0)p.setProperty("layout","COMPACT");
            if((mask&2)!=0)p.setProperty("theme","GRAPHITE");
            if((mask&4)!=0)p.setProperty("hud.x","0.71");
            if((mask&8)!=0)p.setProperty("hud.y","0.29");
            if((i&16)!=0)ConfigIntegrity.seal(p);
            try(OutputStream out=Files.newOutputStream(primary)){
                p.store(out,"R3.19 sparse legacy UI");
            }
            // Always mutate the live UI before loading to catch accidental
            // fallbacks to values left by a previous profile.
            ui.layout(UiState.Layout.COMPACT);
            ui.theme(UiState.Theme.GRAPHITE);
            ui.position(.93,.94);
            ui.widgets().position(widget,.33,.44);
            store.load();
            ok(ui.layout()==((mask&1)!=0?
                UiState.Layout.COMPACT:UiState.DEFAULT_LAYOUT),
                "missing/explicit sparse layout "+i);
            ok(ui.theme()==((mask&2)!=0?
                UiState.Theme.GRAPHITE:UiState.DEFAULT_THEME),
                "missing/explicit sparse theme "+i);
            ok(ui.hudX()==((mask&4)!=0?.71:UiState.DEFAULT_HUD_X) &&
               ui.hudY()==((mask&8)!=0?.29:UiState.DEFAULT_HUD_Y),
               "sparse status HUD anchors use immutable defaults "+i);
            ok(ui.widgets().x(widget)==1.0 && ui.widgets().y(widget)==1.0,
               "sparse movable HUD keeps its immutable defaults "+i);
            ok(ConfigIntegrity.verify(ConfigIntegrity.read(primary))==((i&16)!=0),
               "sealed and unsealed sparse loading both accepted "+i);
        }

        String[] badFields={"layout","theme","hud.x","hud.y",
                            "hud.x","hud.y","layout","theme"};
        String[] badValues={"NOT_A_LAYOUT","NO_THEME","NaN",
                            "-0.01","Infinity","1.01",""," "};
        for(int i=0;i<badFields.length*128;i++){
            Properties p=new Properties();
            p.setProperty("schema","1");
            p.setProperty(badFields[i%badFields.length],
                          badValues[i%badValues.length]);
            if((i&1)==0)ConfigIntegrity.seal(p);
            try(OutputStream out=Files.newOutputStream(primary)){
                p.store(out,"R3.19 malformed UI");
            }
            ui.layout(UiState.Layout.COMPACT);
            ui.theme(UiState.Theme.GRAPHITE);
            ui.position(.73,.84);
            ui.widgets().position(widget,.32,.43);
            boolean refused=false;
            try{store.load();}catch(IllegalArgumentException expected){refused=true;}
            ok(refused,"explicitly invalid sparse UI field rejected "+i);
            ok(ui.layout()==UiState.Layout.COMPACT &&
               ui.theme()==UiState.Theme.GRAPHITE &&
               ui.hudX()==.73 && ui.hudY()==.84 &&
               ui.widgets().x(widget)==.32 && ui.widgets().y(widget)==.43,
               "invalid sparse UI never partially applies "+i);
        }

        // Backup admission must accept a sealed, typed-valid sparse snapshot.
        Properties sparseBackup=new Properties();
        sparseBackup.setProperty("schema","1");
        sparseBackup.setProperty("layout","COMPACT");
        sparseBackup.setProperty("hud.x","0.63");
        ConfigIntegrity.seal(sparseBackup);
        try(OutputStream out=Files.newOutputStream(backup)){
            sparseBackup.store(out,"sealed sparse backup");
        }
        Properties malformed=new Properties();
        malformed.setProperty("schema","1");
        malformed.setProperty("theme","NOT_A_THEME");
        ConfigIntegrity.seal(malformed);
        try(OutputStream out=Files.newOutputStream(primary)){
            malformed.store(out,"invalid typed primary");
        }
        byte[] original=Files.readAllBytes(primary);
        ui.layout(UiState.Layout.COMPACT);
        ui.theme(UiState.Theme.GRAPHITE);
        ui.position(.84,.91);
        ok(store.loadRecovering(),"typed-sparse verified backup promoted");
        ok(ui.layout()==UiState.Layout.COMPACT &&
           ui.theme()==UiState.DEFAULT_THEME &&
           ui.hudX()==.63 && ui.hudY()==UiState.DEFAULT_HUD_Y,
           "recovery fills only genuinely absent fields");
        ok(java.util.Arrays.equals(original,Files.readAllBytes(
            dir.resolve("ui.properties.corrupt"))),
           "typed-invalid original retained unchanged after recovery");
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(primary)),
           "promoted sparse backup remains sealed");

        // A real HostRuntime constructor must accept a minimal schema-1 UI.
        Path hostDir=Files.createTempDirectory("ghostc-r319-host-sparse-ui");
        Properties minimal=new Properties();
        minimal.setProperty("schema","1");
        ConfigIntegrity.seal(minimal);
        try(OutputStream out=Files.newOutputStream(hostDir.resolve("ui.properties"))){
            minimal.store(out,"minimal legacy UI startup");
        }
        HostRuntime host=new HostRuntime(hostDir);
        ok(host.ui().layout()==UiState.DEFAULT_LAYOUT &&
           host.ui().theme()==UiState.DEFAULT_THEME &&
           host.ui().hudX()==UiState.DEFAULT_HUD_X &&
           host.ui().hudY()==UiState.DEFAULT_HUD_Y,
           "minimal sparse UI restores correct defaults at real host startup");
        host.close();
    }
    private static void hostIntegration() throws Exception {
        Path dir = Files.createTempDirectory("ghostc-ui-host-");
        HostRuntime host = new HostRuntime(dir);
        ok(host.modules().get("hud.status").module() != null &&
            host.modules().get("hud.metrics").module() != null &&
            host.modules().all().size() >= 2, "original HUD modules retained after new registrations");
        host.ui().layout(UiState.Layout.COMPACT);
        host.ui().theme(UiState.Theme.GRAPHITE);
        host.ui().position(0.43, 0.61);
        host.persistUi();
        host.uiActions().toggle("hud.metrics");
        host.uiActions().bool("hud.metrics","show_coordinates");
        host.uiActions().fraction("hud.status","scale", 1);
        ok(host.statusScale() == 1.75f, "scale configured via GUI actions");
        host.close();
        HostRuntime restored = new HostRuntime(dir);
        ok(restored.ui().layout() == UiState.Layout.COMPACT, "host restores UI layout");
        ok(restored.ui().theme() == UiState.Theme.GRAPHITE, "host restores UI theme");
        ok(restored.ui().hudX() == 0.43 && restored.ui().hudY() == 0.61, "host restores HUD anchor");
        ok(restored.modules().get("hud.metrics").state() == ModuleRegistry.State.ENABLED, "host restores module state");
        ok(restored.showCoordinates(), "host restores module setting");
        ok(restored.statusScale() == 1.75f, "host restores number setting");
        restored.worldPresence(true);
        ok(!restored.metricsFrameRequested(), "metrics frame not requested until render");
        restored.render2d();
        ok(restored.metricsFrameRequested(), "enabled metrics requests frame");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        modelAndActions();
        numericNoopWrites();
        geometry();
        preferences();
        sparsePreferences();
        hostIntegration();
        ok(count > 300, "meaningful UI regression coverage");
        System.out.println("UI_REGRESSION_PASS assertions=" + count);
    }
}
