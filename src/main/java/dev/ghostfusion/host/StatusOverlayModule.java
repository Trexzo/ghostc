package dev.ghostfusion.host;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;

/** Render intent only; the Forge adapter exclusively owns Minecraft graphics calls. */
public final class StatusOverlayModule implements GhostModule {
    private boolean frameRequested;
    public String id() { return "hud.status"; }
    public String displayName() { return "GhostC Status"; }
    public Category category() { return Category.RENDER; }
    public void onEnable() { frameRequested = false; }
    public void onDisable() { frameRequested = false; }
    public void onEvent(GameEvent event) {
        if (event.kind() == GameEvent.Kind.RENDER_2D) frameRequested = true;
    }
    void beginFrame() { frameRequested = false; }
    boolean frameRequested() { return frameRequested; }
}
