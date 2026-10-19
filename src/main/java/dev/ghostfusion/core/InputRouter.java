package dev.ghostfusion.core;

import java.util.HashSet;
import java.util.Set;

/** Edge-triggered module toggles. Suppresses input while text/chat/menu has focus. */
public final class InputRouter {
    private final ModuleRegistry registry;
    private final Set<Integer> pressed = new HashSet<Integer>();
    public InputRouter(ModuleRegistry registry) { this.registry = registry; }
    public void keyEvent(int key, boolean down, boolean typingOrModal) {
        registry.assertOwnerThread();
        // A global shortcut is legal as an incoming physical key event but
        // is NOT valid as an assignable module key. Ignore it without throwing
        // or changing any module's pressed-key state.
        if (key == 0 || ReservedKeys.reserved(key)) return;
        ModuleRegistry.validateKey(key);
        if (down) {
            if (pressed.add(key) && !typingOrModal) registry.toggleKey(key);
        } else pressed.remove(key);
    }
    public void clearPressed() { registry.assertOwnerThread(); pressed.clear(); }
}
