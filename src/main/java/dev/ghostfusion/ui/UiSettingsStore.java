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
        // Optional R1.9 widget fields preserve existing schema=1 UI files.
        // Validate all six values BEFORE mutating legacy or new preferences.
        final HudPositions.Widget[] widgets=HudPositions.Widget.values();
        final double[][] positions=new double[widgets.length][2];
        for(int i=0;i<widgets.length;i++) {
            HudPositions.Widget widget=widgets[i];
            String rawX=p.getProperty(HudPositions.property(widget,true),
                Double.toString(state.widgets().x(widget)));
            String rawY=p.getProperty(HudPositions.property(widget,false),
                Double.toString(state.widgets().y(widget)));
            double px=Double.parseDouble(rawX),py=Double.parseDouble(rawY);
            if(!Double.isFinite(px)||!Double.isFinite(py)||
                px<0||px>1||py<0||py>1)
                throw new IllegalArgumentException("Invalid widget HUD position");
            positions[i][0]=px; positions[i][1]=py;
        }
        // No field mutates until all persisted fields have been validated.
        state.layout(layout);
        state.theme(theme);
        state.position(x, y);
        for(int i=0;i<widgets.length;i++)
            state.widgets().position(widgets[i],positions[i][0],positions[i][1]);
    }
    public void save() throws IOException {
        Files.createDirectories(directory);
        Properties p = new Properties();
        p.setProperty("schema", "1");
        p.setProperty("layout", state.layout().name());
        p.setProperty("theme", state.theme().name());
        p.setProperty("hud.x", Double.toString(state.hudX()));
        p.setProperty("hud.y", Double.toString(state.hudY()));
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            p.setProperty(HudPositions.property(widget,true),
                Double.toString(state.widgets().x(widget)));
            p.setProperty(HudPositions.property(widget,false),
                Double.toString(state.widgets().y(widget)));
        }
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
