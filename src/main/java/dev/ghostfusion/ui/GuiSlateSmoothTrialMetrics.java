package dev.ghostfusion.ui;

/**
 * Aggregate, text-free trial instrumentation for the optional smooth font.
 * No individual module names, keybinds, player names or GUI text are stored.
 * Owned by one GUI session; only its rendering thread updates the counters.
 */
public final class GuiSlateSmoothTrialMetrics {
    private long attempts,painted,fitHits,rasters,uploads,evictions,errors,nanos;
    private int peakTextures,peakLayouts;

    public void hit() {fitHits++;}
    public void raster() {rasters++;}
    public void upload() {uploads++;}
    public void evict() {evictions++;}
    public void error() {errors++;}

    public void draw(boolean success,long elapsedNanos,int textures,int layouts) {
        if(elapsedNanos<0||textures<0||textures>96||
           layouts<0||layouts>192)
            throw new IllegalArgumentException("Slate trial counters");
        attempts++;
        if(success)painted++;
        nanos+=elapsedNanos;
        if(textures>peakTextures)peakTextures=textures;
        if(layouts>peakLayouts)peakLayouts=layouts;
    }

    /** Stable label prefix for opt-in console logs, no user text. */
    public String report() {
        long average=attempts==0?0:(nanos/attempts)/1000L;
        return "GHOSTC_SLATE_FONT_TRIAL attempts="+attempts+
            " painted="+painted+" fallback="+(attempts-painted)+
            " fitHits="+fitHits+" rasters="+rasters+
            " uploads="+uploads+" evictions="+evictions+
            " errors="+errors+" peakTextures="+peakTextures+
            " peakLayouts="+peakLayouts+" avgDrawMicros="+average;
    }
}
