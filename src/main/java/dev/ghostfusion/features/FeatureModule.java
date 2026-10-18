package dev.ghostfusion.features;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;

/** Declarative first-party module; Forge adapter is the sole owner of game mutations/draw calls. */
public final class FeatureModule implements GhostModule {
    private final String id, label;
    private final Category category;
    public FeatureModule(String id, String label, Category category) {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}") || label == null ||
            label.trim().isEmpty() || category == null) throw new IllegalArgumentException("feature metadata");
        this.id = id; this.label = label; this.category = category;
    }
    public String id() { return id; }
    public String displayName() { return label; }
    public Category category() { return category; }
    public void onEnable() { }
    public void onDisable() { }
    public void onEvent(GameEvent event) { }
}
