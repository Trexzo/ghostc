package dev.ghostfusion.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Stable IDs, deterministic order, fault containment, no callbacks off the owner thread. */
public final class ModuleRegistry {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    public enum State { DISABLED, ENABLED, FAULTED }
    public static final class Entry {
        private final GhostModule module;
        private final LinkedHashMap<String, Setting<?>> settings = new LinkedHashMap<String, Setting<?>>();
        private State state = State.DISABLED;
        private int keybind;
        private String lastFault = "";
        private boolean transitioning;
        private Entry(GhostModule module) { this.module = module; }
        public GhostModule module() { return module; }
        public State state() { return state; }
        public int keybind() { return keybind; }
        public String lastFault() { return lastFault; }
        public Map<String, Setting<?>> settings() { return Collections.unmodifiableMap(settings); }
    }
    private final Thread owner = Thread.currentThread();
    private final LinkedHashMap<String, Entry> modules = new LinkedHashMap<String, Entry>();
    private boolean shutdown;
    private int lifecycleCallbacks;
    private void beginLifecycle(Entry e) {
        if (e.transitioning) throw new IllegalStateException("Recursive module lifecycle: " + e.module.id());
        e.transitioning = true;
        lifecycleCallbacks++;
    }
    private void endLifecycle(Entry e) {
        e.transitioning = false;
        lifecycleCallbacks--;
    }
    public static String validateId(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid ID: " + id);
        return id;
    }
    public void assertOwnerThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Only the client owner thread may mutate/dispatch");
    }
    private void assertLive() { assertOwnerThread(); if (shutdown) throw new IllegalStateException("Core shut down"); }
    public void register(GhostModule module, int defaultKey, Setting<?>... settings) {
        assertLive();
        if (module == null || module.category() == null || module.displayName() == null) throw new IllegalArgumentException("Missing module metadata");
        String id = validateId(module.id());
        if (modules.containsKey(id)) throw new IllegalArgumentException("Duplicate module ID: " + id);
        validateKey(defaultKey);
        if (defaultKey != 0 && isBound(defaultKey)) throw new IllegalArgumentException("Duplicate keybind: " + defaultKey);
        Entry entry = new Entry(module);
        entry.keybind = defaultKey;
        if (settings != null) for (Setting<?> s : settings) {
            if (s == null || entry.settings.put(s.id(), s) != null) throw new IllegalArgumentException("Duplicate/invalid setting");
        }
        modules.put(id, entry);
    }
    public Entry get(String id) {
        validateId(id);
        Entry e = modules.get(id);
        if (e == null) throw new IllegalArgumentException("Unknown module: " + id);
        return e;
    }
    public List<Entry> all() { return Collections.unmodifiableList(new ArrayList<Entry>(modules.values())); }
    public static void validateKey(int key) {
        if (key < 0 || key > 65535 || ReservedKeys.reserved(key))
            throw new IllegalArgumentException("Invalid or reserved keycode: " + key);
    }
    private boolean isBound(int key) {
        for (Entry e : modules.values()) if (e.keybind == key) return true;
        return false;
    }
    public void bind(String id, int key) {
        assertLive(); validateKey(key);
        Entry target = get(id);
        if (key != 0) for (Entry e : modules.values())
            if (e != target && e.keybind == key) throw new IllegalArgumentException("Key already bound: " + key);
        target.keybind = key;
    }
    public void setEnabled(String id, boolean enabled) {
        assertLive(); Entry e = get(id);
        // State is not committed until a lifecycle callback returns.
        // Re-entering it while pending would otherwise recurse indefinitely.
        if(e.transitioning) throw new IllegalStateException("Recursive module lifecycle: " + id);
        if (enabled) {
            if (e.state == State.ENABLED) return;
            if (e.state == State.FAULTED) throw new IllegalStateException("Reset fault first: " + id);
            beginLifecycle(e);
            try {
                try { e.module.onEnable(); e.state = State.ENABLED; }
                catch (Throwable ex) { fault(e, ex, true); }
            } finally { endLifecycle(e); }
        } else {
            if (e.state == State.ENABLED) {
                beginLifecycle(e);
                try {
                    try { e.module.onDisable(); e.state = State.DISABLED; }
                    // onDisable was already invoked; never invoke it twice.
                    catch (Throwable ex) { fault(e, ex, false); }
                } finally { endLifecycle(e); }
            } else if (e.state == State.FAULTED) { e.state = State.DISABLED; e.lastFault = ""; }
        }
    }
    public void toggleKey(int key) {
        assertLive(); if (key == 0) return;
        for (Entry e : modules.values()) if (e.keybind == key) {
            // A faulted keybind clears the fault without running the
            // previously crashing onEnable. A second press opts in to retry.
            setEnabled(e.module.id(), e.state == State.DISABLED); return;
        }
    }
    public void dispatch(GameEvent event) {
        assertLive();
        if (event == null) throw new IllegalArgumentException("event is null");
        for (Entry e : modules.values()) if (e.state == State.ENABLED) {
            try { e.module.onEvent(event); }
            catch (Throwable ex) { fault(e, ex, true); }
        }
    }
    private void fault(Entry e, Throwable cause, boolean cleanup) {
        // Event dispatch can reach this without an active lifecycle scope.
        // Acquire one so a faulty cleanup cannot recursively disable itself.
        boolean acquired = !e.transitioning;
        if(acquired) beginLifecycle(e);
        try {
            if(cleanup) {
                try { e.module.onDisable(); } catch (Throwable ignored) { /* isolation */ }
            }
            e.state = State.FAULTED;
        // Exception messages may contain private chat, server info or account
        // data supplied by third-party modules. Retain only the class name.
        String type = cause.getClass().getSimpleName();
        if (type.isEmpty()) type = "Throwable";
            e.lastFault = type.length() > 60 ? type.substring(0,60) : type;
        } finally {
            if(acquired) endLifecycle(e);
        }
    }
    public void shutdown() {
        assertOwnerThread(); if (shutdown) return;
        if(lifecycleCallbacks!=0)
            throw new IllegalStateException("Cannot shutdown during module lifecycle");
        // Seal the registry BEFORE invoking arbitrary module callbacks.
        // They cannot re-enable peers, register modules or change keybinds.
        shutdown = true;
        for (Entry e : modules.values()) if (e.state == State.ENABLED) {
            beginLifecycle(e);
            try {
                try { e.module.onDisable(); e.state = State.DISABLED; }
                catch (Throwable ex) { fault(e, ex, false); }
            } finally { endLifecycle(e); }
        }
    }
}
