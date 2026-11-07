package dev.ghostfusion.ui;

/**
 * F7 Slate visual emphasis reflects real HUD visibility. Positionable editor
 * placeholders must not pretend their live widgets are enabled.
 *
 * Colors and admission only: no Minecraft/GL calls, input, profile writes,
 * feature toggles, HUD draw changes or synthetic game information.
 */
public final class HudSlatePreviewEmphasis {
    private HudSlatePreviewEmphasis() { }

    /**
     * The metrics share the Status footprint but can appear independently.
     * The Module List is visually empty when no real eligible rows exist.
     * All other widgets follow their own enabled feature flag.
     */
    public static boolean visible(HudEditorControls.Item item,
                                  boolean enabled,boolean metricsVisible,
                                  boolean listHasRows) {
        if(item==null)throw new IllegalArgumentException("Slate F7 preview widget");
        if(item==HudEditorControls.Item.STATUS)
            return enabled||metricsVisible;
        if(item==HudEditorControls.Item.MODULE_LIST)
            return enabled&&listHasRows;
        return enabled;
    }

    /**
     * Active output is byte-for-byte the original skin palette. Inactive
     * Slate previews retain their panel footprint, but lose accent-colored
     * marks in favor of translucent neutral ink and tracks.
     */
    public static int color(HudThemeTokens tokens,int role,boolean visible) {
        if(tokens==null||role<HudPreviewArt.PANEL||role>HudPreviewArt.TRACK)
            throw new IllegalArgumentException("Slate F7 preview palette");
        if(role==HudPreviewArt.PANEL)return tokens.panel;
        if(visible) {
            switch(role) {
                case HudPreviewArt.PRIMARY: return tokens.accent;
                case HudPreviewArt.SECONDARY: return tokens.secondary;
                case HudPreviewArt.TRACK: return tokens.track;
                default: return tokens.muted;
            }
        }
        if(role==HudPreviewArt.TRACK)
            return (tokens.track&0x00FFFFFF)|0x99000000;
        int alpha=role==HudPreviewArt.MUTED?0x77:0xAA;
        return (tokens.muted&0x00FFFFFF)|(alpha<<24);
    }
}
