package dev.ghostfusion.tests;
import dev.ghostfusion.host.SnapshotTelemetry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-thread, bounded and nonidentifying source-observation regressions. */
public final class SnapshotTelemetryTests {
    private static int checks;
    private static void ok(boolean good,String message) {
        checks++;if(!good)throw new AssertionError(message);
    }
    private static void rejects(Runnable task,String message) {
        boolean rejected=false;
        try{task.run();}catch(RuntimeException expected){rejected=true;}
        ok(rejected,message);
    }
    private static void exact(){
        SnapshotTelemetry m=new SnapshotTelemetry();
        SnapshotTelemetry.Snapshot e=m.snapshot();
        ok(e.samples==0&&e.p95Us==0&&e.maxUs==0,"empty collection is zero");
        m.record(1000,10,8,2,4);
        m.record(5000,10,9,0,6);
        m.record(10000,20,8,3,5);
        m.record(200000000,30,5,5,5);
        SnapshotTelemetry.Snapshot s=m.snapshot();
        ok(s.samples==4,"all successful snapshots recorded");
        ok(s.scanned==70&&s.inRange==30&&s.losQueries==10&&s.published==20,
            "aggregate input workload precisely");
        ok(s.p50Us==5&&s.p95Us==4096&&s.p99Us==4096,
            "nearest-rank quantiles capped in final bucket");
        ok(s.maxUs==200000,"uncapped maximum microseconds retained");
        rejects(()->m.record(-1,1,1,1,1),"negative elapsed rejected");
        rejects(()->m.record(0,-1,0,0,0),"negative counters rejected");
        rejects(()->m.record(0,1,2,0,0),"more range than scanned rejected");
        rejects(()->m.record(0,2,1,2,0),"more rays than range rejected");
        rejects(()->m.record(0,2,1,0,2),"more published than range rejected");
        ok(m.snapshot().samples==4,"invalid samples are atomic/no mutation");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try{m.record(0,0,0,0,0);}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        other.start();
        try{other.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(blocked.get(),"snapshot measurements owner-thread bound");
    }
    private static void deterministic(){
        SnapshotTelemetry m=new SnapshotTelemetry();
        Random random=new Random(2172026L);
        for(int i=0;i<16000;i++){
            int scanned=random.nextInt(1000);
            int passed=random.nextInt(scanned+1);
            int rays=random.nextInt(passed+1);
            int published=random.nextInt(passed+1);
            long duration=(i%509==0?15000L:random.nextInt(4096))*1000L;
            m.record(duration,scanned,passed,rays,published);
            SnapshotTelemetry.Snapshot s=m.snapshot();
            ok(s.samples==i+1,"sample total exact");
            ok(s.p50Us<=s.p95Us&&s.p95Us<=s.p99Us,
                "quantiles monotonic");
            ok(s.p99Us<=SnapshotTelemetry.MAX_US_BIN,
                "histogram quantiles bounded");
            ok(s.maxUs>=s.p99Us,"true maximum at least top percentile");
            ok(s.scanned>=s.inRange&&s.inRange>=s.losQueries,
                "cumulative admission counters ordered");
            ok(s.inRange>=s.published,"publication cannot exceed range checks");
        }
    }
    private static void optInExport() throws Exception {
        Path path=Files.createTempDirectory("ghostc-r217-profiler");
        HostRuntime host=new HostRuntime(path);
        host.recordSnapshotWork(2000,5,5,1,1);
        ok(host.snapshotTelemetry().samples==0,"no disconnected samples");
        host.worldPresence(true);
        host.recordSnapshotWork(2000,5,5,1,1);
        ok(host.snapshotTelemetry().samples==0,"diagnostics disabled by default");
        host.uiActions().toggle("hud.diagnostics");
        host.recordSnapshotWork(3000,15,9,3,4);
        host.recordSnapshotWork(6000,8,2,0,2);
        ok(host.snapshotTelemetry().samples==2,"enabled measurements recorded");
        ok(host.snapshotTelemetry().p95Us==6,"correct host p95");
        host.uiActions().toggle("hud.diagnostics");
        host.recordSnapshotWork(9000,9,8,7,6);
        ok(host.snapshotTelemetry().samples==2,"disabled feature stops measurement");
        host.uiActions().toggle("hud.diagnostics");
        host.disconnect();
        Path out=path.resolve("diagnostics-last.properties");
        ok(Files.exists(out),"opt-in disconnect export");
        Properties properties=new Properties();
        try(InputStream in=Files.newInputStream(out)){properties.load(in);}
        ok("2".equals(properties.getProperty("snap.samples")),"export sample count");
        ok("23".equals(properties.getProperty("snap.scanned")),"export scanned count");
        ok("3".equals(properties.getProperty("snap.los_queries")),"export LOS count");
        ok("6".equals(properties.getProperty("snap.p95_us")),"export p95");
        ok("6".equals(properties.getProperty("snap.max_us")),"export maximum");
        ok("counter-only-no-identity".equals(properties.getProperty("scope")),"privacy scope");
        for(String k:properties.stringPropertyNames()){
            ok(k.equals("schema")||k.equals("scope")||k.startsWith("count.")||
                k.startsWith("perf.")||k.startsWith("snap."),
                "only numeric and schema properties");
            if(k.startsWith("snap."))ok(properties.getProperty(k).matches("[0-9]+"),
                "every snapshot entry is numeric");
        }
        host.close();
        ok(Files.exists(path.resolve("diagnostics-last.properties.bak")),
            "repeated opt-in export rotates previous report");
        RuntimeDiagnostics d=new RuntimeDiagnostics();
        Path second=Files.createTempDirectory("ghostc-legacy-r217");
        d.save(second);
        Properties legacy=new Properties();
        try(InputStream in=Files.newInputStream(
            second.resolve("diagnostics-last.properties"))){legacy.load(in);}
        ok(!legacy.containsKey("snap.samples"),"legacy save API remains compatible");
    }
    public static void main(String[] args) throws Exception{
        exact();deterministic();optInExport();
        ok(checks>80000,"wide profiler regression coverage");
        System.out.println("SNAPSHOT_TELEMETRY_PASS assertions="+checks);
    }
}
