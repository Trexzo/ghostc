package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.FeatureFaultGuard;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Regression: a frame callback can fail without making other modules crash or leaking messages. */
public final class FeatureFaultTests {
    private static int checks;
    private static void ok(boolean pass,String why) {
        checks++; if(!pass) throw new AssertionError(why);
    }
    private static final class TestModule implements GhostModule {
        final String id;
        int disabled;
        TestModule(String id) { this.id=id; }
        public String id() { return id; }
        public String displayName() { return "Test feature"; }
        public Category category() { return Category.RENDER; }
        public void onEnable() { }
        public void onDisable() { disabled++; }
        public void onEvent(GameEvent e) { }
    }
    private static void faultContainment() throws Exception {
        ModuleRegistry registry=new ModuleRegistry();
        final String[] reported=new String[2];
        FeatureFaultGuard guard=new FeatureFaultGuard(registry,
            (id,type)->{reported[0]=id;reported[1]=type;});
        TestModule failed=new TestModule("hud.failure");
        TestModule safe=new TestModule("hud.safe");
        registry.register(failed,0);
        registry.register(safe,0);
        registry.setEnabled(failed.id(),true);
        registry.setEnabled(safe.id(),true);
        ok(!guard.execute(failed.id(), ()->{throw new IllegalArgumentException("PRIVATE-SECRET-MESSAGE");}),
           "failed renderer quarantined");
        ok(failed.disabled==1 && registry.get(failed.id()).state()==ModuleRegistry.State.DISABLED,
           "only failed module disabled");
        ok(registry.get(safe.id()).state()==ModuleRegistry.State.ENABLED,"independent module remains live");
        ok("hud.failure".equals(reported[0]) && "IllegalArgumentException".equals(reported[1]),
           "callback reports type and stable id only");
        ok(!guard.recentFaults().toString().contains("PRIVATE-SECRET-MESSAGE"),"exception messages never retained");
        ok(!guard.execute(failed.id(), ()->{throw new AssertionError("should not run");}),
           "disabled module never called again");
        final int[] draws={0};
        ok(guard.execute(safe.id(),()->draws[0]++),"healthy frame executed");
        ok(draws[0]==1,"healthy callback ran");
        boolean immutable=false;
        try {guard.recentFaults().clear();} catch(UnsupportedOperationException ex){immutable=true;}
        ok(immutable,"read-only fault summary");
        registry.setEnabled(failed.id(),true);
        ok(guard.execute(failed.id(), ()->draws[0]++),"reenabling after a fault allowed");
        ok(!guard.recentFaults().containsKey(failed.id()),"successful recovery clears previous fault");
        boolean seriousEscaped=false;
        try {guard.execute(safe.id(),()->{throw new AssertionError("JVM serious Error");});}
        catch(AssertionError expected){seriousEscaped=true;}
        ok(seriousEscaped,"JVM errors are not hidden");
        registry.shutdown();
    }
    private static void listenerErrors() {
        ModuleRegistry registry=new ModuleRegistry();
        TestModule mod=new TestModule("hud.listener");
        registry.register(mod,0);
        registry.setEnabled(mod.id(),true);
        FeatureFaultGuard guard=new FeatureFaultGuard(registry,(id,type)->{
            throw new IllegalStateException("listener broken");
        });
        ok(!guard.execute(mod.id(),()->{throw new IllegalStateException("frame broken");}),
           "fault reporter exception isolated");
        ok(registry.get(mod.id()).state()==ModuleRegistry.State.DISABLED,
           "even broken listener quarantines module");
        registry.shutdown();
    }
    private static void hostAndPersistence() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-feature-fault");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        host.uiActions().toggle("hud.keystrokes");
        host.uiActions().toggle("hud.cps");
        ok(host.runFeature("hud.keystrokes",()->{throw new IllegalStateException("private player name");})==false,
           "host quarantines failing rendering feature");
        ok(host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==1,
           "counter increments once");
        ok(host.faultWarning().contains("hud.keystrokes") &&
           !host.faultWarning().contains("private player name"),"warning sanitized");
        ok(!host.featureEnabled("hud.keystrokes") && host.featureEnabled("hud.cps"),
           "other rendering feature kept running");
        final int[] counter={0};
        for(int i=0;i<150;i++){
            ok(host.runFeature("hud.cps",()->counter[0]++),"healthy render "+i);
            ok(!host.runFeature("hud.keystrokes",()->{throw new IllegalStateException("repeated crash");}),
               "failed renderer suppressed "+i);
        }
        ok(counter[0]==150 && host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==1,
           "no per-frame exception storm");
        host.close();
        HostRuntime restart=new HostRuntime(dir);
        ok(restart.modules().get("hud.keystrokes").state()==ModuleRegistry.State.DISABLED,
           "failed feature disabled across restart");
        ok(restart.modules().get("hud.cps").state()==ModuleRegistry.State.ENABLED,
           "healthy feature persisted");
        restart.worldPresence(true);
        ok(!restart.runFeature("hud.keystrokes",()->counter[0]++),"disabled stays safe");
        restart.uiActions().toggle("hud.keystrokes");
        ok(restart.runFeature("hud.keystrokes",()->counter[0]++),"user opt-in recovers feature");
        restart.close();
    }
    private static void ownership() throws Exception {
        ModuleRegistry r=new ModuleRegistry();
        TestModule mod=new TestModule("hud.owner");
        r.register(mod,0);
        r.setEnabled(mod.id(),true);
        FeatureFaultGuard g=new FeatureFaultGuard(r,(id,type)->{});
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try {g.execute(mod.id(),()->{});} catch(IllegalStateException expected){rejected.set(true);}
        });
        worker.start();worker.join();
        ok(rejected.get(),"offthread UI callback rejected");
        r.shutdown();
    }
    public static void main(String[] args) throws Exception {
        faultContainment();listenerErrors();hostAndPersistence();ownership();
        ok(checks>=320,"substantial per-frame quarantine coverage");
        System.out.println("FEATURE_FAULT_PASS assertions="+checks);
    }
}
