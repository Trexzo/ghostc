package dev.ghostfusion.ui;

/**
 * First-party, allocation-free Slate HUD interaction colors.
 * Keeps Keystrokes readable without a full bright accent fill when pressed.
 * Classic and Minimal never read these tokens.
 */
public final class SlateHudKeyStyle {
    private static final HudThemeTokens SLATE=HudThemeTokens.of("slate");
    // Slightly lighter than the neutral unpressed card, never neon/opaque ice.
    private static final int PRESSED_PANEL=0xE42C3942;
    private static final int PRESSED_EDGE=0xFF607E89;
    private SlateHudKeyStyle() {}

    public static int surface(boolean pressed) {
        return pressed?PRESSED_PANEL:SLATE.panel;
    }
    public static int topEdge(boolean pressed) {
        return pressed?PRESSED_EDGE:SLATE.frame;
    }
    /** Draw the accent as a 1px bottom rule, not a full-button fill. */
    public static int bottomEdge(boolean pressed) {
        return pressed?SLATE.accent:SLATE.sheen;
    }
    public static int text(boolean pressed) {
        return pressed?SLATE.text:SLATE.muted;
    }
}
