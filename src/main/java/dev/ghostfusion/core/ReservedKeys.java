package dev.ghostfusion.core;

/** Global client controls that must never be assigned to modules/profiles. */
public final class ReservedKeys {
    /** LWJGL 2 Keyboard.KEY_F7 in the Minecraft 1.8.9 Forge client. */
    public static final int CLICK_GUI = 65;
    private ReservedKeys() {}
    public static boolean reserved(int key) { return key == CLICK_GUI; }
}