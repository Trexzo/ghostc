package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.FramePerformance;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.host.SnapshotTelemetry;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/** R3.29: closing must invalidate read-only feature eligibility and telemetry. */
public final class HostClosingEligibilityTests {
    private static int checks;
    private static void ok(boolean yes,String context) {
        checks++;
        if(!yes)throw new AssertionError(context);
    }
    private static final class Observer implements GhostModule {
        final HostRuntime host;
        final String id;
        boolean sawClosedEligibility, callbackRan;
        int disabled;
        Observer(HostRuntime host,String id){this.host=host;this.id=id;}
        public String id(){return id;}
        public String displayName(){return id;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){
            disabled++;
            callbackRan=true;
            // The registry has not yet committed the disabled state for
            // this very callback, and worldPresent remains true.
            if(host.modules().get(id).state()==ModuleRegistry.State.ENABLED &&
               host.featureEnabled(id))
                sawClosedEligibility=true;
            if(host.snapshotProfilingEnabled())
                sawClosedEligibility=true;
            if(host.metricsFrameRequested())
                sawClosedEligibility=true;
        }
        public void onEvent(GameEvent event){}
    }
    /** Simulate the phase after close sets closing and before it shuts
     * down its modules; this keeps all diagnostic/visual opt-ins enabled. */
    private static void simulatedClosingRejectsTelemetry() throws Exception {
        Field closing=HostRuntime.class.getDeclaredField("closing");
        closing.setAccessible(true);
        for(int i=0;i<192;i++) {
            Path folder=Files.createTempDirectory("ghostc-r329-eligibility");
            HostRuntime host=new HostRuntime(folder);
            host.modules().setEnabled("hud.diagnostics",true);
            host.modules().setEnabled("hud.metrics",true);
            host.modules().setEnabled("hud.target",true);
            host.worldPresence(true);
            ok(host.featureEnabled("hud.target") &&
               host.snapshotProfilingEnabled(),
               "opt-in feature and telemetry eligible when live "+i);
            host.render2d();
            ok(host.metricsFrameRequested(),
               "live render intent is visible before close "+i);
            host.recordSnapshotWork(2000,5,4,1,2,true,true);
            host.performanceFrame(1000000000L,1048576L,true);
            host.performanceFrame(1020000000L,2097152L,true);
            long frames=host.performanceSnapshot().frames;
            long samples=host.snapshotTelemetry().samples;
            long heapSamples=host.performanceSnapshot().memorySamples;
            SnapshotTelemetry.Mode mode=host.lastSnapshotMode();
            ok(frames==1 && samples==1 && heapSamples==2 &&
               mode==SnapshotTelemetry.Mode.MIXED,
               "pre-close metrics have actual samples "+i);
            closing.setBoolean(host,true);
            try {
                ok(!host.featureEnabled("hud.target") &&
                   !host.snapshotProfilingEnabled() &&
                   !host.metricsFrameRequested(),
                   "closing invalidates all read-only eligibility gates "+i);
                for(int j=0;j<12;j++) {
                    host.recordSnapshotWork(9000,9,8,7,6,true,false);
                    host.recordSnapshotWork(9000,9,8,7,6);
                    host.performanceFrame(1040000000L+20000000L*j,10485760L,true);
                }
                FramePerformance.Snapshot after=host.performanceSnapshot();
                ok(after.frames==frames && after.memorySamples==heapSamples &&
                   host.snapshotTelemetry().samples==samples &&
                   host.lastSnapshotMode()==mode,
                   "closing cannot mutate FPS, heap or snapshot timing "+i);
                ok(host.diagnosticsEnabled(),
                   "diagnostic opt-in stays visible for the trusted final export "+i);
            } finally {
                closing.setBoolean(host,false);
            }
            ok(host.featureEnabled("hud.target") &&
               host.snapshotProfilingEnabled() &&
               host.metricsFrameRequested(),
               "normal read-only eligibility resumes outside shutdown "+i);
            host.recordSnapshotWork(3000,5,4,1,2,false,true);
            host.performanceFrame(1040000000L,3145728L,true);
            ok(host.snapshotTelemetry().samples==samples+1 &&
               host.performanceSnapshot().frames==frames+1 &&
               host.lastSnapshotMode()==SnapshotTelemetry.Mode.UNFILTERED_ONLY,
               "normal telemetry resumes and tracks new demand group "+i);
            host.close();
            ok(host.isClosed() && !host.featureEnabled("hud.target") &&
               !host.snapshotProfilingEnabled() &&
               !host.metricsFrameRequested(),
               "closed host remains ineligible "+i);
        }
    }
    private static void realShutdownCallbackCannotAdvertiseFeatures() throws Exception {
        for(int i=0;i<192;i++) {
            HostRuntime host=new HostRuntime(Files.createTempDirectory(
                "ghostc-r329-true-close"));
            Observer observer=new Observer(host,"test.closing_observer");
            host.modules().register(observer,0);
            host.modules().setEnabled(observer.id(),true);
            host.modules().setEnabled("hud.diagnostics",true);
            host.modules().setEnabled("hud.metrics",true);
            host.worldPresence(true);
            host.render2d();
            host.close();
            ok(observer.callbackRan && observer.disabled==1 &&
               !observer.sawClosedEligibility,
               "real onDisable callback never sees live feature eligibility "+i);
            ok(host.isClosed() &&
               host.modules().get(observer.id()).state()==ModuleRegistry.State.DISABLED,
               "real owner shutdown remains terminal and exactly once "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        simulatedClosingRejectsTelemetry();
        realShutdownCallbackCannotAdvertiseFeatures();
        ok(checks>2000,"sustained closing eligibility and telemetry replay");
        System.out.println("HOST_CLOSING_ELIGIBILITY_PASS checks="+checks);
    }
}
