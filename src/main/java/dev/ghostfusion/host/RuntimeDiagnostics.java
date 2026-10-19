package dev.ghostfusion.host;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Bounded owner-thread counters for local runtime acceptance.
 * Never records server addresses, names, coordinates, chat or packet contents.
 */
public final class RuntimeDiagnostics {
    public enum Counter {
        CLIENT_TICK, WORLD_ENTER, WORLD_LEAVE, DISCONNECT,
        FOCUS_LOST, FOCUS_GAINED, SNAPSHOT,
        AIM_APPLIED, EXTRA_ATTACK, EXTRA_USE, INPUT_SUPPRESSED
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
        check();
        if (directory == null) throw new IllegalArgumentException("directory");
        Files.createDirectories(directory);
        Properties props = new Properties();
        props.setProperty("schema", "1");
        props.setProperty("scope", "counter-only-no-identity");
        for (Counter key : Counter.values())
            props.setProperty("count." + key.name().toLowerCase(java.util.Locale.ROOT),
                              Long.toString(counters.get(key)));
        Path target = directory.resolve("diagnostics-last.properties");
        Path temp = Files.createTempFile(directory, "ghostc-diag-", ".tmp");
        try {
            try(OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "GhostC opt-in client counters; no identity or network data");
            }
            if (Files.exists(target))
                Files.copy(target, directory.resolve("diagnostics-last.properties.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
