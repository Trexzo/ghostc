package dev.ghostfusion.host;

/**
 * Fixed 4097-bin aggregate of real Forge snapshot publication elapsed times
 * (integer microseconds, final bin >=4096). No retained timestamps or entities.
 * This is client-thread elapsed work, not GPU time or total Minecraft tick time.
 */
public final class SnapshotTelemetry {
    public static final int MAX_US_BIN=4096;
    private final Thread owner=Thread.currentThread();
    private final long[] bins=new long[MAX_US_BIN+1];
    private long samples,scanned,inRange,losQueries,published,peakUs;
    private void check(){
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("snapshot profiler owner thread");
    }
    private static long plus(long a,long b){
        return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;
    }
    /** Accept only a complete, successfully published owner-tick sample. */
    public void record(long elapsedNanos,int playersScanned,int rangePassed,
                       int visibilityQueries,int targetsPublished){
        check();
        if(elapsedNanos<0 || playersScanned<0 || rangePassed<0 ||
           visibilityQueries<0 || targetsPublished<0 ||
           rangePassed>playersScanned || visibilityQueries>rangePassed ||
           targetsPublished>rangePassed)
            throw new IllegalArgumentException("invalid snapshot metrics");
        long micros=elapsedNanos/1000L;
        int bin=(int)Math.min(MAX_US_BIN,micros);
        bins[bin]=plus(bins[bin],1);
        samples=plus(samples,1);
        scanned=plus(scanned,playersScanned);
        inRange=plus(inRange,rangePassed);
        losQueries=plus(losQueries,visibilityQueries);
        published=plus(published,targetsPublished);
        if(micros>peakUs)peakUs=micros;
    }
    private long percentile(long numerator) {
        if(samples==0)return 0;
        long rank=(samples/100)*numerator+
            (samples%100*numerator+99)/100;
        long sum=0;
        for(int i=0;i<bins.length;i++){
            sum=plus(sum,bins[i]);
            if(sum>=rank)return i;
        }
        return MAX_US_BIN;
    }
    public Snapshot snapshot() {
        check();
        return new Snapshot(samples,scanned,inRange,losQueries,published,
            percentile(50),percentile(95),percentile(99),peakUs);
    }
    public static final class Snapshot {
        public final long samples,scanned,inRange,losQueries,published;
        /** Quantiles capped at 4096+ us; maxUs is never histogram-capped. */
        public final long p50Us,p95Us,p99Us,maxUs;
        Snapshot(long samples,long scanned,long inRange,long losQueries,long published,
                 long p50Us,long p95Us,long p99Us,long maxUs) {
            this.samples=samples;this.scanned=scanned;this.inRange=inRange;
            this.losQueries=losQueries;this.published=published;
            this.p50Us=p50Us;this.p95Us=p95Us;this.p99Us=p99Us;this.maxUs=maxUs;
        }
    }
}
