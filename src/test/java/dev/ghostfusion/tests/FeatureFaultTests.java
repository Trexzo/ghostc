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
        ok(guard.latestFaultWarning().contains("hud.failure") &&
           !guard.latestFaultWarning().contains("PRIVATE-SECRET-MESSAGE"),
           "latest warning uses only stable module and exception type");
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
        ok(guard.latestFaultWarning().isEmpty(),
           "successful retry clears an old on-screen module warning");
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
        ok(guard.latestFaultWarning().contains("hud.listener"),
           "broken fault reporter cannot hide the live warning");
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
    /** Every first-party Forge visual/movement callback uses the same host gate. */
    private static void fullFeatureQuarantine() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-all-render-features");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        String[] ids={"hud.keystrokes","hud.cps","hud.target",
                      "hud.diagnostics","render.esp","movement.sprint"};
        for(String id:ids) {
            host.uiActions().toggle(id);
            ok(host.featureEnabled(id),"feature enabled before fault "+id);
        }
        final int[] executed={0};
        for(int i=0;i<ids.length;i++) {
            final String id=ids[i];
            ok(!host.runFeature(id,()->{throw new IllegalArgumentException("SECRET_NAME");}),
                "feature quarantined "+id);
            ok(!host.featureEnabled(id),"fault disabled its module "+id);
            for(int j=i+1;j<ids.length;j++) {
                String healthy=ids[j];
                ok(host.runFeature(healthy,()->executed[0]++),
                   "unrelated later callback survives "+healthy);
            }
            ok(host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==i+1,
               "exactly one fault per feature");
        }
        ok(executed[0]==15,"all unaffected callbacks completed during isolation");
        ok(host.featureFaults().recentFaults().size()==ids.length,"bounded fault snapshot");
        ok(!host.featureFaults().recentFaults().toString().contains("SECRET_NAME"),
           "never retains private exception content");
        host.close();
        HostRuntime loaded=new HostRuntime(dir);
        loaded.worldPresence(true);
        for(String id:ids) {
            ok(!loaded.featureEnabled(id),"failed module remains disabled after restart "+id);
            loaded.uiActions().toggle(id);
            ok(loaded.runFeature(id,()->executed[0]++),"explicit reenable restores "+id);
        }
        loaded.close();
    }
    /** R2.96: direct and cross-module recursive renderer callbacks must
     * not bypass the existing module fault quarantine or overflow the stack. */
    private static void reentrantCallbackFence() {
        ModuleRegistry registry=new ModuleRegistry();
        TestModule a=new TestModule("hud.recursive.a");
        TestModule b=new TestModule("hud.recursive.b");
        registry.register(a,0); registry.register(b,0);
        registry.setEnabled(a.id(),true);registry.setEnabled(b.id(),true);
        final int[] faults={0},calls={0};
        FeatureFaultGuard guard=new FeatureFaultGuard(registry,
            (id,type)->{
                ok("IllegalStateException".equals(type),"recursive fault type sanitized");
                faults[0]++;
            });
        ok(!guard.execute(a.id(),()->{
            calls[0]++;
            guard.execute(a.id(),()->calls[0]++);
        }),"direct reentry quarantines offending feature");
        ok(calls[0]==1 && a.disabled==1 && faults[0]==1,
            "nested callback cannot execute and fault is reported once");
        ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED &&
           registry.get(b.id()).state()==ModuleRegistry.State.ENABLED,
           "recursive feature quarantined while unrelated peer remains enabled");
        ok("IllegalStateException".equals(guard.recentFaults().get(a.id())),
            "only sanitized recursive exception class is retained");

        registry.setEnabled(a.id(),true);
        final int[] inner={0};
        ok(guard.execute(a.id(),()->{
            ok(!guard.execute(b.id(),()->{
                inner[0]++;
                guard.execute(a.id(),()->inner[0]++);
            }),"indirect A-B-A recursion quarantines inner peer");
        }),"outer A completes after nested peer's contained failure");
        ok(inner[0]==1 && faults[0]==2 &&
           registry.get(a.id()).state()==ModuleRegistry.State.ENABLED &&
           registry.get(b.id()).state()==ModuleRegistry.State.DISABLED,
           "only reentering nested callback is rejected and B quarantined");
        ok(!guard.recentFaults().containsKey(a.id()) &&
           "IllegalStateException".equals(guard.recentFaults().get(b.id())),
           "healthy A stays clean while nested B fault is recorded");

        registry.setEnabled(b.id(),true);
        ok(guard.execute(a.id(),()->{
            ok(guard.execute(b.id(),()->inner[0]++),
                "independent nested module remains permitted");
        }),"normal A-B nested callback succeeds");
        ok(inner[0]==2 && faults[0]==2,
           "no new fault from independent nested module");

        final int[] intercepted={0};
        ok(guard.execute(a.id(),()->{
            try {guard.execute(a.id(),()->{throw new AssertionError("ran recursively");});}
            catch(IllegalStateException rejected) {intercepted[0]++;}
        }),"a caller may contain a rejected nested invocation itself");
        ok(intercepted[0]==1 && registry.get(a.id()).state()==ModuleRegistry.State.ENABLED,
           "rejected nested callback does not execute if exception is handled");

        boolean escaped=false;
        try {guard.execute(a.id(),()->{throw new AssertionError("serious JVM fault");});}
        catch(AssertionError expected){escaped=true;}
        ok(escaped,"JVM Error remains unswallowed by reentry boundary");
        ok(guard.execute(a.id(),()->calls[0]++),
           "guard released after a serious JVM Error");

        for(int i=0;i<12000;i++) {
            ok(!guard.execute(a.id(),()->{
                calls[0]++;
                guard.execute(a.id(),()->calls[0]++);
            }),"reentrant callback bounded at depth one "+i);
            ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED,
               "recursive callback remains disabled "+i);
            registry.setEnabled(a.id(),true);
            ok(guard.execute(a.id(),()->calls[0]++),
               "explicit re-enable restores legal callback "+i);
        }
        ok(calls[0]==24002 && faults[0]==12002,
           "12,000 cycles cannot stack-recurse, leak guard state or duplicate reports");
        registry.shutdown();
    }
    /** R2.99: one recovered renderer must clear its own warning without
     * masking a second unresolved feature or churning per-frame strings. */
    private static void liveWarningRecovery() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r299-live-fault-warning");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        host.uiActions().toggle("hud.cps");
        host.uiActions().toggle("hud.keystrokes");
        ok(host.faultWarning().isEmpty(),"no spurious warning before callback failures");
        ok(!host.runFeature("hud.cps",()->{
            throw new IllegalArgumentException("SECRET PLAYER NAME");
        }),"first feature fault quarantined");
        String a=host.faultWarning();
        ok(a.contains("hud.cps") && a.contains("IllegalArgumentException") &&
           !a.contains("SECRET"),"first fault retains no private payload");
        ok(!host.runFeature("hud.keystrokes",()->{
            throw new IllegalStateException("PRIVATE SERVER ADDRESS");
        }),"independent second feature fault quarantined");
        String b=host.faultWarning();
        ok(b.contains("hud.keystrokes") && !b.contains("PRIVATE") &&
           !b.contains("hud.cps"),"newest unresolved failure gets HUD priority");
        for(int i=0;i<40000;i++)
            ok(host.faultWarning()==b,
                "unchanged HUD warning retains same cached object "+i);
        host.uiActions().toggle("hud.keystrokes");
        ok(host.runFeature("hud.keystrokes",()->{}),
           "latest faulted feature explicitly reenabled and recovers");
        ok(host.faultWarning().contains("hud.cps") &&
           !host.faultWarning().contains("hud.keystrokes"),
           "recovery reveals older unresolved fault instead of leaving stale latest");
        host.uiActions().toggle("hud.cps");
        ok(host.runFeature("hud.cps",()->{}),
           "last faulted feature successfully retries");
        ok(host.faultWarning().isEmpty(),"all successful recoveries clear the overlay warning");
        ok(host.featureFaults().recentFaults().isEmpty(),
           "visible warnings and bounded fault history agree");
        ok(host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==2,
           "recovery does not increment fault diagnostic counters");
        host.close();
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
        faultContainment();listenerErrors();hostAndPersistence();fullFeatureQuarantine();
        reentrantCallbackFence();liveWarningRecovery();ownership();
        ok(checks>=320,"substantial per-frame quarantine coverage");
        System.out.println("FEATURE_FAULT_PASS assertions="+checks);
    }
}
