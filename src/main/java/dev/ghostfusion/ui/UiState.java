package dev.ghostfusion.ui;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure Java UI model; coordinate values are resolution-independent and persistable. */
public final class UiState {
    public enum Layout { COMPACT, WIDE }
    public enum Theme { GRAPHITE, INDIGO }
    public static final Layout DEFAULT_LAYOUT = Layout.WIDE;
    public static final Theme DEFAULT_THEME = Theme.INDIGO;
    private Layout layout = DEFAULT_LAYOUT;
    private Theme theme = DEFAULT_THEME;
    private String query = "";
    private final HudPositions widgets = new HudPositions();
    private GhostModule.Category category;
    public static final double DEFAULT_HUD_X = 0.025;
    public static final double DEFAULT_HUD_Y = 0.055;
    private double hudX = DEFAULT_HUD_X;
    private double hudY = DEFAULT_HUD_Y;

    public Layout layout() { return layout; }
    public Theme theme() { return theme; }
    public String query() { return query; }
    public HudPositions widgets() { return widgets; }
    public GhostModule.Category category() { return category; }
    public double hudX() { return hudX; }
    public double hudY() { return hudY; }
    public void layout(Layout next) { if (next == null) throw new IllegalArgumentException("layout"); layout = next; }
    public void theme(Theme next) { if (next == null) throw new IllegalArgumentException("theme"); theme = next; }
    public void query(String next) {
        if (next == null) throw new IllegalArgumentException("query");
        query = next.length() > 100 ? next.substring(0, 100) : next;
    }
    public void category(GhostModule.Category next) { category = next; }
    public static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite position");
        return Math.min(max, Math.max(min, value));
    }
    public void position(double x, double y) {
        double cx = clamp(x, 0, 1), cy = clamp(y, 0, 1);
        hudX = cx; hudY = cy;
    }
    public int pixelX(int viewport, int width) { return pixel(hudX, viewport, width); }
    public int pixelY(int viewport, int height) { return pixel(hudY, viewport, height); }
    private static int pixel(double fraction, int viewport, int content) {
        if (viewport < 1 || content < 1) throw new IllegalArgumentException("Invalid dimensions");
        return (int) Math.round(fraction * Math.max(0, viewport - content));
    }
    public void positionPixels(int x, int y, int width, int height,
                               int contentWidth, int contentHeight) {
        if (width < 1 || height < 1 || contentWidth < 1 || contentHeight < 1)
            throw new IllegalArgumentException("Invalid dimensions");
        double nx = clamp((double) x / Math.max(1, width - contentWidth), 0, 1);
        double ny = clamp((double) y / Math.max(1, height - contentHeight), 0, 1);
        position(nx, ny);
    }
    public List<ModuleRegistry.Entry> filtered(ModuleRegistry modules) {
        if (modules == null) throw new IllegalArgumentException("modules");
        String term = query.trim().toLowerCase(Locale.ROOT);
        List<ModuleRegistry.Entry> result = new ArrayList<ModuleRegistry.Entry>();
        for (ModuleRegistry.Entry e : modules.all()) {
            if (category != null && category != e.module().category()) continue;
            if (!term.isEmpty() &&
                !e.module().id().toLowerCase(Locale.ROOT).contains(term) &&
                !e.module().displayName().toLowerCase(Locale.ROOT).contains(term)) continue;
            result.add(e);
        }
        return result;
    }
}
