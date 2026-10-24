package dev.ghostfusion.core;

import java.util.Arrays;

/** Fixed-memory, owner-thread physical key edges. Suppressed GUI/chat keys
 * remain pressed until release or an explicit focus/world reset. */
public final class InputRouter {
    // Key validation admits an unsigned 16-bit domain. No Integer boxing.
    private final long[] pressed = new long[1024];
    private final ModuleRegistry registry;
    public InputRouter(ModuleRegistry registry) {
        if(registry==null)throw new IllegalArgumentException("registry");
        this.registry=registry;
    }
    private boolean press(int key) {
        int slot=key >>> 6;
        long mask=1L << (key & 63);
        long old=pressed[slot];
        if((old & mask)!=0)return false;
        pressed[slot]=old | mask;
        return true;
    }
    private void release(int key) {
        int slot=key >>> 6;
        pressed[slot] &= ~(1L << (key & 63));
    }
    public void keyEvent(int key,boolean down,boolean typingOrModal) {
        registry.assertOwnerThread();
        // F7 is a global shortcut and cannot bind a module.
        if (key == 0 || ReservedKeys.reserved(key)) return;
        ModuleRegistry.validateKey(key); // validate before bitmap indexing
        if(down) {
            if(press(key) && !typingOrModal)registry.toggleKey(key);
        } else release(key);
    }
    public void clearPressed() {
        registry.assertOwnerThread();
        Arrays.fill(pressed,0L);
    }
}
