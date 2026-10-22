package dev.ghostfusion.features;

import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Optional pixel-bounded distance label for the real Target HUD card.
 * Uses live current positions, never a cached snapshot distance or entity
 * name. No Minecraft classes, packets, or gameplay side effects.
 */
public final class TargetHudDistanceText {
    private TargetHudDistanceText() {}

    /**
     * Return a live player-to-target distance in Minecraft blocks.
     * Empty string means invalid/out of range/too narrow to display.
     * The card always reserves 9px margins, regardless of enabled mode.
     */
    public static String format(double targetX,double targetY,double targetZ,
                                double localX,double localY,double localZ,
                                double range,int pixels,ToIntFunction<String> widths) {
        if(widths==null || pixels<0)
            throw new IllegalArgumentException("HUD distance font/budget");
        if(!Double.isFinite(range) || range<=0 ||
           !Double.isFinite(targetX)||!Double.isFinite(targetY)||
           !Double.isFinite(targetZ)||!Double.isFinite(localX)||
           !Double.isFinite(localY)||!Double.isFinite(localZ))return "";
        double dx=targetX-localX,dy=targetY-localY,dz=targetZ-localZ;
        double sq=dx*dx+dy*dy+dz*dz;
        if(!Double.isFinite(sq)||sq>range*range)return "";
        String number=String.format(Locale.ROOT,"%.1f",Math.sqrt(sq));
        String standard=number+" blocks";
        int size=widths.applyAsInt(standard);
        if(size<0)throw new IllegalArgumentException("negative font width");
        if(size<=pixels)return standard;
        String compact=number+"b";
        int compactSize=widths.applyAsInt(compact);
        if(compactSize<0)throw new IllegalArgumentException("negative font width");
        return compactSize<=pixels?compact:"";
    }
}
