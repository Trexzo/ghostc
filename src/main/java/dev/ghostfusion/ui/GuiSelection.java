package dev.ghostfusion.ui;

import dev.ghostfusion.core.ModuleRegistry;
import java.util.List;

/** A ClickGUI selection is valid only while it is present in the visible filter. */
public final class GuiSelection {
    private GuiSelection() { }

    /**
     * Preserve a visible selected module; otherwise select the first visible
     * entry, or null if no results remain. Never retain an off-filter module
     * whose settings/keybind could otherwise be edited invisibly.
     */
    public static String reconcile(String current, List<ModuleRegistry.Entry> visible) {
        if (visible == null) throw new IllegalArgumentException("visible modules");
        if (current != null) {
            for (ModuleRegistry.Entry entry : visible) {
                if (entry == null) throw new IllegalArgumentException("null entry");
                if (current.equals(entry.module().id())) return current;
            }
        }
        if (visible.isEmpty()) return null;
        ModuleRegistry.Entry first = visible.get(0);
        if (first == null) throw new IllegalArgumentException("null entry");
        return first.module().id();
    }
}
