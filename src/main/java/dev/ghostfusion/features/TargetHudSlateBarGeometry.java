package dev.ghostfusion.features;

/**
 * Allocation-free Slate Target HUD bar geometry.
 * The existing card stays 160x43 (health visible), or 160x29 (health hidden).
 * Four restrained quarter markers provide progress rhythm; they never alter
 * health targeting, animation, damage or saved anchor behavior.
 */
public final class TargetHudSlateBarGeometry {
    public static final int INSET=9;
    public static final int TOP=25;
    public static final int HEIGHT=5;
    public static final int DIVIDER_Y=18;
    private TargetHudSlateBarGeometry() {}

    public static int left(int cardX){return cardX+INSET;}
    public static int right(int cardX,int cardWidth) {
        if(cardWidth<2*INSET+4)
            throw new IllegalArgumentException("Slate Target HUD bar width");
        return cardX+cardWidth-INSET;
    }
    public static int filled(int trackWidth,double fraction) {
        if(trackWidth<1)throw new IllegalArgumentException("HUD track");
        double bounded=Double.isFinite(fraction)?
            Math.max(0,Math.min(1,fraction)):0;
        return (int)Math.round(trackWidth*bounded);
    }
    /** Quarter dividers at 25/50/75%. Endpoints are never returned. */
    public static int marker(int trackWidth,int quarter) {
        if(trackWidth<4||quarter<1||quarter>3)
            throw new IllegalArgumentException("HUD quarter marker");
        return Math.max(1,Math.min(trackWidth-1,
            (int)Math.round(trackWidth*quarter/4.0)));
    }
    public static boolean fits(int cardWidth,int cardHeight,int fontHeight) {
        if(cardWidth<2*INSET+4||cardHeight<1||fontHeight<1)return false;
        // Name readout at y+7 and muted separator at y+18 cannot collide.
        return 7+fontHeight<=DIVIDER_Y &&
            DIVIDER_Y+1<TOP && TOP+HEIGHT<=cardHeight;
    }
}
