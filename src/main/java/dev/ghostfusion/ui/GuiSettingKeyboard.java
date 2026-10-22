package dev.ghostfusion.ui;

/**
 * Pure keyboard traversal of the selected module's typed settings.
 * No Minecraft or persistence dependency. All focus positions use the same
 * stable ordered setting indices as GUI draw and hit testing.
 */
public final class GuiSettingKeyboard {
    private GuiSettingKeyboard() {}

    /** Cyclic Tab/Shift+Tab movement; -1 means module list/no setting focus. */
    public static int next(int focus,int count,int direction) {
        if(count<0 || (direction!=1 && direction!=-1))
            throw new IllegalArgumentException("setting focus range/direction");
        if(count==0)return -1;
        if(focus<0 || focus>=count)return direction==1?0:count-1;
        return (focus+direction+count)%count;
    }
    /** Reveal keyboard focus without changing an already-visible scroll. */
    public static int reveal(int focus,int first,int count,int rows) {
        if(count<0 || rows<0)throw new IllegalArgumentException("setting rows");
        int start=Math.max(0,Math.min(first,Math.max(0,count-rows)));
        if(focus<0 || focus>=count || rows==0)return start;
        if(focus<start)return focus;
        if(focus>=start+rows)return Math.min(count-rows,focus-rows+1);
        return start;
    }
    /** 5% of the validated numeric range per Left/Right press. */
    public static double nudgeFraction(double value,double min,double max,int direction) {
        if(direction!=1 && direction!=-1)
            throw new IllegalArgumentException("numeric setting direction");
        double current=NumericSliderScale.fraction(value,min,max);
        return Math.max(0.0,Math.min(1.0,current+direction*0.05));
    }
}
