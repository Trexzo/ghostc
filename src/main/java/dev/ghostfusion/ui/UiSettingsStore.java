package dev.ghostfusion.ui;

import dev.ghostfusion.core.ConfigIntegrity;

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
        Path file=directory.resolve("ui.properties");
        if(!Files.exists(file))return;
        loadFile(file);
    }
    public boolean loadRecovering() throws IOException {
        Path primary=directory.resolve("ui.properties");
        Path backup=directory.resolve("ui.properties.bak");
        if(!Files.exists(primary) && !Files.exists(backup))return false;
        try {
            loadFile(primary);
            return false;
        } catch(IOException | RuntimeException primaryFailure) {
            if(!Files.exists(backup))throw primaryFailure;
            try {
                ConfigIntegrity.restoreBackup(primary,
                    new java.util.function.Predicate<Properties>() {
                        public boolean test(Properties candidate) {
                            try { stage(candidate); return true; }
                            catch (RuntimeException invalid) { return false; }
                        }
                    });
                // Do not change live HUD state until the file promotion
                // successfully completes from the typed-validated snapshot.
                loadFile(primary);
                return true;
            } catch(IOException | RuntimeException backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
                throw primaryFailure;
            }
        }
    }
    /** Fully parsed UI preferences before any live state change. */
    private static final class Staged {
        final UiState.Layout layout;
        final UiState.Theme theme;
        final double x, y;
        final double[][] positions;
        Staged(UiState.Layout layout, UiState.Theme theme,
                double x, double y, double[][] positions) {
            this.layout=layout; this.theme=theme;
            this.x=x; this.y=y; this.positions=positions;
        }
    }
    private Staged stage(Properties p) {
        if (!"1".equals(p.getProperty("schema"))) throw new IllegalArgumentException("Invalid UI schema");
        final UiState.Layout layout = UiState.Layout.valueOf(p.getProperty("layout"));
        final UiState.Theme theme = UiState.Theme.valueOf(p.getProperty("theme"));
        final double x = Double.parseDouble(p.getProperty("hud.x"));
        final double y = Double.parseDouble(p.getProperty("hud.y"));
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1)
            throw new IllegalArgumentException("Invalid HUD coordinates");
        final HudPositions.Widget[] widgets=HudPositions.Widget.values();
        final double[][] positions=new double[widgets.length][2];
        for(int i=0;i<widgets.length;i++) {
            HudPositions.Widget widget=widgets[i];
            // Sparse legacy schema-1 preferences must never inherit
            // mutable live widget anchors during load or backup recovery.
            String rawX=p.getProperty(HudPositions.property(widget,true),
                Double.toString(widget.defaultX));
            String rawY=p.getProperty(HudPositions.property(widget,false),
                Double.toString(widget.defaultY));
            double px=Double.parseDouble(rawX),py=Double.parseDouble(rawY);
            if(!Double.isFinite(px)||!Double.isFinite(py)||
                px<0||px>1||py<0||py>1)
                throw new IllegalArgumentException("Invalid widget HUD position");
            positions[i][0]=px; positions[i][1]=py;
        }
        return new Staged(layout,theme,x,y,positions);
    }
    private void loadFile(Path file) throws IOException {
        Staged staged=stage(ConfigIntegrity.read(file));
        state.layout(staged.layout);
        state.theme(staged.theme);
        state.position(staged.x,staged.y);
        final HudPositions.Widget[] widgets=HudPositions.Widget.values();
        for(int i=0;i<widgets.length;i++)
            state.widgets().position(widgets[i],staged.positions[i][0],staged.positions[i][1]);
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
        ConfigIntegrity.seal(p);
        Path file = directory.resolve("ui.properties");
        if(ConfigIntegrity.unchangedWithRecoveryBackup(file,p,
                new java.util.function.Predicate<Properties>() {
                    public boolean test(Properties candidate) {
                        try { stage(candidate); return true; }
                        catch (RuntimeException invalid) { return false; }
                    }
                }))return;
        Path tmp = Files.createTempFile(directory, "ghostc-ui-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) { p.store(out, "GhostC UI preferences"); }
            ConfigIntegrity.rotateVerifiedBackup(file,
                new java.util.function.Predicate<Properties>() {
                    public boolean test(Properties candidate) {
                        try { stage(candidate); return true; }
                        catch (RuntimeException invalid) { return false; }
                    }
                });
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
