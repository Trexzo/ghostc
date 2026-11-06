package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Scaled Slate type hierarchy, independent of Minecraft and OpenGL.
 *
 * Uses the caller's actual Minecraft glyph advances. Never ships fonts,
 * changes the global font renderer, or assumes fixed-width characters.
 * Primary names gain emphasis; secondary metadata becomes quieter.
 */
public final class GuiSlateTypeScale {
    public static final double PRIMARY=1.125;
    public static final double SECONDARY=0.875;
    private GuiSlateTypeScale() {}

    public static int sourceBudget(int availablePixels,double scale) {
        require(availablePixels,scale);
        return Math.max(0,(int)Math.floor(availablePixels/scale));
    }
    public static int displayWidth(int sourceWidth,double scale) {
        require(sourceWidth,scale);
        return (int)Math.ceil(sourceWidth*scale);
    }
    public static int displayHeight(int sourceHeight,double scale) {
        require(sourceHeight,scale);
        return (int)Math.ceil(sourceHeight*scale);
    }
    public static String fit(String raw,int availablePixels,double scale,
                             ToIntFunction<String> sourceFont) {
        if(sourceFont==null)throw new IllegalArgumentException("Slate font");
        int source=sourceBudget(availablePixels,scale);
        String text=HudTextFit.fit(raw,source,sourceFont);
        if(displayWidth(sourceFont.applyAsInt(text),scale)>availablePixels)
            throw new IllegalStateException("Scaled Slate text exceeded its budget");
        return text;
    }
    private static void require(int size,double scale) {
        if(size<0 || !Double.isFinite(scale) || scale<=0 || scale>3)
            throw new IllegalArgumentException("Slate type scale/extent");
    }
}
