package dev.ghostfusion.features;

/**
 * Player ESP draw budget, independent of its nearest-80 candidate selection.
 * Limits only the renderer's GL workload and leaves the closest candidates
 * unchanged. Existing render mode painter ordering remains deterministic.
 */
public final class EspDrawLimit {
    private EspDrawLimit(){}
    public static int count(int configured,int available){
        if(configured<1 || configured>80 || available<0 || available>80)
            throw new IllegalArgumentException("Invalid ESP draw budget");
        return Math.min(configured,available);
    }
    public static int slot(int drawIndex,int drawCount,boolean filled){
        if(drawCount<1 || drawCount>80 || drawIndex<0 || drawIndex>=drawCount)
            throw new IllegalArgumentException("Invalid ESP drawn slot");
        return filled ? drawCount-1-drawIndex : drawIndex;
    }
}
