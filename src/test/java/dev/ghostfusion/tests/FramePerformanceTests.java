package dev.ghostfusion.tests;
import dev.ghostfusion.host.FramePerformance;
import dev.ghostfusion.host.HostRuntime;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.9 fixed-memory, owner-thread telemetry and opt-in export. */
public final class FramePerformanceTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String why) {
        boolean failed=false;
        try{r.run();}catch(RuntimeException expected){failed=true;}
        ok(failed,why);
    }
    private static void histogram() {
        FramePerformance p=new FramePerformance();
        ok(p.snapshot().frames==0 && p.snapshot().minFps==0,"empty baseline");
        p.frame(1,1024*1024L);
        ok(p.snapshot().frames==0,"first draw has no delta");
        p.suspend();p.suspend();
        p.frame(90000000000L,2*1048576L);
        ok(p.snapshot().frames==0,"pause discards huge gap");
        ok(p.snapshot().suspends==1,"suspend idempotent");
        p.frame(90016666667L,3*1048576L);
        FramePerformance.Snapshot s=p.snapshot();
        ok(s.frames==1&&s.minFps==60&&s.medianFps==60&&
            s.p95Fps==60&&s.p99Fps==60,"60 FPS sample exact");
        ok(s.heapMiB==3&&s.peakHeapMiB==3,"heap sample precise");
        p.frame(90016666667L,1048576);
        ok(p.snapshot().frames==1,"zero elapsed time ignored");
        rejects(()->p.frame(10,-1),"invalid heap rejected");
        p.frame(89900000000L,1048576);
        ok(p.snapshot().frames==1 &&
           p.snapshot().invalidIntervals==1,
           "reversed timestamp rejected without altering valid FPS bins");
        p.frame(89920000000L,1048576);
        ok(p.snapshot().frames==2 &&
           p.snapshot().minFps==50&&p.snapshot().maxFps==60,
           "valid rendering resumes from new reference after bad timestamp");
        FramePerformance distribution=new FramePerformance();
        long t=1;
        distribution.frame(t,0);
        for(int i=0;i<100;i++) {
            int rate=i<10?10:i<50?50:i<95?100:200;
            t+=1000000000L/rate;
            distribution.frame(t,(i+1)*1048576L);
        }
        s=distribution.snapshot();
        ok(s.frames==100,"all samples counted");
        ok(s.minFps==10&&s.maxFps==200,"min/max accurate");
        ok(s.medianFps==50&&s.p95Fps==100&&s.p99Fps==200,
            "nearest-rank quantiles exact");
        ok(s.memorySamples==101&&s.peakHeapMiB==100,"heap history accurate");
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread outsider=new Thread(()->{
            try{distribution.snapshot();}
            catch(IllegalStateException expected){refused.set(true);}
        });
        outsider.start();
        try{outsider.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(refused.get(),"no off-thread metric access");
    }
    /** R2.94: a forward nanoTime jump across the signed-subtraction
     * boundary is a valid, very slow frame, not a reversed clock sample. */
    private static void wideForwardIntervals() {
        Random rnd=new Random(2942026L);
        FramePerformance perf=new FramePerformance();
        for(int trial=0;trial<12000;trial++) {
            perf.suspend();
            long start=Long.MIN_VALUE+rnd.nextInt(1000000);
            long end=Long.MAX_VALUE-rnd.nextInt(1000000);
            perf.frame(start,1048576L);
            perf.frame(end,1048576L);
            if(trial%100==0) {
                FramePerformance.Snapshot snapshot=perf.snapshot();
                ok(snapshot.frames==trial+1 &&
                   snapshot.minFps==0 && snapshot.maxFps==0 &&
                   snapshot.medianFps==0 && snapshot.p99Fps==0,
                   "wide elapsed counted as one zero FPS interval "+trial);
                ok(snapshot.invalidIntervals==0,
                   "forward wide interval never recorded as invalid "+trial);
            }
        }
        FramePerformance.Snapshot all=perf.snapshot();
        ok(all.frames==12000 && all.invalidIntervals==0 &&
           all.memorySamples==24000 && all.peakHeapMiB==1,
           "all wide intervals accepted with fixed-memory telemetry");
        // A freshly resumed ordinary frame must not inherit the huge gap.
        perf.suspend();
        perf.frame(-16666667L,1048576L);
        perf.frame(0L,1048576L);
        all=perf.snapshot();
        ok(all.frames==12001 && all.maxFps==60 && all.minFps==0,
           "ordinary 60 FPS still available after huge elapsed measurements");
        long before=all.frames;
        perf.frame(-1L,1048576L);
        ok(perf.snapshot().frames==before &&
           perf.snapshot().invalidIntervals==1,
           "a truly backward signed clock remains invalid");
        perf.frame(19999999L,1048576L);
        ok(perf.snapshot().frames==before+1 &&
           perf.snapshot().invalidIntervals==1,
           "valid cadence resumes after a reversed clock sample");

        FramePerformance boundary=new FramePerformance();
        boundary.frame(0L,0L);
        boundary.frame(2000000000L,0L);
        ok(boundary.snapshot().frames==1 &&
           boundary.snapshot().medianFps==1,
           "exact two-second boundary rounds half FPS up to one");
        boundary.frame(4000000001L,0L);
        ok(boundary.snapshot().frames==2 &&
           boundary.snapshot().minFps==0 &&
           boundary.snapshot().maxFps==1,
           "duration beyond two seconds rounds to zero FPS");
    }
    private static void replay() {
        Random random=new Random(20261020L);
        FramePerformance p=new FramePerformance();
        long clock=1;
        p.frame(clock,0);
        int n=0;
        for(int i=0;i<12000;i++) {
            if(i%317==0) {
                p.suspend();
                clock+=120000000000L;
                p.frame(clock,random.nextInt(500)*1048576L);
                continue;
            }
            int speed=20+random.nextInt(221);
            clock+=Math.round(1000000000.0/speed);
            p.frame(clock,random.nextInt(500)*1048576L);
            n++;
            FramePerformance.Snapshot s=p.snapshot();
            ok(s.frames==n,"exact sample count "+i);
            ok(s.minFps<=s.medianFps&&s.medianFps<=s.p95Fps&&
                s.p95Fps<=s.p99Fps&&s.p99Fps<=s.maxFps,
                "ordered quantiles "+i);
            ok(s.heapMiB<=s.peakHeapMiB,"heap bounded "+i);
        }
        ok(p.snapshot().suspends>=30,"replay suspensions counted");
    }
    private static void integration() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r29-frame");
        HostRuntime h=new HostRuntime(dir);
        h.performanceFrame(1,0,true);
        ok(h.performanceSnapshot().frames==0,"no world no samples");
        h.worldPresence(true);
        h.performanceFrame(5,0,true);
        h.performanceFrame(20000005L,0,true);
        ok(h.performanceSnapshot().frames==0,"diagnostics opt-in enforced");
        h.uiActions().toggle("hud.diagnostics");
        h.performanceFrame(1,1048576L,true);
        h.performanceFrame(20000001L,3*1048576L,true);
        ok(h.performanceSnapshot().medianFps==50,"50 fps observed on client bridge");
        h.performanceFrame(9000000000L,4*1048576L,false);
        h.performanceFrame(10000000000L,5*1048576L,true);
        ok(h.performanceSnapshot().frames==1,"GUI pause excluded");
        h.performanceFrame(10020000000L,6*1048576L,true);
        ok(h.performanceSnapshot().frames==2,"sampling resumed");
        h.worldPresence(false);
        h.disconnect();
        Path report=dir.resolve("diagnostics-last.properties");
        ok(Files.isRegularFile(report),"optional report generated on disconnect");
        Properties file=new Properties();
        try(InputStream in=Files.newInputStream(report)){file.load(in);}
        ok("2".equals(file.getProperty("perf.frame_samples")),"frame count saved");
        ok("50".equals(file.getProperty("perf.median_fps")),"median saved");
        ok("6".equals(file.getProperty("perf.peak_heap_mib")),"heap peak saved");
        ok("0".equals(file.getProperty("perf.invalid_intervals")),
            "safe invalid interval count exported");
        ok("counter-only-no-identity".equals(file.getProperty("scope")),
            "privacy scope maintained");
        for(String k:file.stringPropertyNames()) {
            ok(k.equals("schema")||k.equals("scope")||
                k.startsWith("count.")||k.startsWith("perf.")||k.startsWith("snap."),
                "no unapproved keys");
            if(k.startsWith("perf.")||k.startsWith("snap."))
                ok(file.getProperty(k).matches("[0-9]+"),"numeric-only telemetry");
        }
        h.close();
        ok(Files.exists(dir.resolve("diagnostics-last.properties.bak")),
            "close preserved export backup");
    }
    public static void main(String[] args) throws Exception {
        histogram();wideForwardIntervals();replay();integration();
        ok(checks>30000,"substantial deterministic timing coverage");
        System.out.println("FRAME_PERFORMANCE_PASS assertions="+checks);
    }
}
