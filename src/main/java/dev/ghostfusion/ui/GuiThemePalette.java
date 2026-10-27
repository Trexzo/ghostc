package dev.ghostfusion.ui;

/**
 * A coherent, immutable ClickGUI appearance library.
 * INDIGO and GRAPHITE retain their original background/panel/accent colors.
 * The persisted SLATE identifier displays as SLATE: graphite, white and ice-blue.
 */
public final class GuiThemePalette {
    public final int background,panel,accent,header,divider,frame,sheen;
    public final int selectedCategory,selectedModule,idleModule,selectedSetting,idleSetting;
    public final int barTrack,text,muted,secondary;
    public final String label;
    public final boolean slate;

    private static final GuiThemePalette INDIGO=new GuiThemePalette(
        "INDIGO",false,0xF3161A2C,0xFF22283E,0xFF9A8CFF,
        0xBB080C1A,0x557E879D,0,0,
        0x556B83EA,0x664B597D,0x442D344A,0x886A588D,0x443B465C,
        0xFF525A6C,0xFFE4E7F0,0xFF979DAF,0xFF9A8CFF);
    private static final GuiThemePalette GRAPHITE=new GuiThemePalette(
        "MINT",false,0xF31A1C20,0xFF303339,0xFF69D9C6,
        0xBB080C1A,0x557E879D,0,0,
        0x556B83EA,0x664B597D,0x442D344A,0x886A588D,0x443B465C,
        0xFF525A6C,0xFFE4E7F0,0xFF979DAF,0xFF69D9C6);
    private static final GuiThemePalette SLATE=new GuiThemePalette(
        "SLATE",true,0xF5121519,0xFF1B2026,HudThemeTokens.of("slate").accent,
        0xF5161B21,0x5039414B,0x88444D58,HudThemeTokens.of("slate").sheen,
        0x573D4A54,0x64313F49,0x2228313B,0x4B35424A,0x26303A44,
        HudThemeTokens.of("slate").track,HudThemeTokens.of("slate").text,
        HudThemeTokens.of("slate").muted,HudThemeTokens.of("slate").secondary);

    private GuiThemePalette(String label,boolean slate,int background,int panel,
            int accent,int header,int divider,int frame,int sheen,
            int selectedCategory,int selectedModule,int idleModule,
            int selectedSetting,int idleSetting,int barTrack,
            int text,int muted,int secondary) {
        this.label=label;this.slate=slate;
        this.background=background;this.panel=panel;this.accent=accent;
        this.header=header;this.divider=divider;this.frame=frame;this.sheen=sheen;
        this.selectedCategory=selectedCategory;this.selectedModule=selectedModule;
        this.idleModule=idleModule;this.selectedSetting=selectedSetting;
        this.idleSetting=idleSetting;this.barTrack=barTrack;
        this.text=text;this.muted=muted;this.secondary=secondary;
    }
    public static GuiThemePalette of(UiState.Theme style) {
        if(style==UiState.Theme.INDIGO)return INDIGO;
        if(style==UiState.Theme.GRAPHITE)return GRAPHITE;
        if(style==UiState.Theme.SLATE)return SLATE;
        throw new IllegalArgumentException("Unknown ClickGUI theme");
    }
    /** Preserve one-click INDIGO -> GRAPHITE behavior, adding opt-in Slate. */
    public static UiState.Theme next(UiState.Theme current) {
        if(current==UiState.Theme.INDIGO)return UiState.Theme.GRAPHITE;
        if(current==UiState.Theme.GRAPHITE)return UiState.Theme.SLATE;
        if(current==UiState.Theme.SLATE)return UiState.Theme.INDIGO;
        throw new IllegalArgumentException("Unknown ClickGUI theme");
    }
}
