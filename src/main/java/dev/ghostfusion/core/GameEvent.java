package dev.ghostfusion.core;

/** Immutable host-independent event. Concrete Minecraft objects never cross into the core. */
public final class GameEvent {
    public enum Kind { CLIENT_TICK, RENDER_2D, WORLD_CHANGE, SESSION_DISCONNECT }
    private final Kind kind;
    private final long sequence;
    public GameEvent(Kind kind, long sequence) {
        if (kind == null || sequence < 0) throw new IllegalArgumentException("Invalid event");
        this.kind = kind;
        this.sequence = sequence;
    }
    public Kind kind() { return kind; }
    public long sequence() { return sequence; }
}
