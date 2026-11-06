package dev.ghostfusion.ui;

/**
 * Immutable diagnostics-overlay colors. Slate shares the active GhostC
 * graphite/ice palette; older profile themes retain exact legacy colors.
 * This is presentation only, not telemetry collection or feature admission.
 */
public final class DiagnosticsHudStyle {
    public final int panel,title,body;
    private static final DiagnosticsHudStyle LEGACY=new DiagnosticsHudStyle(
        0xC31B2133,0xFFAEAAFF,0xFFC9D1DF);
    private static final DiagnosticsHudStyle SLATE=new DiagnosticsHudStyle(
        0xE2161A1E,HudThemeTokens.of("slate").accent,
        HudThemeTokens.of("slate").text);

    private DiagnosticsHudStyle(int panel,int title,int body) {
        this.panel=panel;this.title=title;this.body=body;
    }
    public static DiagnosticsHudStyle of(UiState.Theme theme) {
        if(theme==UiState.Theme.SLATE)return SLATE;
        if(theme==UiState.Theme.INDIGO||theme==UiState.Theme.GRAPHITE)
            return LEGACY;
        throw new IllegalArgumentException("Diagnostics HUD theme");
    }
}
