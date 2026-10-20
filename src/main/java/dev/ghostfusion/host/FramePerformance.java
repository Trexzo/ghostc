package dev.ghostfusion.host;

/**
 * Fixed-memory owner-thread distribution of active-world render callback
 * intervals. Rounded FPS samples approximate presentation cadence, NOT
 * GPU frame execution time or a benchmark of the host OS.
 */
public final class FramePerformance {
    public static final int MAX_FPS=1000;
    private final Thread owner=Thread.currentThread();
    private final long[] fpsBins=new long[MAX_FPS+1];
    private long previousNanos;
    private boolean timing;
    private long frames, memorySamples, peakHeapBytes, lastHeapBytes, suspends;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("performance owner thread");
    }
    /** A long pause or world/GUI change must not appear as a 0 FPS frame. */
    public void suspend() {
        check();
        if(timing) suspends++;
        timing=false;
    }
    public void frame(long nowNanos,long heapBytes) {
        check();
        if(heapBytes<0)throw new IllegalArgumentException("negative heap usage");
        lastHeapBytes=heapBytes;
        if(heapBytes>peakHeapBytes)peakHeapBytes=heapBytes;
        if(memorySamples<Long.MAX_VALUE)memorySamples++;
        if(timing) {
            long elapsed=nowNanos-previousNanos;
            if(elapsed>0) {
                // Rounded to integer FPS; a 0 bucket represents <0.5 FPS.
                int fps=(int)Math.min(MAX_FPS,Math.round(1000000000.0/(double)elapsed));
                if(fpsBins[fps]<Long.MAX_VALUE)fpsBins[fps]++;
                if(frames<Long.MAX_VALUE)frames++;
            }
        }
        previousNanos=nowNanos;
        timing=true;
    }
    public Snapshot snapshot() {
        check();
        if(frames==0) return new Snapshot(0,0,0,0,0,0,
            memorySamples,lastHeapBytes/1048576L,peakHeapBytes/1048576L,suspends);
        int min=-1,max=0,p50=-1,p95=-1,p99=-1;
        long cumulative=0;
        long rank50=rank(frames,50),rank95=rank(frames,95),rank99=rank(frames,99);
        for(int fps=0;fps<fpsBins.length;fps++) {
            long c=fpsBins[fps];
            if(c==0)continue;
            if(min<0)min=fps;
            max=fps;
            cumulative = cumulative > Long.MAX_VALUE-c ? Long.MAX_VALUE : cumulative+c;
            if(p50<0 && cumulative>=rank50)p50=fps;
            if(p95<0 && cumulative>=rank95)p95=fps;
            if(p99<0 && cumulative>=rank99)p99=fps;
        }
        return new Snapshot(frames,min,max,p50,p95,p99,
            memorySamples,lastHeapBytes/1048576L,peakHeapBytes/1048576L,suspends);
    }
    private static long rank(long n,long percentile) {
        // ceil(percentile*n / 100) without overflow.
        return (n/100)*percentile+(n%100*percentile+99)/100;
    }
    public static final class Snapshot {
        public final long frames,memorySamples,heapMiB,peakHeapMiB,suspends;
        public final int minFps,maxFps,medianFps,p95Fps,p99Fps;
        Snapshot(long frames,int min,int max,int median,int p95,int p99,
                 long memorySamples,long heapMiB,long peakHeapMiB,long suspends) {
            this.frames=frames;this.minFps=min;this.maxFps=max;
            this.medianFps=median;this.p95Fps=p95;this.p99Fps=p99;
            this.memorySamples=memorySamples;this.heapMiB=heapMiB;
            this.peakHeapMiB=peakHeapMiB;this.suspends=suspends;
        }
    }
}
