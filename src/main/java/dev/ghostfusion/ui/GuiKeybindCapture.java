package dev.ghostfusion.ui;

/**
 * Decides whether the module list can hand keyboard ownership to the keybind
 * capture editor. Used by the B shortcut; mouse capture remains unchanged.
 * This policy is independent of Minecraft, LWJGL, and saved config files.
 */
public final class GuiKeybindCapture {
    private GuiKeybindCapture() {}

    public static boolean arm(boolean modulesPage,boolean searchFocused,
                              int settingFocus,String selected,boolean keyRepeat) {
        return modulesPage && !searchFocused && settingFocus<0 &&
               selected!=null && !selected.isEmpty() && !keyRepeat;
    }
}
