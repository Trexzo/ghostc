package dev.ghostfusion.ui;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import java.util.List;

/** Mutation gateway for GUI input; no direct access to mutable module internals from widgets. */
public final class UiActions {
    private final ModuleRegistry modules;
    private final Runnable persist;
    public UiActions(ModuleRegistry modules, Runnable persist) {
        if (modules == null || persist == null) throw new IllegalArgumentException("modules/persist");
        this.modules = modules; this.persist = persist;
    }
    public void toggle(String id) {
        modules.assertOwnerThread();
        ModuleRegistry.Entry e = modules.get(id);
        modules.setEnabled(id, e.state() != ModuleRegistry.State.ENABLED);
        persist.run();
    }
    public void bind(String id, int key) {
        modules.assertOwnerThread();
        modules.bind(id, key);
        persist.run();
    }
    public void bool(String id, String settingId) {
        Setting<?> s = setting(id, settingId);
        if (!(s instanceof Setting.Bool)) throw new IllegalArgumentException("Not a bool");
        s.setFromText(Boolean.toString(!((Setting.Bool) s).get()));
        persist.run();
    }
    public void cycle(String id, String settingId) {
        Setting<?> s = setting(id, settingId);
        if (!(s instanceof Setting.Choice)) throw new IllegalArgumentException("Not a choice");
        Setting.Choice choice = (Setting.Choice) s;
        List<String> values = choice.options();
        int idx = values.indexOf(choice.get());
        choice.setFromText(values.get((idx + 1) % values.size()));
        persist.run();
    }
    public void fraction(String id, String settingId, double fraction) {
        Setting<?> s = setting(id, settingId);
        if (!(s instanceof Setting.Number)) throw new IllegalArgumentException("Not a number");
        Setting.Number n = (Setting.Number) s;
        double bounded = UiState.clamp(fraction, 0, 1);
        double value = n.min() + (n.max() - n.min()) * bounded;
        n.setFromText(Double.toString(value));
        persist.run();
    }
    private Setting<?> setting(String id, String settingId) {
        modules.assertOwnerThread();
        Setting<?> s = modules.get(id).settings().get(settingId);
        if (s == null) throw new IllegalArgumentException("Unknown setting");
        return s;
    }
}
