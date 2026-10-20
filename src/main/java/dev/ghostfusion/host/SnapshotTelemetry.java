package dev.ghostfusion.host;

/**
 * Numeric-only snapshot publication timing. Five fixed-size bounded rank indexes:
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
        // One-based Fenwick order-statistic index, bounded to the same 4097
        // microsecond buckets as the original linear-scan histogram.
        // Each successful sample costs <=13 updates; p50/p95/p99 cost <=13
        // lookups each, regardless of elapsed session length.
        final long[] counts=new long[MAX_US_BIN+2];
        private Snapshot cached;
        long samples,scanned,inRange,losQueries,published,peakUs;
        void record(long micros,int players,int range,int rays,int targets){
            int bin=(int)Math.min(MAX_US_BIN,micros);
            for(int i=bin+1;i<counts.length;i+=i&-i)
                counts[i]=plus(counts[i],1L);
            samples=plus(samples,1);
            scanned=plus(scanned,players);
            inRange=plus(inRange,range);
            losQueries=plus(losQueries,rays);
            published=plus(published,targets);
            if(micros>peakUs)peakUs=micros;
            cached=null;
        }
        private long percentile(int fraction){
            if(samples==0)return 0;
            // Identical nearest-rank definition, including partial hundredths,
            // to R2.17/R2.18. Avoid floating-point rank precision loss.
            long rank=(samples/100)*fraction+
                (samples%100*fraction+99)/100;
            int index=0;
            for(int step=Integer.highestOneBit(counts.length-1);step>0;step>>=1){
                int next=index+step;
                if(next<counts.length && counts[next]<rank){
                    index=next;
                    rank-=counts[next];
                }
            }
            return Math.min(MAX_US_BIN,index);
        }
        Snapshot snapshot(){
            if(cached==null)cached=new Snapshot(
                samples,scanned,inRange,losQueries,published,
                percentile(50),percentile(95),percentile(99),peakUs);
            return cached;
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
