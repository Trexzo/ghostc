package dev.ghostfusion.ui;

/**
 * Pure, bounded layout for the optional Forge diagnostics panel.
 * Screen coordinates are Minecraft scaled pixels. The full-height panel
 * retains R2.29 appearance; compact screens prioritize performance evidence.
 */
public final class DiagnosticsHudLayout {
    public static final int TOTAL_ROWS=14;
    private static final int[] COMPACT_PRIORITY={
        8,9,10,11,12,13,6,7,0,1,2,3,4,5
    };
    public final int x,y,width,height,rows,textWidth;
    private final int firstRow,step;
    private DiagnosticsHudLayout(int x,int y,int width,int height,
                                 int rows,int textWidth,int firstRow,int step) {
        this.x=x;this.y=y;this.width=width;this.height=height;
        this.rows=rows;this.textWidth=textWidth;
        this.firstRow=firstRow;this.step=step;
    }
    public static DiagnosticsHudLayout calculate(int viewportWidth,
                                                  int viewportHeight,int fontHeight) {
        if(viewportWidth<1||viewportHeight<1||fontHeight<1)
            throw new IllegalArgumentException("Diagnostics viewport/font");
        int width=Math.min(286,Math.max(0,viewportWidth-8));
        int height=Math.min(180,Math.max(0,viewportHeight-8));
        long first=Math.max(17L,8L+fontHeight);
        long stride=Math.max(11L,2L+fontHeight);
        // Hide an impossibly small panel rather than drawing outside it.
        if(width<=10 || first+fontHeight+2L>height)
            return new DiagnosticsHudLayout(0,0,0,0,0,0,0,0);
        int count=(int)Math.min(TOTAL_ROWS,
            1L+((long)height-2-first-fontHeight)/stride);
        return new DiagnosticsHudLayout(viewportWidth-width-4,4,width,height,
            count,Math.max(0,width-10),(int)first,(int)stride);
    }
    public int rowY(int slot) {
        requireSlot(slot);
        return y+firstRow+slot*step;
    }
    public int sourceRow(int slot) {
        requireSlot(slot);
        return rows==TOTAL_ROWS?slot:COMPACT_PRIORITY[slot];
    }
    private void requireSlot(int slot) {
        if(slot<0||slot>=rows)
            throw new IllegalArgumentException("Diagnostics row");
    }
    public boolean fitsViewport(int w,int h) {
        return rows>0 && x>=0 && y>=0 && width>0 && height>0 &&
            (long)x+width<=w && (long)y+height<=h;
    }
}
