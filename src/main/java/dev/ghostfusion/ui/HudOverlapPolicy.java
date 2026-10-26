package dev.ghostfusion.ui;

/**
 * Half-open pixel footprint collision checks for HUD editor feedback.
 * Read-only, independent of Minecraft and saved widget anchors.
 */
public final class HudOverlapPolicy {
    private HudOverlapPolicy() {}
    public static boolean overlaps(HudPreviewGeometry.Rect a,HudPreviewGeometry.Rect b) {
        if(a==null||b==null||a.width<1||a.height<1||b.width<1||b.height<1)
            throw new IllegalArgumentException("HUD overlap footprint");
        return (long)a.x<(long)b.x+b.width &&
               (long)b.x<(long)a.x+a.width &&
               (long)a.y<(long)b.y+b.height &&
               (long)b.y<(long)a.y+a.height;
    }
}
