package dev.ghostfusion.host;

/**
 * Numeric-only snapshot publication timing. Five fixed-size histograms:
 * aggregate plus one per active target-demand mode. Nothing stores targets,
 * positions, server identity or raw timing sequences.
 */
public final class SnapshotTelemetry {
    public static final int MAX_US_BIN=4096;
    public enum Mode {
        OFF, VISIBLE_ONLY, UNFILTERED_ONLY, MIXED;
        public static Mode from(boolean visible,boolean unfiltered){
            if(visible)return unfiltered?MIXED:VISIBLE_ONLY;
            return unfiltered?UNFILTERED_ONLY:OFF;
        }
    }
    private static final class Bucket {
        final long[] bins=new long[MAX_US_BIN+1];
        long samples,scanned,inRange,losQueries,published,peakUs;
        void record(long micros,int players,int range,int rays,int targets){
            int bin=(int)Math.min(MAX_US_BIN,micros);
            bins[bin]=plus(bins[bin],1);
            samples=plus(samples,1);
            scanned=plus(scanned,players);
            inRange=plus(inRange,range);
            losQueries=plus(losQueries,rays);
            published=plus(published,targets);
            if(micros>peakUs)peakUs=micros;
        }
        long percentile(int fraction){
            if(samples==0)return 0;
            long rank=(samples/100)*fraction+
                (samples%100*fraction+99)/100;
            long accum=0;
            for(int i=0;i<bins.length;i++){
                accum=plus(accum,bins[i]);
                if(accum>=rank)return i;
            }
            return MAX_US_BIN;
        }
        Snapshot snapshot(){
            return new Snapshot(samples,scanned,inRange,losQueries,published,
                percentile(50),percentile(95),percentile(99),peakUs);
        }
    }
    private final Thread owner=Thread.currentThread();
    private final Bucket all=new Bucket();
    private final Bucket[] perMode=new Bucket[Mode.values().length];
    public SnapshotTelemetry(){
        for(int i=0;i<perMode.length;i++)perMode[i]=new Bucket();
    }
    private void check(){
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("snapshot profiler owner thread");
    }
    private static long plus(long a,long b){
        return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;
    }
    private static void validate(long elapsedNanos,int scanned,int range,
                                 int rays,int published){
        if(elapsedNanos<0 || scanned<0 || range<0 || rays<0 || published<0 ||
           range>scanned || rays>range || published>range)
            throw new IllegalArgumentException("invalid snapshot metrics");
    }
    /** Backward-compatible nonclassified aggregate for existing callers. */
    public void record(long elapsedNanos,int scanned,int inRange,int rays,int published){
        check();
        validate(elapsedNanos,scanned,inRange,rays,published);
        all.record(elapsedNanos/1000L,scanned,inRange,rays,published);
    }
    /** Real Forge work must carry its exact visibility-demand class. */
    public void record(long elapsedNanos,int scanned,int inRange,int rays,int published,
                       Mode mode){
        check();
        if(mode==null)throw new IllegalArgumentException("snapshot demand mode");
        validate(elapsedNanos,scanned,inRange,rays,published);
        long micros=elapsedNanos/1000L;
        all.record(micros,scanned,inRange,rays,published);
        perMode[mode.ordinal()].record(micros,scanned,inRange,rays,published);
    }
    public Snapshot snapshot(){check();return all.snapshot();}
    public Snapshot snapshot(Mode mode){
        check();
        if(mode==null)throw new IllegalArgumentException("snapshot demand mode");
        return perMode[mode.ordinal()].snapshot();
    }
    public static final class Snapshot {
        public final long samples,scanned,inRange,losQueries,published;
        /** p50/p95/p99 capped at 4096+ us; maxUs remains uncapped. */
        public final long p50Us,p95Us,p99Us,maxUs;
        Snapshot(long samples,long scanned,long inRange,long losQueries,long published,
                 long p50Us,long p95Us,long p99Us,long maxUs){
            this.samples=samples;this.scanned=scanned;this.inRange=inRange;
            this.losQueries=losQueries;this.published=published;
            this.p50Us=p50Us;this.p95Us=p95Us;this.p99Us=p99Us;this.maxUs=maxUs;
        }
    }
}
