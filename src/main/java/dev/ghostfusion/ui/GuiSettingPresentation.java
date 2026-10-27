package dev.ghostfusion.ui;

/**
 * Read-only, source-compatible display text for the Minecraft 1.8.9 GUI.
 * Setting IDs and serialized values remain unchanged.
 */
public final class GuiSettingPresentation {
    private GuiSettingPresentation(){}
    /** Convert validated underscore IDs into compact readable labels. */
    public static String label(String id) {
        if(id==null||id.length()==0)throw new IllegalArgumentException("setting ID");
        String[] parts=id.split("_",-1);
        StringBuilder sb=new StringBuilder(id.length()+5);
        for(String word:parts) {
            if(word.length()==0)throw new IllegalArgumentException("setting ID segments");
            if(sb.length()!=0)sb.append(' ');
            if(word.equals("cps")||word.equals("fps")||word.equals("fov")||
               word.equals("hp")||word.equals("esp")||word.equals("rgb"))
                sb.append(word.toUpperCase(java.util.Locale.ROOT));
            else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if(word.length()>1)sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
    public static String choice(String raw) {
        // Existing profiles keep the persisted "nebula" identifier.
        // Users see the redesigned Slate label, never the old purple brand.
        if("nebula".equals(raw))return "Slate";
        return label(raw);
    }
    /** Whitelisted units only; unknown numeric settings retain their value. */
    public static String number(String id,double value) {
        if(id==null||!Double.isFinite(value))
            throw new IllegalArgumentException("numeric setting display");
        double rounded=Math.rint(value*100.0)/100.0;
        String display=Double.toString(rounded);
        if(display.endsWith(".0"))display=display.substring(0,display.length()-2);
        if(id.equals("scale"))return display+"x";
        if(id.equals("cps"))return display+" CPS";
        if(id.equals("fov"))return display+" deg";
        if(id.equals("degrees_per_tick"))return display+" deg/t";
        if(id.equals("interval_ticks")||id.equals("min_gap_ticks"))
            return display+" ticks";
        if(id.equals("range"))return display+" blocks";
        if(id.equals("opacity")||id.equals("fill_opacity")||
           id.equals("fade_min_opacity")) {
            double pct=Math.rint(value*10000.0)/100.0;
            return Double.toString(pct).replaceAll("\\.0$","")+"%";
        }
        return display;
    }
}
