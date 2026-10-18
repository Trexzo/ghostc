package dev.ghostfusion.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Atomic UI layout/preferences file separate from the module profile schema. */
public final class UiSettingsStore {
    private final Path directory;
    private final UiState state;
    public UiSettingsStore(Path directory, UiState state) {
        if (directory == null || state == null) throw new IllegalArgumentException("directory/state");
        this.directory = directory;
        this.state = state;
    }
    public void load() throws IOException {
        Path file = directory.resolve("ui.properties");
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        if (!"1".equals(p.getProperty("schema"))) throw new IllegalArgumentException("Invalid UI schema");
        final UiState.Layout layout = UiState.Layout.valueOf(p.getProperty("layout"));
        final UiState.Theme theme = UiState.Theme.valueOf(p.getProperty("theme"));
        final double x = Double.parseDouble(p.getProperty("hud.x"));
        final double y = Double.parseDouble(p.getProperty("hud.y"));
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("Invalid HUD coordinates");
        // No field mutates until all persisted fields have been validated.
        state.layout(layout);
        state.theme(theme);
        state.position(x, y);
    }
    public void save() throws IOException {
        Files.createDirectories(directory);
        Properties p = new Properties();
        p.setProperty("schema", "1");
        p.setProperty("layout", state.layout().name());
        p.setProperty("theme", state.theme().name());
        p.setProperty("hud.x", Double.toString(state.hudX()));
        p.setProperty("hud.y", Double.toString(state.hudY()));
        Path file = directory.resolve("ui.properties");
        Path tmp = Files.createTempFile(directory, "ghostc-ui-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, "GhostC UI preferences"); }
            if (Files.exists(file)) Files.copy(file, directory.resolve("ui.properties.bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
