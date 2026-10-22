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
        // Faulted modules cannot be re-enabled until their fault marker is
        // explicitly reset. One press resets to DISABLED, a second enables.
        // Never invoke onEnable while the prior fault is still latched.
        modules.setEnabled(id, e.state() == ModuleRegistry.State.DISABLED);
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
    /** Explicit single-setting default restore. Unchanged state never writes
     * a new profile; focus/validation is owned by the ClickGUI. */
    public boolean reset(String id,String settingId) {
        Setting<?> s=setting(id,settingId);
        if(!s.resetToDefault())return false;
        persist.run();
        return true;
    }
    public void fraction(String id, String settingId, double fraction) {
        previewFraction(id,settingId,fraction);
        persist.run();
    }
    /** Update a captured slider on the owner thread without writing a file
     * for every pointer movement. The GUI persists once when capture ends. */
    public boolean previewFraction(String id,String settingId,double fraction) {
        Setting<?> s = setting(id, settingId);
        if (!(s instanceof Setting.Number)) throw new IllegalArgumentException("Not a number");
        Setting.Number n = (Setting.Number) s;
        double value = NumericSliderScale.value(n.min(),n.max(),fraction);
        if(Double.compare(n.get(),value)==0)return false;
        n.setFromText(Double.toString(value));
        return true;
    }
    /** Persist an already-applied pointer capture exactly once. */
    public void commitSlider() {
        modules.assertOwnerThread();
        persist.run();
    }
    private Setting<?> setting(String id, String settingId) {
        modules.assertOwnerThread();
        Setting<?> s = modules.get(id).settings().get(settingId);
        if (s == null) throw new IllegalArgumentException("Unknown setting");
        return s;
    }
}
