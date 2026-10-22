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
    /** Existing 5% step remains the unmodified keyboard behavior. */
    public static double nudgeFraction(double value,double min,double max,int direction) {
        return nudgeFraction(value,min,max,direction,false);
    }
    /** Shift+arrow uses 1% of the numeric range instead of the normal 5%. */
    public static double nudgeFraction(double value,double min,double max,
                                       int direction,boolean fine) {
        if(direction!=1 && direction!=-1)
            throw new IllegalArgumentException("numeric setting direction");
        double current=NumericSliderScale.fraction(value,min,max);
        return Math.max(0.0,Math.min(1.0,current+direction*(fine?0.01:0.05)));
    }
    /** Home/End address exact endpoints through the same numeric parser. */
    public static double endpointFraction(double min,double max,boolean end) {
        NumericSliderScale.fraction(min,min,max); // validate finite bounds
        return end?1.0:0.0;
    }
}
