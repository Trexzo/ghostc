package dev.ghostfusion.features;

import dev.ghostfusion.core.ReservedKeys;

/**
 * Physical attack-hold admission gate. Losing world/focus/GUI lock requires
 * seeing an actual RELEASE in an active game before synthetic pulses resume.
 * Vanilla click handling is never blocked or modified.
 */
public final class PhysicalInputGate {
    private final Thread owner = Thread.currentThread();
    private long generation = -1;
    private boolean releaseRequired = true;
    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("input gate owner thread");
    }
    /**
     * attackCode may be negative for LWJGL mouse keybinds. Zero is unbound.
     * Key 65 is LWJGL F7, reserved as the GhostC ClickGUI shortcut.
     */
    public boolean allowed(long epoch, boolean ready, boolean focused, boolean guiOpen,
                           boolean attackDown, int attackCode, int useCode) {
        check();
        if (epoch < 0) throw new IllegalArgumentException("generation");
        if (epoch != generation) { generation = epoch; releaseRequired = true; }
        if (!ready || !focused || guiOpen) {
            releaseRequired = true;
            return false;
        }
        if (!attackDown) {
            releaseRequired = false;
            return false;
        }
        return !releaseRequired && attackCode != 0 && attackCode != ReservedKeys.CLICK_GUI &&
            attackCode != useCode;
    }
    public void reset() {
        check(); generation = -1; releaseRequired = true;
    }
}
