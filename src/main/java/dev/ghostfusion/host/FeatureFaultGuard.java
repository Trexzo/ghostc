package dev.ghostfusion.host;

import dev.ghostfusion.core.ModuleRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
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
    // The HUD queries this every frame. Update only when a fault is added
    // or successfully recovered, never allocate/copy the fault map on paint.
    private String latestWarning="";
    private void refreshLatestWarning() {
        String id=null,type=null;
        for(Map.Entry<String,String> candidate:recent.entrySet()) {
            id=candidate.getKey(); type=candidate.getValue();
        }
        latestWarning=id==null?"":"Module disabled: "+id+" ("+type+")";
    }
    /** Cached warning for the latest unresolved fault, or empty on recovery. */
    public String latestFaultWarning() {
        registry.assertOwnerThread();
        return latestWarning;
    }
    // One callback for a given module may not re-enter itself, directly or
    // through another module. Peer callbacks may still nest normally.
    private final HashSet<String> executing = new HashSet<String>();
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
        // Throw to the already-running parent callback, whose ordinary fault
        // boundary quarantines the offending feature. Do not create an
        // additional lifecycle callback or recursively invoke user code.
        if (!executing.add(stableId))
            throw new IllegalStateException("Recursive feature callback");
        try {
            try {
                callback.run();
                if(recent.remove(stableId)!=null)refreshLatestWarning();
                return true;
            } catch (RuntimeException ex) {
                String type=ex.getClass().getSimpleName();
                // Anonymous RuntimeException subclasses have no simple name.
                // Keep fault metadata nonempty without retaining messages.
                if (type.isEmpty()) type="RuntimeException";
                if (type.length() > 60) type=type.substring(0,60);
                // The callback may have legitimately shut down the host
                // (and therefore its registry) before throwing. It is
                // already quarantined if its Entry was disabled/faulted
                // during that transition. Do not turn the original frame
                // fault into a second "Core shut down" exception.
                if(entry.state() == ModuleRegistry.State.ENABLED) {
                    try { registry.setEnabled(stableId,false); }
                    catch(RuntimeException disableFailure) {
                        // During an onDisable lifecycle transition the
                        // registry may still report ENABLED. Propagate the
                        // original exception to that lifecycle's own fault
                        // boundary; never report quarantine while active.
                        if(entry.state() == ModuleRegistry.State.ENABLED) {
                            if(disableFailure != ex) ex.addSuppressed(disableFailure);
                            throw ex;
                        }
                    }
                }
                // LinkedHashMap preserves the first insertion slot on put.
                // A retry that fails again is the NEWEST unresolved fault:
                // move its id to the tail before applying bounded eviction.
                // Otherwise the HUD can show an older peer failure, and
                // a recently retried feature can be prematurely evicted.
                recent.remove(stableId);
                recent.put(stableId,type);
                if (recent.size() > 32) recent.remove(recent.keySet().iterator().next());
                refreshLatestWarning();
                try { listener.report(stableId,type); }
                catch (RuntimeException ignored) { /* report must not crash the caller */ }
                return false;
            }
        } finally {
            // Also release on serious JVM Error or a fault-report failure.
            // A later explicit module recovery cannot inherit this latch.
            executing.remove(stableId);
        }
    }
    public Map<String,String> recentFaults() {
        registry.assertOwnerThread();
        return Collections.unmodifiableMap(new LinkedHashMap<String,String>(recent));
    }
}
