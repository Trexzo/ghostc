package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.FeatureFaultGuard;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.nio.file.Files;
import java.util.Map;

/** R3.30: only a guard-owned failure may create a new guarded fault report. */
public final class FeatureFaultLifecycleReportingTests {
    private static int checks;
    private static void ok(boolean condition,String why) {
        checks++;
        if(!condition)throw new AssertionError(why);
    }
    private static final class Module implements GhostModule {
        final String stableId;
        int disabled;
        boolean failDisable;
        Module(String id){stableId=id;}
        public String id(){return stableId;}
        public String displayName(){return stableId;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){
            disabled++;
            if(failDisable)throw new IllegalStateException("PRIVATE CLEANUP FAILURE");
        }
        public void onEvent(GameEvent event){}
    }
    private static void completedLifecycleHasNoFakeReport() {
        for(int i=0;i<1024;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Module self=new Module("test.self_stop");
            Module peer=new Module("test.peer");
            registry.register(self,0);registry.register(peer,0);
            registry.setEnabled(self.id(),true);
            registry.setEnabled(peer.id(),true);
            final int[] reports={0};
            FeatureFaultGuard guard=new FeatureFaultGuard(registry,
                (id,type)->reports[0]++);
            ok(!guard.execute(self.id(),()->{
                registry.setEnabled(self.id(),false);
                throw new IllegalArgumentException("PRIVATE SELF-STOP");
            }),"self-disable then throw returns false "+i);
            ok(self.disabled==1 &&
               registry.get(self.id()).state()==ModuleRegistry.State.DISABLED &&
               registry.get(self.id()).lastFault().isEmpty(),
               "completed disable is retained once "+i);
            ok(reports[0]==0 && guard.recentFaults().isEmpty() &&
               guard.latestFaultWarning().isEmpty(),
               "no fictitious warning or listener for voluntary self-stop "+i);
            ok(guard.execute(peer.id(),()->{}) &&
               registry.get(peer.id()).state()==ModuleRegistry.State.ENABLED,
               "peer retains normal behavior after self-stop "+i);
            registry.shutdown();
            ok(self.disabled==1 && peer.disabled==1,
               "registry shutdown does not invoke self cleanup twice "+i);
        }
        for(int i=0;i<1024;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Module self=new Module("test.shutdown_throw");
            Module peer=new Module("test.shutdown_peer");
            registry.register(self,0);registry.register(peer,0);
            registry.setEnabled(self.id(),true);registry.setEnabled(peer.id(),true);
            final int[] reports={0};
            FeatureFaultGuard guard=new FeatureFaultGuard(registry,
                (id,type)->reports[0]++);
            ok(!guard.execute(self.id(),()->{
                registry.shutdown();
                throw new IllegalArgumentException("PRIVATE CLOSED SESSION");
            }),"shutdown then throw returns false "+i);
            ok(self.disabled==1 && peer.disabled==1 && reports[0]==0 &&
               registry.get(self.id()).state()==ModuleRegistry.State.DISABLED &&
               guard.recentFaults().isEmpty() && guard.latestFaultWarning().isEmpty(),
               "successful terminal shutdown must not imply a new guard fault "+i);
            ok(!guard.execute(peer.id(),()->{
                throw new AssertionError("peer ran after terminal close");
            }),"closed peer is inert "+i);
        }
    }
    private static void disableFailurePreservesRegistryFault() {
        for(int i=0;i<1024;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Module self=new Module("test.lifecycle_failure");
            registry.register(self,0);
            registry.setEnabled(self.id(),true);
            self.failDisable=true;
            final int[] reports={0};
            FeatureFaultGuard guard=new FeatureFaultGuard(registry,
                (id,type)->reports[0]++);
            ok(!guard.execute(self.id(),()->{
                throw new IllegalArgumentException("PRIVATE EVENT MESSAGE");
            }),"event fault with failed disable contained "+i);
            ok(registry.get(self.id()).state()==ModuleRegistry.State.FAULTED &&
               "IllegalStateException".equals(registry.get(self.id()).lastFault()) &&
               self.disabled==1,
               "lifecycle failure remains authoritative, not event fault "+i);
            ok(reports[0]==0 && guard.recentFaults().isEmpty() &&
               guard.latestFaultWarning().isEmpty(),
               "no misleading secondary feature fault warning "+i);
            registry.setEnabled(self.id(),false);
            ok(registry.get(self.id()).state()==ModuleRegistry.State.DISABLED &&
               self.disabled==1,
               "explicit fault reset never invokes disabled callback again "+i);
            registry.shutdown();
        }
    }
    private static void oldWarningClearsAfterVoluntaryShutdown() {
        for(int i=0;i<768;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Module old=new Module("test.older_fault"),recovered=new Module("test.recovered");
            registry.register(old,0);registry.register(recovered,0);
            registry.setEnabled(old.id(),true);registry.setEnabled(recovered.id(),true);
            final int[] reports={0};
            FeatureFaultGuard guard=new FeatureFaultGuard(registry,(id,type)->reports[0]++);
            ok(!guard.execute(old.id(),()->{
                throw new IllegalArgumentException("PRIVATE OLD FAILURE");
            }),"older fault created "+i);
            ok(!guard.execute(recovered.id(),()->{
                throw new IllegalStateException("PRIVATE CURRENT FAILURE");
            }),"newer fault created "+i);
            ok(guard.latestFaultWarning().contains(recovered.id()) &&
               reports[0]==2,"newer guard fault has HUD priority "+i);
            registry.setEnabled(recovered.id(),true);
            ok(!guard.execute(recovered.id(),()->{
                registry.setEnabled(recovered.id(),false);
                throw new IllegalArgumentException("PRIVATE NORMAL SELF STOP");
            }),"self-disable replaces an unresolved older guard warning "+i);
            Map<String,String> map=guard.recentFaults();
            ok(map.size()==1 && map.containsKey(old.id()) &&
               !map.containsKey(recovered.id()) &&
               guard.latestFaultWarning().contains(old.id()) && reports[0]==2,
               "completed lifecycle clears stale warning, preserves peer "+i);
            registry.shutdown();
        }
    }
    private static void realHostCloseDoesNotIncrementFaults() throws Exception {
        for(int i=0;i<96;i++) {
            HostRuntime host=new HostRuntime(
                Files.createTempDirectory("ghostc-r330-real-close"));
            host.worldPresence(true);
            host.uiActions().toggle("hud.cps");
            ok(host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==0,
               "baseline fault count is zero "+i);
            ok(!host.runFeature("hud.cps",()->{
                host.close();
                throw new IllegalArgumentException("PRIVATE AUTHENTICATION");
            }),"host closes within feature callback then throws "+i);
            ok(host.isClosed() && host.faultWarning().isEmpty() &&
               host.featureFaults().recentFaults().isEmpty() &&
               host.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==0,
               "no invented guard fault after legitimate host close "+i);
        }
    }
    private static void ordinaryQuarantineStillReports() {
        ModuleRegistry registry=new ModuleRegistry();
        Module mod=new Module("test.actual_failure");
        registry.register(mod,0);registry.setEnabled(mod.id(),true);
        final int[] reports={0};
        FeatureFaultGuard guard=new FeatureFaultGuard(registry,(id,type)->{
            ok(id.equals(mod.id()) && type.equals("IllegalArgumentException"),
               "reported metadata is only the stable ID and sanitized type");
            reports[0]++;
        });
        ok(!guard.execute(mod.id(),()->{
            throw new IllegalArgumentException("PRIVATE SERVER NAME");
        }),"genuine active feature failure quarantined");
        ok(reports[0]==1 && mod.disabled==1 &&
           registry.get(mod.id()).state()==ModuleRegistry.State.DISABLED &&
           guard.latestFaultWarning().contains("IllegalArgumentException") &&
           !guard.latestFaultWarning().contains("PRIVATE"),
           "ordinary fault still reports exactly one safe warning");
        registry.shutdown();
    }
    public static void main(String[] args) throws Exception {
        completedLifecycleHasNoFakeReport();
        disableFailurePreservesRegistryFault();
        oldWarningClearsAfterVoluntaryShutdown();
        realHostCloseDoesNotIncrementFaults();
        ordinaryQuarantineStillReports();
        ok(checks>14000,"sustained lifecycle-aware feature fault tests");
        System.out.println("FEATURE_FAULT_LIFECYCLE_REPORTING_PASS checks="+checks);
    }
}
