package dev.ghostfusion.features;

import dev.ghostfusion.ui.HudThemeTokens;

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
    // Two distinct opt-in modern layouts. Both palettes are immutable singletons.
    private static final TargetHudPalette MINIMAL=new TargetHudPalette(
        0xE0151921,0xFFE1E5EF,0xFF384250,0xFFF8F9FC,0xFFB8C2D0,0xAA5C6776,0xFF8CA2C5);
    private static final TargetHudPalette SLATE=new TargetHudPalette(
        HudThemeTokens.of("slate").panel,HudThemeTokens.of("slate").accent,
        HudThemeTokens.of("slate").track,HudThemeTokens.of("slate").text,
        HudThemeTokens.of("slate").muted,HudThemeTokens.of("slate").frame,
        HudThemeTokens.of("slate").secondary);
    public final int secondary;
    private TargetHudPalette(int background,int accent,int track,int name,
                             int healthText,int outline) {
        this(background,accent,track,name,healthText,outline,0);
    }
    private TargetHudPalette(int background,int accent,int track,int name,
                             int healthText,int outline,int secondary) {
        this.background=background;this.accent=accent;this.track=track;
        this.name=name;this.healthText=healthText;this.outline=outline;
        this.secondary=secondary;
    }
    /** Fail closed on invalid/malformed stored choices. */
    public static TargetHudPalette of(String skin) {
        if("classic".equals(skin))return CLASSIC;
        if("glass".equals(skin))return GLASS;
        if("minimal".equals(skin))return MINIMAL;
        if("slate".equals(skin))return SLATE;
        throw new IllegalArgumentException("Unknown Target HUD skin");
    }
}
