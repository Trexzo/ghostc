package dev.ghostfusion.ui;

/**
 * Slate-only enabled-module overlay decoration.
 *
 * Vape's Text GUI and Slinky's mod overlay inform the text-first, per-label
 * surface: this is original geometry, not a copied skin. The layout still
 * owns membership, text measurements, sorting, position and maximum rows.
 * All geometry is allocation free and detached from game and GL state.
 */
public final class ModuleListSlateRowGeometry {
    private static final int PADDING=6;
    private static final int RAIL_WIDTH=2;
    private ModuleListSlateRowGeometry() { }

    /** Per-label background instead of a repeated longest-name-wide slab. */
    public static int width(ModuleListLayout.Row row) {
        if(row==null || row.textWidth<1 || row.width<row.textWidth+2*PADDING)
            throw new IllegalArgumentException("Slate ArrayList row width");
        return row.textWidth+2*PADDING;
    }

    /** Follow the animated label and keep the entire surface on screen. */
    public static int x(ModuleListLayout.Row row,int textX,int viewportWidth) {
        int width=width(row);
        if(viewportWidth<width || textX<0 ||
           (long)textX+row.textWidth>viewportWidth)
            throw new IllegalArgumentException("Slate ArrayList surface");
        return Math.max(0,Math.min(viewportWidth-width,textX-PADDING));
    }

    /** Quiet short edge dash, not a bright full-height stripe. */
    public static int railX(int surfaceX,int surfaceWidth,boolean right) {
        if(surfaceX<0||surfaceWidth<2)
            throw new IllegalArgumentException("Slate ArrayList rail");
        return right?surfaceX+surfaceWidth-RAIL_WIDTH:surfaceX;
    }

    public static int railY(ModuleListLayout.Row row) {
        if(row==null||row.height<4)
            throw new IllegalArgumentException("Slate ArrayList height");
        return row.y+(row.height-railHeight(row))/2;
    }

    public static int railHeight(ModuleListLayout.Row row) {
        if(row==null||row.height<4)
            throw new IllegalArgumentException("Slate ArrayList height");
        return Math.min(7,row.height-2);
    }
}
