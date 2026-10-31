package dev.ghostfusion.features;

/**
 * Two-column Slate Target HUD distance readout. The label is immutable,
 * and the live number gets its own pixel-width budget and right alignment.
 * No snapshots, render state or gameplay dependencies.
 */
public final class TargetHudSlateReadoutGeometry {
    public static final String TITLE="RANGE";
    public static final int INSET=9;
    public static final int GAP=8;
    private TargetHudSlateReadoutGeometry() {}

    public static int valueBudget(int cardWidth,int titleWidth) {
        if(cardWidth<1||titleWidth<0)
            throw new IllegalArgumentException("Target distance column width");
        return Math.max(0,cardWidth-2*INSET-titleWidth-GAP);
    }
    public static int valueX(int cardX,int cardWidth,int valueWidth) {
        if(cardWidth<1||valueWidth<0)
            throw new IllegalArgumentException("Target distance column");
        return cardX+cardWidth-INSET-valueWidth;
    }
    public static boolean separated(int cardWidth,int titleWidth,int valueWidth) {
        return cardWidth>=2*INSET+GAP &&
            titleWidth>=0&&valueWidth>=0 &&
            titleWidth+valueWidth+GAP<=cardWidth-2*INSET;
    }
}
