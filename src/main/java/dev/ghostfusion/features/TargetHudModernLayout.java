package dev.ghostfusion.features;

/**
 * Immutable first-party geometry tokens for the two opt-in modern Target HUDs.
 * Classic/Glass remain on their original pixel-identical render path.
 * All offsets are within the original 160x43 card, and health-off uses 160x29.
 */
public final class TargetHudModernLayout {
    public final int nameY,healthLabelY,trackY,trackHeight;
    public final int distanceWithHealthY,distanceWithoutHealthY;
    public final int accentWidth;
    public final boolean topBand;
    private static final TargetHudModernLayout MINIMAL=new TargetHudModernLayout(
        6,6,37,3,21,18,0,false);
    private static final TargetHudModernLayout NEBULA=new TargetHudModernLayout(
        8,8,25,5,33,18,3,true);

    private TargetHudModernLayout(int nameY,int healthLabelY,int trackY,int trackHeight,
                                  int distanceWithHealthY,int distanceWithoutHealthY,
                                  int accentWidth,boolean topBand) {
        this.nameY=nameY;this.healthLabelY=healthLabelY;
        this.trackY=trackY;this.trackHeight=trackHeight;
        this.distanceWithHealthY=distanceWithHealthY;
        this.distanceWithoutHealthY=distanceWithoutHealthY;
        this.accentWidth=accentWidth;this.topBand=topBand;
    }
    public static TargetHudModernLayout of(String skin) {
        if("minimal".equals(skin))return MINIMAL;
        if("nebula".equals(skin))return NEBULA;
        throw new IllegalArgumentException("Not a modern Target HUD skin");
    }
    /** Reject unusable font sizes rather than clipping into the screen. */
    public boolean fits(int cardWidth,int cardHeight,int fontHeight,boolean health) {
        if(cardWidth<30 || cardHeight<1 || fontHeight<1)return false;
        int distanceY=health?distanceWithHealthY:distanceWithoutHealthY;
        if(nameY+fontHeight>cardHeight ||
           distanceY+fontHeight>cardHeight)return false;
        if(!health)return cardHeight>=29;
        return healthLabelY+fontHeight<=cardHeight &&
               trackY>=0 && trackHeight>=1 &&
               trackY+trackHeight<=cardHeight &&
               (distanceY+fontHeight<=trackY ||
                trackY+trackHeight<=distanceY);
    }
}
