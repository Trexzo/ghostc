package dev.ghostfusion.ui;

/** Pure-Java footer priority and pixel budget for both ClickGUI layouts. */
public final class GuiFooterText {
    private GuiFooterText() { }

    /** Validation errors are local and more actionable than host warnings. */
    public static String warning(String message,String warning) {
        if(message==null || warning==null)
            throw new IllegalArgumentException("null GUI footer text");
        return !message.isEmpty()?message:warning;
    }

    /** Keep text to the left of the panel's right edge with a safety inset.
     * Long arithmetic cannot underflow on extreme synthetic viewports. */
    public static int pixelBudget(int width,int sidebar) {
        if(width<0 || sidebar<0)
            throw new IllegalArgumentException("negative footer geometry");
        long budget=Math.max(0L,(long)width-sidebar-32L);
        return (int)Math.min((long)Integer.MAX_VALUE,budget);
    }
}
