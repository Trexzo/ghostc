package dev.ghostfusion.core;

/** A self-contained module. All lifecycle/event callbacks run on the client owner thread. */
public interface GhostModule {
    String id();
    String displayName();
    Category category();
    void onEnable();
    void onDisable();
    void onEvent(GameEvent event);

    enum Category { COMBAT, MOVEMENT, WORLD, RENDER, UTILITY }
}
