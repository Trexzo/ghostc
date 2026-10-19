package dev.ghostfusion.host;

import dev.ghostfusion.core.ModuleRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-thread, fail-closed boundary for one feature callback.
 * RuntimeException quarantines only that module; JVM Errors are deliberately
 * not swallowed. No user data, exception message or stack trace is retained.
 */
public final class FeatureFaultGuard {
    public interface OnFault {
        void report(String stableId, String exceptionType);
    }
    private final ModuleRegistry registry;
    private final OnFault listener;
    private final LinkedHashMap<String, String> recent = new LinkedHashMap<String,String>();
    public FeatureFaultGuard(ModuleRegistry registry, OnFault listener) {
        if (registry == null || listener == null) throw new IllegalArgumentException("fault guard dependencies");
        this.registry=registry;
        this.listener=listener;
    }
    public boolean execute(String stableId, Runnable callback) {
        registry.assertOwnerThread();
        if (callback == null) throw new IllegalArgumentException("callback");
        ModuleRegistry.Entry entry=registry.get(stableId);
        if (entry.state() != ModuleRegistry.State.ENABLED) return false;
        try {
            callback.run();
            recent.remove(stableId);
            return true;
        } catch (RuntimeException ex) {
            String type=ex.getClass().getSimpleName();
            if (type.length() > 60) type=type.substring(0,60);
            registry.setEnabled(stableId,false);
            recent.put(stableId,type);
            if (recent.size() > 32) recent.remove(recent.keySet().iterator().next());
            try { listener.report(stableId,type); }
            catch (RuntimeException ignored) { /* report must not crash the caller */ }
            return false;
        }
    }
    public Map<String,String> recentFaults() {
        registry.assertOwnerThread();
        return Collections.unmodifiableMap(new LinkedHashMap<String,String>(recent));
    }
}
