package dev.ghostfusion.host;

import dev.ghostfusion.core.ConfigIntegrity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

/**
 * Bounded owner-thread counters for local runtime acceptance.
 * Never records server addresses, names, coordinates, chat or packet contents.
 */
public final class RuntimeDiagnostics {
    public enum Counter {
        CLIENT_TICK, WORLD_ENTER, WORLD_LEAVE, DISCONNECT,
        FOCUS_LOST, FOCUS_GAINED, SNAPSHOT,
        AIM_APPLIED, EXTRA_ATTACK, EXTRA_USE, INPUT_SUPPRESSED, FEATURE_FAULT,
        GUI_OPEN, GUI_FRAME, GUI_CLOSE, OVERLAY_FRAME, WORLD_RENDER_FRAME,
        KEY_CALLBACK, MOUSE_CALLBACK,
        ESP_RENDER_FRAME, ESP_DRAWN_PLAYER, ESP_DRAWN_SKELETON, ESP_INVALID_GEOMETRY,
        // R3.44: opt-in *grant-frame* totals, not native action counts.
        MODULE_WTAP_PREEMPTED_SPRINT, MODULE_ARMOR_PREEMPTED_SORTER,
        MODULE_ARMOR_GRANT, MODULE_SORTER_GRANT, MODULE_CHEST_GRANT,
        MODULE_GAMEPLAY_GUI_DENIED, MODULE_GAMEPLAY_WINDOW_DENIED,
        ACTION_CHEST_ATTEMPT, ACTION_CHEST_LOCAL_CHANGE, ACTION_CHEST_TIMEOUT, ACTION_CHEST_CANCELLED, ACTION_CHEST_REJECTED, ACTION_CHEST_DISPATCHED, ACTION_CHEST_LOCAL_COMPLETE,
        ACTION_SORTER_ATTEMPT, ACTION_SORTER_LOCAL_CHANGE, ACTION_SORTER_TIMEOUT, ACTION_SORTER_CANCELLED, ACTION_SORTER_REJECTED, ACTION_SORTER_DISPATCHED, ACTION_SORTER_LOCAL_COMPLETE,
        ACTION_ARMOR_ATTEMPT, ACTION_ARMOR_LOCAL_CHANGE, ACTION_ARMOR_TIMEOUT, ACTION_ARMOR_CANCELLED, ACTION_ARMOR_REJECTED, ACTION_ARMOR_DISPATCHED, ACTION_ARMOR_LOCAL_COMPLETE,
        ACTION_WTAP_ATTEMPT, ACTION_WTAP_LOCAL_CHANGE, ACTION_WTAP_TIMEOUT, ACTION_WTAP_CANCELLED, ACTION_WTAP_REJECTED, ACTION_WTAP_DISPATCHED, ACTION_WTAP_LOCAL_COMPLETE,
        ACTION_CLICKER_ATTEMPT, ACTION_CLICKER_LOCAL_CHANGE, ACTION_CLICKER_TIMEOUT, ACTION_CLICKER_CANCELLED, ACTION_CLICKER_REJECTED, ACTION_CLICKER_DISPATCHED, ACTION_CLICKER_LOCAL_COMPLETE,
        ACTION_BLOCKHIT_ATTEMPT, ACTION_BLOCKHIT_LOCAL_CHANGE, ACTION_BLOCKHIT_TIMEOUT, ACTION_BLOCKHIT_CANCELLED, ACTION_BLOCKHIT_REJECTED, ACTION_BLOCKHIT_DISPATCHED, ACTION_BLOCKHIT_LOCAL_COMPLETE,
        // R3.54: TriggerBot key pulses are not AutoClicker pulses or server hits.
        ACTION_TRIGGER_ATTEMPT, ACTION_TRIGGER_LOCAL_CHANGE, ACTION_TRIGGER_TIMEOUT, ACTION_TRIGGER_CANCELLED, ACTION_TRIGGER_REJECTED, ACTION_TRIGGER_DISPATCHED, ACTION_TRIGGER_LOCAL_COMPLETE,
        ACTION_JUMPRESET_ATTEMPT, ACTION_JUMPRESET_LOCAL_CHANGE, ACTION_JUMPRESET_TIMEOUT, ACTION_JUMPRESET_CANCELLED, ACTION_JUMPRESET_REJECTED, ACTION_JUMPRESET_DISPATCHED, ACTION_JUMPRESET_LOCAL_COMPLETE,
        ACTION_AUTOROD_ATTEMPT, ACTION_AUTOROD_LOCAL_CHANGE, ACTION_AUTOROD_TIMEOUT, ACTION_AUTOROD_CANCELLED, ACTION_AUTOROD_REJECTED, ACTION_AUTOROD_DISPATCHED, ACTION_AUTOROD_LOCAL_COMPLETE,
        ACTION_AUTOSNOWBALL_ATTEMPT, ACTION_AUTOSNOWBALL_LOCAL_CHANGE, ACTION_AUTOSNOWBALL_TIMEOUT, ACTION_AUTOSNOWBALL_CANCELLED, ACTION_AUTOSNOWBALL_REJECTED, ACTION_AUTOSNOWBALL_DISPATCHED, ACTION_AUTOSNOWBALL_LOCAL_COMPLETE,
        // Client-visible local observation ONLY, never a remote ACK or hit.
        PROJECTILE_ROD_LOCAL_HOOK, PROJECTILE_SNOWBALL_LOCAL_DECREASE,
        PROJECTILE_OBSERVATION_AMBIGUOUS, PROJECTILE_OBSERVATION_EXPIRED,
        PROJECTILE_OBSERVATION_INVALIDATED,
        // Observations are distinct: camera yields, native key output and
        // actual local Forge attack callbacks do NOT prove server hit ACKs.
        COMBAT_AIM_TRIGGER_YIELD, COMBAT_AIM_MOUSE_YIELD,
        COMBAT_NATIVE_ATTACK_CALLBACK,
        ACCEPT_GAMEPLAY, ACCEPT_CONCURRENT, ACCEPT_ARMOR, ACCEPT_SORTER,
        ACCEPT_CHEST, ACCEPT_GUI_BLOCK, ACCEPT_FOCUS_BLOCK, ACCEPT_WTAP_HOLD,
        ACCEPT_HOOK_ATTACHED, ACCEPT_S12_LOCAL, ACCEPT_S27_EXPLOSION
    }
    private final Thread owner = Thread.currentThread();
    private final EnumMap<Counter, Long> counters = new EnumMap<Counter,Long>(Counter.class);
    public RuntimeDiagnostics() {
        for (Counter c : Counter.values()) counters.put(c, 0L);
    }
    private void check() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("diagnostics owner thread");
    }
    public void record(Counter counter) {
        check();
        if (counter == null) throw new IllegalArgumentException("counter");
        long previous = counters.get(counter);
        counters.put(counter, previous == Long.MAX_VALUE ? Long.MAX_VALUE : previous + 1);
    }
    public long count(Counter counter) {
        check();
        if (counter == null) throw new IllegalArgumentException("counter");
        return counters.get(counter);
    }
    public Map<Counter,Long> snapshot() {
        check();
        return Collections.unmodifiableMap(new EnumMap<Counter,Long>(counters));
    }
    /** Called only on opt-in module enablement during disconnect or host close. */
    public void save(Path directory) throws IOException {
        saveReport(directory,null,null,null);
    }
    /** Preserve the established frame-only reporting API. */
    public void save(Path directory,FramePerformance.Snapshot perf) throws IOException {
        saveReport(directory,perf,null,null);
    }
    /** Optional anonymized frame and snapshot-work acceptance aggregates. */
    public void save(Path directory,FramePerformance.Snapshot perf,
                     SnapshotTelemetry.Snapshot snap) throws IOException {
        saveReport(directory,perf,snap,null);
    }
    /** Classified report used by the owner host; same existing aggregate keys. */
    public void saveWithSnapshotModes(Path directory,FramePerformance.Snapshot perf,
                     SnapshotTelemetry profiling) throws IOException {
        if(profiling==null)throw new IllegalArgumentException("snapshot profiler");
        saveReport(directory,perf,profiling.snapshot(),profiling);
    }
    /** Diagnostics are local counter snapshots, not sealed config files.
     * Never let a damaged, external-linked or oversized report replace the
     * last well-formed backup while publishing a fresh safe report. */
    private static final long MAX_REPORT_BYTES=65536L;
    // Schema 1 remains backward compatible with pre-R3.44 reports. New
    // counters are optional only when validating an older backup; all newly
    // written reports still contain them. Every unknown key is rejected.
    private static boolean optionalNewCounter(Counter counter) {
        // Old schema-1 backups lacked native-outcome counters entirely.
        // Only this fixed enum prefix is optional; arbitrary report keys
        // remain forbidden by the permitted-key whitelist.
        if(counter.name().startsWith("ACTION_") ||
           counter.name().startsWith("COMBAT_") ||
           counter.name().startsWith("ACCEPT_"))return true;
        switch(counter) {
            case MODULE_WTAP_PREEMPTED_SPRINT:
            case MODULE_ARMOR_PREEMPTED_SORTER:
            case MODULE_ARMOR_GRANT:
            case MODULE_SORTER_GRANT:
            case MODULE_CHEST_GRANT:
            case MODULE_GAMEPLAY_GUI_DENIED:
            case MODULE_GAMEPLAY_WINDOW_DENIED:
                return true;
            default:return false;
        }
    }
    private static boolean validBackupReport(Properties data) {
        if(!"1".equals(data.getProperty("schema")) ||
           !"counter-only-no-identity".equals(data.getProperty("scope")))
            return false;
        Set<String> permitted=new HashSet<String>();
        permitted.add("schema");permitted.add("scope");
        for(Counter counter:Counter.values()) {
            String key="count."+counter.name().toLowerCase(java.util.Locale.ROOT);
            permitted.add(key);
            // All expected counters are present in a genuine saved report.
            if(!data.containsKey(key) && !optionalNewCounter(counter))return false;
        }
        String[] perf={"frame_samples","min_fps","median_fps","p95_fps",
            "p99_fps","max_fps","heap_mib","peak_heap_mib",
            "memory_samples","suspends","invalid_intervals"};
        String[] snapshot={"samples","scanned","in_range","los_queries",
            "published","p50_us","p95_us","p99_us","max_us"};
        for(String field:perf)permitted.add("perf."+field);
        for(String field:snapshot)permitted.add("snap."+field);
        for(SnapshotTelemetry.Mode mode:SnapshotTelemetry.Mode.values()) {
            String prefix="snap.mode_"+mode.name().toLowerCase(java.util.Locale.ROOT)+"_";
            for(String field:snapshot)permitted.add(prefix+field);
        }
        for(String key:data.stringPropertyNames()) {
            if(!permitted.contains(key))return false;
            if(key.equals("schema")||key.equals("scope"))continue;
            String value=data.getProperty(key);
            // Strict nonnegative decimal counter. No arbitrary strings,
            // private fields or overflowing numeric content in a backup.
            if(value==null||!value.matches("(?:0|[1-9][0-9]{0,18})"))return false;
            try {Long.parseLong(value);}
            catch(NumberFormatException malformed){return false;}
        }
        return true;
    }
    /** Copy to a private bounded file, validate that exact snapshot, then
     * promote. The source is opened NOFOLLOW_LINKS even after preflight;
     * a symlink swap must never copy some unrelated local user's bytes. */
    private static void rotateValidBackup(Path primary) throws IOException {
        if(!Files.isRegularFile(primary,LinkOption.NOFOLLOW_LINKS) ||
           Files.size(primary)>MAX_REPORT_BYTES)return;
        Path staged=Files.createTempFile(primary.getParent(),"ghostc-diag-backup-",".tmp");
        try {
            try {
                long copied=0;
                try(InputStream in=Files.newInputStream(primary,LinkOption.NOFOLLOW_LINKS);
                    OutputStream out=Files.newOutputStream(staged)) {
                    byte[] block=new byte[4096];int n;
                    while((n=in.read(block))!=-1) {
                        if(n>MAX_REPORT_BYTES-copied)return;
                        out.write(block,0,n);copied+=n;
                    }
                }
                if(!validBackupReport(ConfigIntegrity.read(staged)))return;
            } catch(IOException | RuntimeException invalid) {
                // Preserve the existing last-known-good backup. Publishing
                // this session's new report is still allowed.
                return;
            }
            Path backup=primary.resolveSibling("diagnostics-last.properties.bak");
            try {
                Files.move(staged,backup,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException ex) {
                Files.move(staged,backup,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
    private void saveReport(Path directory,FramePerformance.Snapshot perf,
                     SnapshotTelemetry.Snapshot snap,SnapshotTelemetry profiling) throws IOException {
        check();
        if (directory == null) throw new IllegalArgumentException("directory");
        Files.createDirectories(directory);
        Properties props = new Properties();
        props.setProperty("schema", "1");
        props.setProperty("scope", "counter-only-no-identity");
        for (Counter key : Counter.values())
            props.setProperty("count." + key.name().toLowerCase(java.util.Locale.ROOT),
                              Long.toString(counters.get(key)));
        if(perf!=null) {
            props.setProperty("perf.frame_samples",Long.toString(perf.frames));
            props.setProperty("perf.min_fps",Integer.toString(perf.minFps));
            props.setProperty("perf.median_fps",Integer.toString(perf.medianFps));
            props.setProperty("perf.p95_fps",Integer.toString(perf.p95Fps));
            props.setProperty("perf.p99_fps",Integer.toString(perf.p99Fps));
            props.setProperty("perf.max_fps",Integer.toString(perf.maxFps));
            props.setProperty("perf.heap_mib",Long.toString(perf.heapMiB));
            props.setProperty("perf.peak_heap_mib",Long.toString(perf.peakHeapMiB));
            props.setProperty("perf.memory_samples",Long.toString(perf.memorySamples));
            props.setProperty("perf.suspends",Long.toString(perf.suspends));
            props.setProperty("perf.invalid_intervals",Long.toString(perf.invalidIntervals));
        }
        if(snap!=null) {
            props.setProperty("snap.samples",Long.toString(snap.samples));
            props.setProperty("snap.scanned",Long.toString(snap.scanned));
            props.setProperty("snap.in_range",Long.toString(snap.inRange));
            props.setProperty("snap.los_queries",Long.toString(snap.losQueries));
            props.setProperty("snap.published",Long.toString(snap.published));
            props.setProperty("snap.p50_us",Long.toString(snap.p50Us));
            props.setProperty("snap.p95_us",Long.toString(snap.p95Us));
            props.setProperty("snap.p99_us",Long.toString(snap.p99Us));
            props.setProperty("snap.max_us",Long.toString(snap.maxUs));
        }
        if(profiling!=null)for(SnapshotTelemetry.Mode mode:SnapshotTelemetry.Mode.values()){
            SnapshotTelemetry.Snapshot group=profiling.snapshot(mode);
            String prefix="snap.mode_"+mode.name().toLowerCase(java.util.Locale.ROOT)+"_";
            props.setProperty(prefix+"samples",Long.toString(group.samples));
            props.setProperty(prefix+"scanned",Long.toString(group.scanned));
            props.setProperty(prefix+"in_range",Long.toString(group.inRange));
            props.setProperty(prefix+"los_queries",Long.toString(group.losQueries));
            props.setProperty(prefix+"published",Long.toString(group.published));
            props.setProperty(prefix+"p50_us",Long.toString(group.p50Us));
            props.setProperty(prefix+"p95_us",Long.toString(group.p95Us));
            props.setProperty(prefix+"p99_us",Long.toString(group.p99Us));
            props.setProperty(prefix+"max_us",Long.toString(group.maxUs));
        }
        Path target = directory.resolve("diagnostics-last.properties");
        Path temp = Files.createTempFile(directory, "ghostc-diag-", ".tmp");
        try {
            try(OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "GhostC opt-in client counters; no identity or network data");
            }
            rotateValidBackup(target);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
