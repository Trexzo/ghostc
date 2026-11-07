package dev.ghostfusion.ui;

/**
 * Baseline-safe vertical positioning for the optional smooth Slate font.
 * Painting a cropped glyph at its requested box top made capitals and
 * descenders jump vertically as the text changed. This model binds all
 * runs to a common baseline inside their original UI row.
 *
 * Coordinates and geometry only. No Minecraft, AWT, GL or input access.
 */
public final class GuiSlateSmoothPlacement {
    private GuiSlateSmoothPlacement() { }

    /**
     * Check actual measured width and baseline-safe height together.
     * A failed admission must never require an OpenGL texture upload.
     * A later larger pixel/height budget can admit the same cached glyph.
     */
    public static int admittedTop(int glyphWidth,int croppedHeight,
                                  int baselineFromTop,int pixelBudget,
                                  int heightBudget) {
        if(glyphWidth<1||glyphWidth>GuiSlateSmoothRaster.MAX_WIDTH||
           pixelBudget<0||pixelBudget>GuiSlateSmoothRaster.MAX_WIDTH)
            throw new IllegalArgumentException("Slate smooth glyph width");
        int top=top(croppedHeight,baselineFromTop,heightBudget);
        return glyphWidth<=pixelBudget?top:-1;
    }

    /** Baseline is three pixels above the end of the admitted text row. */
    public static int top(int croppedHeight,int baselineFromTop,
                          int heightBudget) {
        // A punctuation-only glyph can begin *below* the font baseline;
        // negative baseline offsets are valid and must not kill the trial.
        if(croppedHeight<1||croppedHeight>48||
           baselineFromTop < -48||baselineFromTop>48||
           heightBudget<1||heightBudget>48)
            throw new IllegalArgumentException("Slate smooth glyph box");
        final int baseline=heightBudget-3;
        final int top=baseline-baselineFromTop;
        if(top<0||top+croppedHeight>heightBudget)return -1;
        return top;
    }
}
