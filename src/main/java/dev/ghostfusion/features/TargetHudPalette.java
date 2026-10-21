package dev.ghostfusion.features;

/**
 * First-party Target HUD appearance tokens. No Minecraft objects or GL state.
 * Classic retains exact pre-R2.56 ARGB values; glass is opt-in via the
 * persisted hud.target.skin Choice setting.
 */
public final class TargetHudPalette {
    public final int background,accent,track,name,healthText,outline;
    private static final TargetHudPalette CLASSIC=new TargetHudPalette(
        0xD0141A2B,0xFF9A8CFF,0xFF414B60,0xFFF1F2FF,0xFFB6C2DD,0);
    private static final TargetHudPalette GLASS=new TargetHudPalette(
        0xDA102630,0xFF5DDDC8,0xFF304753,0xFFF4FFFD,0xFFB4EDE4,0x884EABA1);
    private TargetHudPalette(int background,int accent,int track,int name,
                             int healthText,int outline) {
        this.background=background;this.accent=accent;this.track=track;
        this.name=name;this.healthText=healthText;this.outline=outline;
    }
    /** Fail closed on invalid/malformed stored choices. */
    public static TargetHudPalette of(String skin) {
        if("classic".equals(skin))return CLASSIC;
        if("glass".equals(skin))return GLASS;
        throw new IllegalArgumentException("Unknown Target HUD skin");
    }
}
