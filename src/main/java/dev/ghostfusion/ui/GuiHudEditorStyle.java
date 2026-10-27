package dev.ghostfusion.ui;

/**
 * Read-only F7 HUD editor paint tokens. Slate must never inherit Indigo's
 * purple selection/tab colors; legacy themes retain their previous pixels.
 * Pure Java so the palette contract can be tested without launching Minecraft.
 */
public final class GuiHudEditorStyle {
    public final int selectedTab,selectedText,idleText;
    public final int previewSurround,previewCanvas,previewGrid,snapGuide,helpText;

    private static final GuiHudEditorStyle LEGACY=new GuiHudEditorStyle(
        0xFF575C8D,0xFFFFFFFF,0xFFBEC6D9,
        0xFF0D1220,0xFF192238,0x304E5D75,0x9966DDD0,0xFFBEC6D9);
    private static final GuiHudEditorStyle SLATE=new GuiHudEditorStyle(
        0xFF33424A,0xFFF1F4F6,0xFF98A5AF,
        0xFF101418,0xFF1C252B,0x293B4D57,0x99A7D3E0,0xFF98A5AF);

    private GuiHudEditorStyle(int selectedTab,int selectedText,int idleText,
            int previewSurround,int previewCanvas,int previewGrid,
            int snapGuide,int helpText) {
        this.selectedTab=selectedTab;
        this.selectedText=selectedText;
        this.idleText=idleText;
        this.previewSurround=previewSurround;
        this.previewCanvas=previewCanvas;
        this.previewGrid=previewGrid;
        this.snapGuide=snapGuide;
        this.helpText=helpText;
    }
    public static GuiHudEditorStyle of(UiState.Theme theme) {
        if(theme==UiState.Theme.SLATE)return SLATE;
        if(theme==UiState.Theme.GRAPHITE||theme==UiState.Theme.INDIGO)return LEGACY;
        throw new IllegalArgumentException("HUD editor theme");
    }
}
