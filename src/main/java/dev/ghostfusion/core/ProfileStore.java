package dev.ghostfusion.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Small schema-versioned profiles, typed validation before any mutation, atomic replace and .bak. */
public final class ProfileStore {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,48}");
    private final Path directory;
    private final ModuleRegistry registry;
    public ProfileStore(Path directory, ModuleRegistry registry) { this.directory = directory; this.registry = registry; }
    private Path path(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid profile name");
        return directory.resolve(name + ".properties");
    }
    private static final class Staged {
        boolean enabled;
        int key;
        Map<Setting<?>, String> settings = new LinkedHashMap<Setting<?>, String>();
    }
    public void save(String name) throws java.io.IOException {
        registry.assertOwnerThread();
        Path destination = path(name);
        Files.createDirectories(directory);
        Properties data = new Properties();
        data.setProperty("schema", "1");
        for (ModuleRegistry.Entry e : registry.all()) {
            String pre = "module." + e.module().id() + ".";
            data.setProperty(pre + "enabled", String.valueOf(e.state() == ModuleRegistry.State.ENABLED));
            data.setProperty(pre + "key", Integer.toString(e.keybind()));
            for (Setting<?> s : e.settings().values())
                data.setProperty(pre + "setting." + s.id(), s.serialize());
        }
        ConfigIntegrity.seal(data);
        Path temp = Files.createTempFile(directory, name + "-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "GhostFusion schema=1"); }
            ConfigIntegrity.rotateVerifiedBackup(destination,
                new java.util.function.Predicate<Properties>() {
                    public boolean test(Properties candidate) {
                        try { stage(candidate); return true; }
                        catch (RuntimeException invalid) { return false; }
                    }
                });
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public void load(String name) throws java.io.IOException {
        registry.assertOwnerThread();
        loadFile(path(name));
    }
    /**
     * Retains the original strict load contract; opt-in startup path recovers
     * from a validated .bak, preserving a failed primary as .corrupt.
     */
    public boolean loadRecovering(String name) throws java.io.IOException {
        registry.assertOwnerThread();
        Path primary=path(name);
        try {
            loadFile(primary);
            return false;
        } catch(java.io.IOException | RuntimeException primaryFailure) {
            Path backup=primary.resolveSibling(primary.getFileName()+".bak");
            if(!Files.exists(backup))throw primaryFailure;
            try {
                ConfigIntegrity.restoreBackup(primary,
                    new java.util.function.Predicate<Properties>() {
                        public boolean test(Properties candidate) {
                            try { stage(candidate); return true; }
                            catch (RuntimeException invalid) { return false; }
                        }
                    });
                // Only apply settings after the verified backup bytes have
                // been promoted; failed restoration leaves live state intact.
                loadFile(primary);
                return true;
            } catch(java.io.IOException | RuntimeException backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
                throw primaryFailure;
            }
        }
    }
    /** Typed preflight shared by loading and backup admission; no live mutations. */
    private LinkedHashMap<String, Staged> stage(Properties data) {
        if (!"1".equals(data.getProperty("schema"))) throw new IllegalArgumentException("Unsupported profile schema");
        LinkedHashMap<String, Staged> plan = new LinkedHashMap<String, Staged>();
        for (ModuleRegistry.Entry e : registry.all()) {
            String id = e.module().id(), pre = "module." + id + ".";
            Staged stage = new Staged();
            String enabled = data.getProperty(pre + "enabled", "false");
            if (!"true".equals(enabled) && !"false".equals(enabled)) throw new IllegalArgumentException("Invalid enabled: " + id);
            stage.enabled = Boolean.parseBoolean(enabled);
            try { stage.key = Integer.parseInt(data.getProperty(pre + "key", "0")); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid key: " + id, ex); }
            // Preflight the central reserved-key policy before applying any
            // enables, settings or keybinds to the live registry.
            ModuleRegistry.validateKey(stage.key);
            for (Setting<?> s : e.settings().values()) {
                String raw = data.getProperty(pre + "setting." + s.id());
                if (raw != null) { s.parse(raw); stage.settings.put(s, raw); }
            }
            plan.put(id, stage);
        }
        java.util.HashSet<Integer> keys = new java.util.HashSet<Integer>();
        for (Staged st : plan.values()) if (st.key != 0 && !keys.add(st.key)) throw new IllegalArgumentException("Duplicate profile keybind");
        return plan;
    }
    private void loadFile(Path source) throws java.io.IOException {
        LinkedHashMap<String, Staged> plan=stage(ConfigIntegrity.read(source));
        LinkedHashMap<String, Staged> old = new LinkedHashMap<String, Staged>();
        for (ModuleRegistry.Entry e : registry.all()) {
            Staged st = new Staged(); st.enabled = e.state() == ModuleRegistry.State.ENABLED;
            st.key = e.keybind();
            for (Setting<?> s : e.settings().values()) st.settings.put(s, s.serialize());
            old.put(e.module().id(), st);
        }
        try { apply(plan); }
        catch (RuntimeException ex) { try { apply(old); } catch (RuntimeException ignored) { } throw ex; }
    }
    private void apply(LinkedHashMap<String, Staged> plan) {
        for (String id : plan.keySet()) registry.setEnabled(id, false);
        for (String id : plan.keySet()) registry.bind(id, 0);
        for (Map.Entry<String, Staged> it : plan.entrySet()) {
            registry.bind(it.getKey(), it.getValue().key);
            for (Map.Entry<Setting<?>, String> s : it.getValue().settings.entrySet()) s.getKey().setFromText(s.getValue());
        }
        for (Map.Entry<String, Staged> it : plan.entrySet()) if (it.getValue().enabled) {
            registry.setEnabled(it.getKey(), true);
            if (registry.get(it.getKey()).state() != ModuleRegistry.State.ENABLED)
                throw new IllegalStateException("Enable failed for " + it.getKey());
        }
    }
}
