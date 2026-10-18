package dev.ghostfusion.host;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;

/** Optional metrics HUD. Forge adapter owns Minecraft-specific data and drawing. */
public final class MetricsOverlayModule implements GhostModule {
    private boolean frame;
    public String id() { return "hud.metrics"; }
    public String displayName() { return "Session Metrics"; }
    public Category category() { return Category.RENDER; }
    public void onEnable() { frame = false; }
    public void onDisable() { frame = false; }
    public void onEvent(GameEvent event) {
        if (event.kind() == GameEvent.Kind.RENDER_2D) frame = true;
    }
    void beginFrame() { frame = false; }
    boolean frameRequested() { return frame; }
}
