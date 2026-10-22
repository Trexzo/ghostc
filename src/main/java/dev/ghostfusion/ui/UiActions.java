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
    /** Retain registry validation even for an unchanged key. Only real
     * assignment changes require rewriting the profile. */
    public void bind(String id, int key) {
        modules.assertOwnerThread();
        int previous = modules.get(id).keybind();
        modules.bind(id, key);
        if(previous != key)persist.run();
    }
    public void bool(String id, String settingId) {
        Setting<?> s = setting(id, settingId);
        if (!(s instanceof Setting.Bool)) throw new IllegalArgumentException("Not a bool");
        s.setFromText(Boolean.toString(!((Setting.Bool) s).get()));
        persist.run();
    }
    /** Preserve the existing forward-cycle API for mouse and Enter actions. */
    public void cycle(String id, String settingId) {
        cycle(id,settingId,1);
    }
    /** Keyboard Left/Right cycles backward/forward through validated choices. */
    public boolean cycle(String id,String settingId,int direction) {
        if(direction!=1 && direction!=-1)
            throw new IllegalArgumentException("choice direction");
        Setting.Choice choice=checkedChoice(id,settingId);
        List<String> values=choice.options();
        int current=values.indexOf(choice.get());
        if(current<0)throw new IllegalStateException("Unknown current choice");
        return choose(choice,values.get((current+direction+values.size())%values.size()));
    }
    /** Keyboard Home/End chooses the first/last ordered option. */
    public boolean choiceEdge(String id,String settingId,boolean last) {
        Setting.Choice choice=checkedChoice(id,settingId);
        List<String> values=choice.options();
        return choose(choice,values.get(last?values.size()-1:0));
    }
    private Setting.Choice checkedChoice(String id,String settingId) {
        Setting<?> s=setting(id,settingId);
        if(!(s instanceof Setting.Choice))
            throw new IllegalArgumentException("Not a choice");
        return (Setting.Choice)s;
    }
    /** Persist one changed option, never an already-selected one. */
    private boolean choose(Setting.Choice choice,String value) {
        if(value.equals(choice.get()))return false;
        choice.setFromText(value);
        persist.run();
        return true;
    }
    /** Explicit single-setting default restore. Unchanged state never writes
     * a new profile; focus/validation is owned by the ClickGUI. */
    public boolean reset(String id,String settingId) {
        Setting<?> s=setting(id,settingId);
        if(!s.resetToDefault())return false;
        persist.run();
        return true;
    }
    /** Persist only if normalization actually changed the numeric setting.
     * This also avoids redundant disk writes from repeated endpoint presses. */
    public void fraction(String id, String settingId, double fraction) {
        if(previewFraction(id,settingId,fraction))persist.run();
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
