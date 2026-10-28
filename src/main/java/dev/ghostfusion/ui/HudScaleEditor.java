package dev.ghostfusion.ui;

/** Keyboard-only F7 HUD scale controls, independent from GL and profiles. */
public final class HudScaleEditor {
    private HudScaleEditor(){}
    public static String moduleId(HudEditorControls.Item item) {
        if(item==null)throw new IllegalArgumentException("Selected HUD item");
        switch(item) {
            case STATUS: return "hud.status";
            case KEYS: return "hud.keystrokes";
            case CPS: return "hud.cps";
            case TARGET: return "hud.target";
            case MODULE_LIST: return "hud.modulelist";
            default: throw new IllegalArgumentException("Unknown HUD item");
        }
    }
    /** Brackets use 5% steps; Shift+brackets use 25% steps. */
    public static double adjust(double current,int direction,boolean coarse) {
        HudScaleLayout.validate(current);
        if(direction!=1&&direction!=-1)
            throw new IllegalArgumentException("HUD zoom direction");
        int step=coarse?25:5;
        int percent=(int)Math.round(current*100);
        int next=Math.max(75,Math.min(175,percent+direction*step));
        return next/100.0;
    }
    public static double fraction(double scale) {
        HudScaleLayout.validate(scale);
        return (scale-HudScaleLayout.MIN)/
            (HudScaleLayout.MAX-HudScaleLayout.MIN);
    }
}
