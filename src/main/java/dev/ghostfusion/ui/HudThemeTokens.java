package dev.ghostfusion.ui;

/** Consistent first-party visual tokens for optional HUD skins. */
public final class HudThemeTokens {
    public final int panel,accent,secondary,text,muted,sheen,frame,track;
    private static final HudThemeTokens CLASSIC=new HudThemeTokens(
        0xB31A1E2A,0xFFA59AFF,0xFFB9C5D6,0xFFEAF0FF,0xFFBFC8D9,0,0xFF7B72BC,0xFF414B60);
    private static final HudThemeTokens MINIMAL=new HudThemeTokens(
        0xE0151921,0xFFE1E5EF,0xFF8CA2C5,0xFFF8F9FC,0xFFB8C2D0,0,0xAA5C6776,0xFF384250);
    private static final HudThemeTokens SLATE=new HudThemeTokens(
        0xDA161A1E,0xFFA7D3E0,0xFFB7C6CE,0xFFF1F4F6,0xFF98A5AF,0x20344350,0x7544515B,0xFF303941);
    private HudThemeTokens(int panel,int accent,int secondary,int text,int muted,int sheen,int frame,int track){
        this.panel=panel;this.accent=accent;this.secondary=secondary;
        this.text=text;this.muted=muted;this.sheen=sheen;
        this.frame=frame;this.track=track;
    }
    public static HudThemeTokens of(String skin){
        if("classic".equals(skin))return CLASSIC;
        if("minimal".equals(skin))return MINIMAL;
        if("slate".equals(skin))return SLATE;
        throw new IllegalArgumentException("Unknown HUD skin");
    }
}
