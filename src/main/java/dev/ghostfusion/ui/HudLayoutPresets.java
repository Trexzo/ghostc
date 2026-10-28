package dev.ghostfusion.ui;

/**
 * Three opt-in, reversible-position HUD presets. Applies only normalized HUD
 * anchors: selected skins, module enablement, scale, and ClickGUI settings stay
 * untouched. The legacy Default preset restores exact original coordinates.
 *
 * The Module List's position Choice remains owned by the typed HostRuntime
 * UI gateway; each preset declares the necessary mode for Forge integration.
 */
public final class HudLayoutPresets {
    private HudLayoutPresets(){}
    public enum Preset {
        DEFAULT("1 DEFAULT","top_right"),
        PVP("2 PVP","custom"),
        STREAM("3 STREAM","custom");
        public final String label,listPosition;
        Preset(String label,String listPosition) {
            this.label=label;this.listPosition=listPosition;
        }
    }
    private static final HudPositions.Widget[] WIDGETS=HudPositions.Widget.values();
    private static final Preset[] PRESETS=Preset.values();
    // Immutable private tables: one Status pair, then widget pairs in enum
    // order. F7 redraw/active-preset detection requires zero object creation.
    private static final double[][] DEFAULT_COORDS={
        {UiState.DEFAULT_HUD_X,UiState.DEFAULT_HUD_Y},
        {1.0,0.86},{1.0,1.0},{0.5,0.93},{1.0,0.0}
    };
    private static final double[][] PVP_COORDS={
        {0.025,0.055},{0.025,0.90},{0.025,0.60},{0.50,0.87},{1.0,0.02}
    };
    private static final double[][] STREAM_COORDS={
        {0.025,0.055},{0.02,0.90},{0.98,0.99},{0.5,0.98},{0.98,0.08}
    };
    private static double[][] coordinates(Preset preset) {
        switch(preset) {
            case DEFAULT:return DEFAULT_COORDS;
            case PVP:return PVP_COORDS;
            case STREAM:return STREAM_COORDS;
            default:throw new IllegalArgumentException("Unknown HUD layout preset");
        }
    }
    /** Set coordinates only after validating the complete preset selection. */
    public static boolean apply(UiState ui,Preset preset) {
        if(ui==null||preset==null)
            throw new IllegalArgumentException("HUD layout preset");
        if(matchesAnchors(ui,preset))return false;
        double[][] coords=coordinates(preset);
        ui.position(coords[0][0],coords[0][1]);
        for(HudPositions.Widget widget:WIDGETS) {
            double[] desired=coords[widget.ordinal()+1];
            ui.widgets().position(widget,desired[0],desired[1]);
        }
        return true;
    }
    public static boolean matches(UiState ui,Preset preset,String listPosition) {
        if(ui==null||preset==null||listPosition==null)
            throw new IllegalArgumentException("HUD layout comparison");
        return preset.listPosition.equals(listPosition)&&matchesAnchors(ui,preset);
    }
    private static boolean matchesAnchors(UiState ui,Preset preset) {
        double[][] coords=coordinates(preset);
        if(ui.hudX()!=coords[0][0]||ui.hudY()!=coords[0][1])return false;
        for(HudPositions.Widget widget:WIDGETS) {
            double[] desired=coords[widget.ordinal()+1];
            if(ui.widgets().x(widget)!=desired[0] ||
               ui.widgets().y(widget)!=desired[1])return false;
        }
        return true;
    }
    /**
     * Sidebar buttons are visible only when layout has vertical room for the
     * module filters and all three preset rows. Hit zones equal drawn rows.
     */
    public static int buttonY(int guiY,int guiHeight,Preset preset) {
        if(preset==null||guiHeight<320)return Integer.MIN_VALUE;
        return guiY+guiHeight-115+preset.ordinal()*22;
    }
    public static Preset buttonAt(int sidebarX,int sidebarWidth,
                                  int guiY,int guiHeight,int mx,int my) {
        if(sidebarWidth<85||guiHeight<320 ||
           mx<sidebarX+6||mx>=sidebarX+sidebarWidth-6)return null;
        for(Preset preset:PRESETS) {
            int top=buttonY(guiY,guiHeight,preset);
            if(my>=top&&my<top+18)return preset;
        }
        return null;
    }
}
