package dev.ghostfusion.ui;

/**
 * Owner-thread-only pointer capture for a numeric ClickGUI slider. Retains
 * the exact stable module/setting identifiers, pixel track, and viewport
 * from mouse-down. No Minecraft objects, I/O, or profile writes.
 */
public final class GuiSliderDrag {
    private final Thread owner=Thread.currentThread();
    private String moduleId, settingId;
    private int trackLeft, trackWidth, viewportWidth, viewportHeight;
    private boolean dirty;
    private boolean baselineKnown;
    private double baseline;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("slider drag owner thread");
    }
    public void begin(String moduleId,String settingId,int left,int width,
                      int screenWidth,int screenHeight) {
        check();
        if(active())throw new IllegalStateException("slider capture already active");
        if(moduleId==null || moduleId.length()==0 ||
           settingId==null || settingId.length()==0 ||
           width<1 || screenWidth<1 || screenHeight<1)
            throw new IllegalArgumentException("slider capture geometry/identity");
        this.moduleId=moduleId;this.settingId=settingId;
        this.trackLeft=left;this.trackWidth=width;
        this.viewportWidth=screenWidth;this.viewportHeight=screenHeight;
        dirty=false;baselineKnown=false;baseline=0.0;
    }
    /** Baseline tracks the persisted numeric value at mouse-down so a
     * pointer returning to that value does not rewrite an unchanged profile. */
    public void begin(String moduleId,String settingId,int left,int width,
                      int screenWidth,int screenHeight,double initialValue) {
        check();
        if(!Double.isFinite(initialValue))
            throw new IllegalArgumentException("nonfinite slider baseline");
        begin(moduleId,settingId,left,width,screenWidth,screenHeight);
        baselineKnown=true;baseline=initialValue;
    }
    /** Feed back the *actual applied* numeric value, not merely pointer motion.
     * A drag away from and back to its initial value is a clean transaction. */
    public void observeValue(double currentValue) {
        check();
        if(!active() || !baselineKnown)
            throw new IllegalStateException("slider baseline not captured");
        if(!Double.isFinite(currentValue))
            throw new IllegalArgumentException("nonfinite slider value");
        dirty=Double.compare(currentValue,baseline)!=0;
    }
    public boolean active() {check();return moduleId!=null;}
    public boolean sameViewport(int width,int height) {
        check();return active() && width==viewportWidth && height==viewportHeight;
    }
    public String moduleId() {check();if(!active())throw new IllegalStateException("no slider");return moduleId;}
    public String settingId() {check();if(!active())throw new IllegalStateException("no slider");return settingId;}
    /** Inclusive endpoints. Long subtraction avoids overflows from mouse coords. */
    public double fraction(int mouseX) {
        check();
        if(!active())throw new IllegalStateException("no slider");
        double value=((long)mouseX-(long)trackLeft)/(double)trackWidth;
        return Math.max(0.0,Math.min(1.0,value));
    }
    public void changed() {
        check();if(!active())throw new IllegalStateException("no slider");
        dirty=true;
    }
    /** Return whether one profile save is required, then revoke capture. */
    public boolean finish() {
        check();
        boolean changed=dirty;
        moduleId=null;settingId=null;dirty=false;
        baselineKnown=false;baseline=0.0;
        return changed;
    }
}
