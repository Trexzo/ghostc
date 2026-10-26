package dev.ghostfusion.ui;

/** Consistent first-party visual tokens for optional HUD skins. */
public final class HudThemeTokens {
    public final int panel,accent,secondary,text,muted,sheen;
    private static final HudThemeTokens CLASSIC=new HudThemeTokens(
        0xB31A1E2A,0xFFA59AFF,0xFFB9C5D6,0xFFEAF0FF,0xFFBFC8D9,0);
    private static final HudThemeTokens MINIMAL=new HudThemeTokens(
        0xD0151921,0xFFE1E5EF,0xFF8CA2C5,0xFFF8F9FC,0xFFB8C2D0,0);
    private static final HudThemeTokens NEBULA=new HudThemeTokens(
        0xD5151526,0xFF9C8BFF,0xFF66DDD0,0xFFF5F3FF,0xFFC5C3DF,0x573A3159);
    private HudThemeTokens(int panel,int accent,int secondary,int text,int muted,int sheen){
        this.panel=panel;this.accent=accent;this.secondary=secondary;
        this.text=text;this.muted=muted;this.sheen=sheen;
    }
    public static HudThemeTokens of(String skin){
        if("classic".equals(skin))return CLASSIC;
        if("minimal".equals(skin))return MINIMAL;
        if("nebula".equals(skin))return NEBULA;
        throw new IllegalArgumentException("Unknown HUD skin");
    }
}
