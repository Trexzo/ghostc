package dev.ghostfusion.tests;

import dev.ghostfusion.host.SnapshotTelemetry;
import dev.ghostfusion.host.SnapshotTelemetry.Mode;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nearest-rank p50/p95/p99 parity against the original 4097-bin scan.
 * Runs arbitrary zero, overflow and large-tailed samples independently in
 * all four demand groups and in the aggregate group.
 */
public final class SnapshotRankIndexTests {
    private static final int CAP=SnapshotTelemetry.MAX_US_BIN;
    private static int assertions;
    private static void ok(boolean condition,String why){
        assertions++;
        if(!condition)throw new AssertionError(why);
    }
    private static final class Reference {
        final long[] histogram=new long[CAP+1];
        long n,peak,scanned,range,rays,published;
        void add(long nanos,int scan,int passed,int los,int output){
            long us=nanos/1000L;
            int bin=(int)Math.min(CAP,us);
            histogram[bin]++;
            n++;scanned+=scan;range+=passed;rays+=los;published+=output;
            if(us>peak)peak=us;
        }
        long percentile(int percent) {
            if(n==0)return 0;
            long target=(n/100)*percent+(n%100*percent+99)/100;
            long cumulative=0;
            for(int i=0;i<histogram.length;i++){
                cumulative+=histogram[i];
                if(cumulative>=target)return i;
            }
            throw new AssertionError("reference percentile missing");
        }
        void matches(SnapshotTelemetry.Snapshot got,String label) {
            ok(got.samples==n,label+" sample count");
            ok(got.scanned==scanned&&got.inRange==range,label+" scan/range counts");
            ok(got.losQueries==rays&&got.published==published,label+" LOS/output counts");
            ok(got.p50Us==percentile(50),label+" p50 exact nearest rank");
            ok(got.p95Us==percentile(95),label+" p95 exact nearest rank");
            ok(got.p99Us==percentile(99),label+" p99 exact nearest rank");
            ok(got.maxUs==peak,label+" uncapped maximum");
        }
    }
    private static void boundaries(){
        SnapshotTelemetry p=new SnapshotTelemetry();
        SnapshotTelemetry.Snapshot empty=p.snapshot();
        ok(empty==p.snapshot(),"empty immutable aggregate view cached");
        ok(empty.samples==0&&empty.p99Us==0,"initial quantiles zero");
        for(Mode m:Mode.values())
            ok(p.snapshot(m)==p.snapshot(m),"empty modes cached independently");
        p.record(0,0,0,0,0,Mode.OFF);
        SnapshotTelemetry.Snapshot off=p.snapshot(Mode.OFF);
        ok(off.p50Us==0&&off.p95Us==0,"0 us bin accurate");
        ok(empty.samples==0,"old snapshot immutable after recording");
        ok(p.snapshot()==p.snapshot(),"same aggregate object on repeat reads");
        ok(p.snapshot(Mode.OFF)==off,"same mode object on repeat reads");
        SnapshotTelemetry.Snapshot other=p.snapshot(Mode.MIXED);
        p.record(4095000,2,1,0,1,Mode.OFF);
        ok(p.snapshot(Mode.OFF)!=off,"updated mode cache invalidated");
        ok(p.snapshot(Mode.MIXED)==other,"unrelated mode cache not invalidated");
        p.record(4096000,3,2,1,1,Mode.MIXED);
        p.record(Long.MAX_VALUE,4,3,2,2,Mode.MIXED);
        ok(p.snapshot().p99Us==4096,"4096+ bucket retains exact boundary");
        ok(p.snapshot().maxUs==Long.MAX_VALUE/1000L,
           "actual extreme elapsed duration not capped");
        ok(p.snapshot(Mode.MIXED).p95Us==4096,"overflow samples classified");
        boolean threw=false;
        try{p.record(-1,0,0,0,0,Mode.OFF);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"invalid elapsed rejected");
        ok(p.snapshot().samples==4,"invalid samples do not change tree");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try{p.snapshot(Mode.OFF);}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        foreign.start();
        try{foreign.join();}catch(InterruptedException interrupted){
            throw new AssertionError(interrupted);
        }
        ok(blocked.get(),"rank lookup owner-thread restricted");
    }
    private static void independentOracle(){
        SnapshotTelemetry profiler=new SnapshotTelemetry();
        Reference aggregate=new Reference();
        Reference[] oracle=new Reference[Mode.values().length];
        for(int i=0;i<oracle.length;i++)oracle[i]=new Reference();
        Random random=new Random(2192026L);
        final Mode[] modes=Mode.values();
        for(int i=1;i<=72000;i++){
            Mode mode=modes[random.nextInt(modes.length)];
            long nanos;
            if(i%541==0)nanos=Long.MAX_VALUE;
            else if(i%71==0)nanos=4096000L;
            else if(i%43==0)nanos=0L;
            else if(i%31==0)nanos=4095000L;
            else nanos=random.nextInt(5500)*1000L+random.nextInt(1000);
            int scanned=random.nextInt(900);
            int admitted=random.nextInt(scanned+1);
            int rays=random.nextInt(admitted+1);
            int published=random.nextInt(admitted+1);
            profiler.record(nanos,scanned,admitted,rays,published,mode);
            aggregate.add(nanos,scanned,admitted,rays,published);
            oracle[mode.ordinal()].add(nanos,scanned,admitted,rays,published);
            if(i%223==0 || i==72000) {
                aggregate.matches(profiler.snapshot(),"aggregate at "+i);
                long modeSamples=0;
                for(Mode m:modes){
                    SnapshotTelemetry.Snapshot current=profiler.snapshot(m);
                    oracle[m.ordinal()].matches(current,m.name()+" at "+i);
                    modeSamples+=current.samples;
                    ok(current==profiler.snapshot(m),"cached mode view "+m.name());
                }
                ok(modeSamples==profiler.snapshot().samples,
                   "classified samples conserve aggregate at "+i);
                ok(profiler.snapshot()==profiler.snapshot(),
                   "aggregate cache reference stable until new sample");
            }
        }
        // Legacy records must not update any classified histogram.
        SnapshotTelemetry.Snapshot lastMode=profiler.snapshot(Mode.OFF);
        SnapshotTelemetry.Snapshot prior=profiler.snapshot();
        profiler.record(6000L,5,3,0,2);
        ok(profiler.snapshot(Mode.OFF)==lastMode,
           "legacy recording cannot invalidate unrelated classified view");
        ok(profiler.snapshot()!=prior,"legacy aggregate invalidates only aggregate");
        aggregate.add(6000L,5,3,0,2);
        aggregate.matches(profiler.snapshot(),"legacy cumulative aggregate parity");
        ok(profiler.snapshot().samples==72001,"all 72001 samples preserved");
    }
    public static void main(String[] args){
        boundaries();independentOracle();
        ok(assertions>10000,"substantial independent rank equivalence replay");
        System.out.println("SNAPSHOT_RANK_INDEX_PASS assertions="+assertions);
    }
}
