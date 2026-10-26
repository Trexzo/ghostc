package dev.ghostfusion.host;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.services.SessionServices;

/**
 * Reversible Panic / Disable All for a local Forge client. It preserves config
 * files, local settings, account state, and Minecraft's native input code.
 * This is NOT an injector unload, self-destruct, or trace eraser.
 */
public final class PanicController {
    private PanicController() { }

    /** Only the creating owner thread may call this. Returns modules disabled. */
    public static int disableAll(final HostRuntime host) {
        if(host==null)throw new IllegalArgumentException("null host");
        final ModuleRegistry registry=host.modules();
        registry.assertOwnerThread();
        if(host.isClosed())return 0;
        // Reject from lifecycle callbacks before altering focus or registry.
        registry.assertShutdownAllowed();
        // Clear owned physical key edges first. The Forge listener handles
        // native key-up and ignores key repeats on the dedicated shortcut.
        host.inputFocus(true);
        int changed=registry.disableAllForPanic(new Runnable() {
            public void run() {
                final SessionServices session=host.services();
                // Completion callbacks may execute arbitrary application code.
                // Registry's Panic enable embargo covers the entire sequence.
                session.packets().reset();
                session.inventory().reset();
                session.actions().reset();
                session.rotations().clear();
            }
        });
        // Panic is explicit and reversible: disabling modules is persisted
        // through the normal safe profile path, not destructive deletion.
        host.persist();
        return changed;
    }
}
