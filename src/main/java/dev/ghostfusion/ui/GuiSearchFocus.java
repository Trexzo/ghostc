package dev.ghostfusion.ui;

/**
 * Keyboard ownership of the ClickGUI search textbox. Text-field navigation
 * must not accidentally trigger module toggles or selection shortcuts.
 * Independent of Minecraft/LWJGL, screen size and persisted profiles.
 */
public final class GuiSearchFocus {
    public enum Route { PASS, EDIT, RELEASE }
    private GuiSearchFocus() {}

    /**
     * A focused search field owns all keys except the explicit Tab transfer.
     * Enter/Numpad Enter exits editing without triggering a module toggle.
     * Escape/F7 are handled by the screen before this decision.
     */
    public static Route route(boolean focused,boolean enter,boolean tab) {
        if(!focused || tab)return Route.PASS;
        return enter?Route.RELEASE:Route.EDIT;
    }
}
