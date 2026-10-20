package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.SnapshotTelemetry;
import dev.ghostfusion.host.SnapshotTelemetry.Mode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.18 opt-in workload stratification and menu-focus exclusion. */
public final class SnapshotModeTests {
    private static int checks;
    private static void ok(boolean pass,String why) {
        checks++;if(!pass)throw new AssertionError(why);
    }
    private static void rejects(Runnable task,String reason) {
        boolean thrown=false;
        try{task.run();}catch(RuntimeException expected){thrown=true;}
        ok(thrown,reason);
    }
    private static void model() {
        ok(Mode.from(false,false)==Mode.OFF,"OFF classification");
        ok(Mode.from(true,false)==Mode.VISIBLE_ONLY,"visible classification");
        ok(Mode.from(false,true)==Mode.UNFILTERED_ONLY,"unfiltered classification");
        ok(Mode.from(true,true)==Mode.MIXED,"mixed classification");
        SnapshotTelemetry meter=new SnapshotTelemetry();
        ok(meter.snapshot().samples==0,"initial aggregate empty");
        for(Mode m:Mode.values())
            ok(meter.snapshot(m).samples==0,"initial mode empty");
        long[] expected={0,0,0,0};
        Random r=new Random(2182026L);
        for(int i=0;i<16000;i++){
            Mode mode=Mode.values()[i%4];
            long nanos=(1+r.nextInt(3000))*1000L;
            int scanned=r.nextInt(400);
            int passed=r.nextInt(scanned+1);
            int rays=r.nextInt(passed+1);
            int published=r.nextInt(passed+1);
            meter.record(nanos,scanned,passed,rays,published,mode);
            expected[mode.ordinal()]++;
            if(i%19==0){
                long groups=0;
                for(Mode type:Mode.values()){
                    SnapshotTelemetry.Snapshot snap=meter.snapshot(type);
                    ok(snap.samples==expected[type.ordinal()],
                       "group sample count exact "+type);
                    ok(snap.p50Us<=snap.p95Us&&snap.p95Us<=snap.p99Us,
                       "quantiles monotonic "+type);
                    groups+=snap.samples;
                }
                ok(meter.snapshot().samples==groups,"group totals equal aggregate");
            }
        }
        ok(meter.snapshot().samples==16000,"exact total sample count");
        for(Mode mode:Mode.values())
            ok(meter.snapshot(mode).samples==4000,"each class independently populated");
        rejects(()->meter.record(-1,0,0,0,0,Mode.MIXED),"reject negative duration");
        rejects(()->meter.record(100,3,2,4,0,Mode.MIXED),"reject impossible ray total");
        rejects(()->meter.record(100,0,0,0,0,null),"reject null mode");
        ok(meter.snapshot().samples==16000,"failed inputs have no partial writes");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try{meter.snapshot(Mode.OFF);}
            catch(IllegalStateException expectedFailure){blocked.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException error){throw new AssertionError(error);}
        ok(blocked.get(),"owner-thread-only mode histograms");
        // Old callers still contribute solely to the existing aggregate.
        meter.record(9000,0,0,0,0);
        ok(meter.snapshot().samples==16001,"legacy aggregate API retained");
        for(Mode mode:Mode.values())
            ok(meter.snapshot(mode).samples==4000,"legacy call does not invent a mode");
    }
    private static void focusAndExport() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r218-telemetry");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        ok(!host.snapshotProfilingEnabled(),"diagnostics opt-in by default");
        host.uiActions().toggle("hud.diagnostics");
        ok(host.snapshotProfilingEnabled(),"focused world and opt-in");
        host.recordSnapshotWork(2000,6,5,2,1,true,false);
        host.inputFocus(true);
        ok(!host.snapshotProfilingEnabled(),"open GUI blocks timing");
        host.recordSnapshotWork(7000000,12,10,9,10,true,true);
        ok(host.snapshotTelemetry().samples==1,
           "menu sample excluded even when diagnostics enabled");
        host.inputFocus(false);
        host.recordSnapshotWork(4000,8,7,0,3,false,true);
        host.recordSnapshotWork(6000,0,0,0,0,false,false);
        host.recordSnapshotWork(8000,9,8,6,4,true,true);
        ok(host.snapshotTelemetry().samples==4,"four focused observations");
        ok(host.lastSnapshotMode()==Mode.MIXED,"HUD identifies latest classified mode");
        ok(host.snapshotTelemetry(Mode.VISIBLE_ONLY).samples==1,"visible segregated");
        ok(host.snapshotTelemetry(Mode.UNFILTERED_ONLY).samples==1,"unfiltered segregated");
        ok(host.snapshotTelemetry(Mode.OFF).samples==1,"off segregated");
        ok(host.snapshotTelemetry(Mode.MIXED).samples==1,"mixed segregated");
        host.disconnect();
        Path file=dir.resolve("diagnostics-last.properties");
        Properties props=new Properties();
        try(InputStream in=Files.newInputStream(file)){props.load(in);}
        ok("4".equals(props.getProperty("snap.samples")),"old aggregate preserved");
        ok("1".equals(props.getProperty("snap.mode_off_samples")),"off sample exported");
        ok("1".equals(props.getProperty("snap.mode_visible_only_samples")),"visible sample exported");
        ok("1".equals(props.getProperty("snap.mode_unfiltered_only_samples")),"unfiltered sample exported");
        ok("1".equals(props.getProperty("snap.mode_mixed_samples")),"mixed sample exported");
        ok("2".equals(props.getProperty("snap.mode_visible_only_p95_us")),"visible timing");
        ok("8".equals(props.getProperty("snap.mode_mixed_max_us")),"mixed max");
        for(String key:props.stringPropertyNames()) {
            if(key.startsWith("snap.")){
                ok(key.matches("snap\\.[a-z0-9_]+"),"safe field whitelist "+key);
                ok(props.getProperty(key).matches("[0-9]+"),"numeric-only "+key);
            }
            ok(!key.contains("playername")&&!key.contains("address")&&!key.contains("server"),
               "no identities in keys");
        }
        host.close();
    }
    public static void main(String[] args)throws Exception {
        model();focusAndExport();
        ok(checks>1200,"substantial classified telemetry regression");
        System.out.println("SNAPSHOT_MODE_PASS assertions="+checks);
    }
}
