package dev.ghostfusion.ui;

/**
 * A coherent, immutable ClickGUI appearance library.
 * INDIGO and GRAPHITE retain their original background/panel/accent colors.
 * NEBULA shares the signature violet and cyan accents with the in-game HUD.
 */
public final class GuiThemePalette {
    public final int background,panel,accent,header,divider,frame,sheen;
    public final int selectedCategory,selectedModule,idleModule,selectedSetting,idleSetting;
    public final int barTrack,text,muted,secondary;
    public final String label;
    public final boolean nebula;

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
    private static final GuiThemePalette NEBULA=new GuiThemePalette(
        "NEBULA",true,0xF515171D,0xFF1D222B,HudThemeTokens.of("nebula").accent,
        0xF5181C24,0x54576273,0x88505769,HudThemeTokens.of("nebula").sheen,
        0x43465A70,0x59465A70,0x2428303C,0x49405268,0x27303949,
        0xFF303949,HudThemeTokens.of("nebula").text,
        0xFF9DA6B5,HudThemeTokens.of("nebula").secondary);

    private GuiThemePalette(String label,boolean nebula,int background,int panel,
            int accent,int header,int divider,int frame,int sheen,
            int selectedCategory,int selectedModule,int idleModule,
            int selectedSetting,int idleSetting,int barTrack,
            int text,int muted,int secondary) {
        this.label=label;this.nebula=nebula;
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
        if(style==UiState.Theme.NEBULA)return NEBULA;
        throw new IllegalArgumentException("Unknown ClickGUI theme");
    }
    /** Preserve one-click INDIGO -> GRAPHITE behavior, adding opt-in Nebula. */
    public static UiState.Theme next(UiState.Theme current) {
        if(current==UiState.Theme.INDIGO)return UiState.Theme.GRAPHITE;
        if(current==UiState.Theme.GRAPHITE)return UiState.Theme.NEBULA;
        if(current==UiState.Theme.NEBULA)return UiState.Theme.INDIGO;
        throw new IllegalArgumentException("Unknown ClickGUI theme");
    }
}
