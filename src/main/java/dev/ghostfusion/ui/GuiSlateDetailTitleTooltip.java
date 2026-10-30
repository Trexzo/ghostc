package dev.ghostfusion.ui;

/**
 * Read-only hover admission for a clipped Slate selected-module title.
 * Bounds come from the existing native-painted heading; no input targets
 * or persisted state are created.
 */
public final class GuiSlateDetailTitleTooltip {
    private GuiSlateDetailTitleTooltip() { }

    public static boolean reveal(String full,String fitted,
                                 int x,int y,int width,int height,
                                 int mouseX,int mouseY) {
        if(full==null||fitted==null||width<0||height<0)
            throw new IllegalArgumentException("Slate detail title");
        if(width==0||height==0||fitted.isEmpty()||full.equals(fitted))
            return false;
        return (long)mouseX>=x && (long)mouseY>=y &&
            (long)mouseX<(long)x+width &&
            (long)mouseY<(long)y+height;
    }
}
