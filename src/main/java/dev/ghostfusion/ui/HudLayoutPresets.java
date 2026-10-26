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
    private static final class Anchor {
        final double x,y;
        Anchor(double x,double y){this.x=x;this.y=y;}
    }
    /** Set coordinates only after validating the complete preset selection. */
    public static boolean apply(UiState ui,Preset preset) {
        if(ui==null||preset==null)
            throw new IllegalArgumentException("HUD layout preset");
        if(matchesAnchors(ui,preset))return false;
        Anchor status=anchor(preset,null);
        ui.position(status.x,status.y);
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            Anchor desired=anchor(preset,widget);
            ui.widgets().position(widget,desired.x,desired.y);
        }
        return true;
    }
    public static boolean matches(UiState ui,Preset preset,String listPosition) {
        if(ui==null||preset==null||listPosition==null)
            throw new IllegalArgumentException("HUD layout comparison");
        return preset.listPosition.equals(listPosition)&&matchesAnchors(ui,preset);
    }
    private static boolean matchesAnchors(UiState ui,Preset preset) {
        Anchor status=anchor(preset,null);
        if(ui.hudX()!=status.x||ui.hudY()!=status.y)return false;
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            Anchor desired=anchor(preset,widget);
            if(ui.widgets().x(widget)!=desired.x ||
               ui.widgets().y(widget)!=desired.y)return false;
        }
        return true;
    }
    private static Anchor anchor(Preset preset,HudPositions.Widget widget) {
        if(preset==Preset.DEFAULT) {
            if(widget==null)return new Anchor(UiState.DEFAULT_HUD_X,UiState.DEFAULT_HUD_Y);
            // Using the authoritative widget defaults maintains original profiles.
            switch(widget){
                case KEYSTROKES:return new Anchor(1.0,0.86);
                case CPS:return new Anchor(1.0,1.0);
                case TARGET:return new Anchor(0.5,0.93);
                case MODULE_LIST:return new Anchor(1.0,0.0);
                default:throw new IllegalArgumentException("HUD widget");
            }
        }
        if(preset==Preset.PVP) {
            if(widget==null)return new Anchor(0.025,0.055);
            switch(widget){
                case KEYSTROKES:return new Anchor(0.025,0.90);
                case CPS:return new Anchor(0.025,0.70);
                case TARGET:return new Anchor(0.50,0.87);
                case MODULE_LIST:return new Anchor(1.0,0.02);
                default:throw new IllegalArgumentException("HUD widget");
            }
        }
        if(preset==Preset.STREAM) {
            if(widget==null)return new Anchor(0.025,0.055);
            switch(widget){
                case KEYSTROKES:return new Anchor(0.02,0.90);
                case CPS:return new Anchor(0.42,0.99);
                case TARGET:return new Anchor(0.5,0.98);
                case MODULE_LIST:return new Anchor(0.98,0.08);
                default:throw new IllegalArgumentException("HUD widget");
            }
        }
        throw new IllegalArgumentException("Unknown HUD layout preset");
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
        for(Preset preset:Preset.values()) {
            int top=buttonY(guiY,guiHeight,preset);
            if(my>=top&&my<top+18)return preset;
        }
        return null;
    }
}
