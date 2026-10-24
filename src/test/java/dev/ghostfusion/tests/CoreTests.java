package dev.ghostfusion.tests;

import dev.ghostfusion.core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

public final class CoreTests {
    private static int assertions = 0;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void fails(Runnable action, String message) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException ex) { threw = true; }
        check(threw, message);
    }
    static final class Fake implements GhostModule {
        final String id;
        int starts, stops, ticks;
        boolean failEnable, failEvent;
        final List<Long> seen = new ArrayList<Long>();
        Fake(String id) { this.id = id; }
        public String id() { return id; }
        public String displayName() { return id; }
        public Category category() { return Category.UTILITY; }
        public void onEnable() { starts++; if (failEnable) throw new IllegalStateException("fake enable exception"); }
        public void onDisable() { stops++; }
        public void onEvent(GameEvent e) {
            if (failEvent) throw new IllegalStateException("fake tick exception");
            ticks++; seen.add(e.sequence());
        }
    }
    private static void testRegistry() {
        ModuleRegistry r = new ModuleRegistry();
        Fake a = new Fake("a"), b = new Fake("b");
        r.register(a, 11);
        r.register(b, 12);
        fails(() -> r.register(new Fake("a"), 13), "Duplicate ID must fail");
        fails(() -> r.register(new Fake("bad name"), 14), "Invalid ID must fail");
        fails(() -> r.bind("b", 11), "Duplicate key must fail");
        InputRouter router = new InputRouter(r);
        router.keyEvent(11, true, true);
        check(r.get("a").state() == ModuleRegistry.State.DISABLED, "chat focus suppressed");
        router.keyEvent(11, true, false);
        check(r.get("a").state() == ModuleRegistry.State.DISABLED, "no spurious toggle on held key");
        router.keyEvent(11, false, false);
        router.keyEvent(11, true, false);
        check(r.get("a").state() == ModuleRegistry.State.ENABLED, "press toggles on");
        router.keyEvent(11, true, false);
        check(a.starts == 1, "autorepeat suppressed");
        router.keyEvent(11, false, false);
        router.keyEvent(11, true, false);
        check(r.get("a").state() == ModuleRegistry.State.DISABLED && a.stops == 1, "press toggles off");
        r.setEnabled("a", true);
        r.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK, 1));
        check(a.ticks == 1, "event sent to enabled module");
        r.setEnabled("b", true);
        b.failEvent = true;
        r.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK, 2));
        check(r.get("b").state() == ModuleRegistry.State.FAULTED, "fault isolation");
        check(a.ticks == 2, "unfaulted module still gets event");
        fails(() -> r.setEnabled("b", true), "faulted enable forbidden");
        r.setEnabled("b", false);
        b.failEvent = false;
        r.setEnabled("b", true);
        check(r.get("b").state() == ModuleRegistry.State.ENABLED, "fault reset works");
        Fake c = new Fake("c"); c.failEnable = true;
        r.register(c, 16); r.setEnabled("c", true);
        check(r.get("c").state() == ModuleRegistry.State.FAULTED, "enable exception isolated");
        r.shutdown();
        check(r.get("a").state() == ModuleRegistry.State.DISABLED, "shutdown disabled");
        fails(() -> r.setEnabled("a", true), "shutdown is final");
    }
    /** R3.05: dynamic bindings match the reference without hot-path copies. */
    private static void testAllocationFreeHotkeyLookup() throws Exception {
        ModuleRegistry r=new ModuleRegistry();
        Fake[] modules=new Fake[64];
        for(int i=0;i<modules.length;i++) {
            modules[i]=new Fake("lookup."+i);
            r.register(modules[i],0);
        }
        check(r.boundToKey(0)==null && r.boundToKey(65)==null,
            "unbound and reserved keys have no module lookup");
        final java.util.concurrent.atomic.AtomicBoolean refused=
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread worker=new Thread(()->{
            try {r.boundToKey(30);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        worker.start();worker.join();
        check(refused.get(),"key lookup is owner-thread-only");
        r.bind("lookup.0",303);
        ModuleRegistry.Entry stable=r.boundToKey(303);
        check(stable==r.get("lookup.0"),"exact registered entry returned");
        r.setEnabled("lookup.0",true);
        check(r.boundToKey(303)==stable &&
            stable.state()==ModuleRegistry.State.ENABLED,
            "state changes preserve lookup identity");
        r.bind("lookup.0",0);
        check(r.boundToKey(303)==null,"unbinding invalidates previous key");
        final java.util.Random rnd=new java.util.Random(3052026L);
        for(int i=0;i<48000;i++) {
            if(i%9==0) {
                int which=rnd.nextInt(modules.length);
                int newKey=1000+i;
                r.bind(modules[which].id(),newKey);
            }
            int probe;
            if(i%3==0) {
                int which=rnd.nextInt(modules.length);
                probe=r.get(modules[which].id()).keybind();
            } else if(i%3==1)probe=1000+rnd.nextInt(49000);
            else probe=0;
            ModuleRegistry.Entry reference=null;
            if(probe!=0)for(ModuleRegistry.Entry e:r.all()) {
                if(e.keybind()==probe){reference=e;break;}
            }
            check(r.boundToKey(probe)==reference,
                "cached hotkey lookup identical to live public snapshot "+i);
        }
        Fake appended=new Fake("lookup.after");
        r.register(appended,59999);
        check(r.boundToKey(59999)==r.get(appended.id()),
            "late registration visible to lookup");
        check(r.boundToKey(303)==null,
            "old unbound key remains invalid after registration");
        r.shutdown();
    }
    private static void testSettingsAndProfiles() throws Exception {
        ModuleRegistry r = new ModuleRegistry();
        Setting.Bool b = new Setting.Bool("enabled", false);
        Setting.Number n = new Setting.Number("opacity", 0.5, 0, 1);
        Setting.Choice mode = new Setting.Choice("style", "compact", "compact", "expanded");
        Fake a = new Fake("a"), c = new Fake("c");
        r.register(a, 15, b, n, mode); r.register(c, 20);
        fails(() -> n.setFromText("Infinity"), "Infinity rejected");
        fails(() -> n.setFromText("1.01"), "out of bound rejected");
        fails(() -> mode.setFromText("unknown"), "enum rejected");
        n.setFromText("0.65"); mode.setFromText("expanded"); b.setFromText("true");
        r.setEnabled("a", true);
        Path temp = Files.createTempDirectory("ghostfusion-profile-test-");
        ProfileStore profiles = new ProfileStore(temp, r);
        profiles.save("test");
        check(Files.exists(temp.resolve("test.properties")), "profile saved");
        r.bind("a", 34); r.setEnabled("a", false);
        n.setFromText("0.2"); mode.setFromText("compact"); b.setFromText("false");
        profiles.load("test");
        check(r.get("a").state() == ModuleRegistry.State.ENABLED, "enabled restored");
        check(r.get("a").keybind() == 15, "key restored");
        check(Math.abs(n.get() - .65) < .000001 && b.get() && "expanded".equals(mode.get()), "typed settings restored");
        profiles.save("test");
        check(Files.exists(temp.resolve("test.properties.bak")), "backup created");
        Path config = temp.resolve("test.properties");
        Properties corrupt = new Properties();
        try (InputStream in = Files.newInputStream(config)) { corrupt.load(in); }
        corrupt.setProperty("module.a.setting.opacity", "999");
        try (OutputStream out = Files.newOutputStream(config)) { corrupt.store(out, "tampered-test"); }
        boolean rejected = false;
        try { profiles.load("test"); } catch (IllegalArgumentException ex) { rejected = true; }
        check(rejected, "corrupt profile rejected");
        check(r.get("a").state() == ModuleRegistry.State.ENABLED && Math.abs(n.get() - .65) < .000001,
            "invalid profile caused no partial mutations");
        boolean traversalRejected = false;
        try { profiles.save("../bad"); } catch (IllegalArgumentException ex) { traversalRejected = true; }
        check(traversalRejected, "traversal rejected");
        Files.copy(temp.resolve("test.properties.bak"), config, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Properties lifecycle = new Properties();
        try (InputStream in = Files.newInputStream(config)) { lifecycle.load(in); }
        lifecycle.setProperty("module.c.enabled", "true");
        // Preserve a valid integrity seal: this fixture tests transactional
        // callback rollback rather than corrupt-file rejection.
        ConfigIntegrity.seal(lifecycle);
        try (OutputStream out = Files.newOutputStream(config)) { lifecycle.store(out, "simulate-module-failure"); }
        c.failEnable = true;
        boolean enableFailure = false;
        try { profiles.load("test"); } catch (IllegalStateException ex) { enableFailure = true; }
        check(enableFailure, "profile loader reports failed module enable");
        check(r.get("a").state() == ModuleRegistry.State.ENABLED &&
            r.get("c").state() == ModuleRegistry.State.DISABLED &&
            r.get("a").keybind() == 15, "best-effort profile rollback after lifecycle failure");
        r.shutdown();
    }
    private static void testIngressAndThread() throws Exception {
        ModuleRegistry r = new ModuleRegistry(); Fake a = new Fake("a"); r.register(a, 6); r.setEnabled("a", true);
        BoundedIngress q = new BoundedIngress(2);
        check(q.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK, 1)), "queue add1");
        check(q.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK, 2)), "queue add2");
        check(!q.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK, 3)) && q.rejected() == 1, "bounded queue rejects");
        check(q.drain(r, 1) == 1 && q.pending() == 1, "limited drain");
        check(q.drain(r, 5) == 1 && q.pending() == 0, "drain rest");
        check(a.seen.size() == 2 && a.seen.get(0) == 1 && a.seen.get(1) == 2, "event order preserved");
        final boolean[] rejected = {false};
        Thread outsider = new Thread(() -> {
            try { r.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK, 5)); }
            catch (IllegalStateException ex) { rejected[0] = true; }
        });
        outsider.start(); outsider.join();
        check(rejected[0], "wrong-thread dispatch rejected");
        r.shutdown();
    }
    public static void main(String[] args) throws Exception {
        testRegistry(); testAllocationFreeHotkeyLookup();
        testSettingsAndProfiles(); testIngressAndThread();
        check(assertions>48000,"48,000 dynamic hotkey lookup checks executed");
        System.out.println("CORE_REGRESSION_PASS assertions=" + assertions);
    }
}
