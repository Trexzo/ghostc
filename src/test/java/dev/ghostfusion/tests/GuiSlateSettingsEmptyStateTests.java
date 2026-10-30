package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateSettingsEmptyState;
import java.util.Random;

/** 50k varied wide/compact Slate viewport and footer-safety probes. */
public final class GuiSlateSettingsEmptyStateTests {
    private static long checks;
    private static void ok(boolean trueValue,String note) {
        checks++;if(!trueValue)throw new AssertionError(note);
    }
    private static void fails(Runnable r,String note) {
        boolean thrown=false;
        try{r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,note);
    }
    public static void main(String[] args) {
        Random r=new Random(30102026L);
        for(int i=0;i<50000;i++) {
            int x=r.nextInt(2000),y=r.nextInt(1000);
            int width=r.nextInt(590),height=r.nextInt(350);
            GuiSlateSettingsEmptyState.Frame f=
                GuiSlateSettingsEmptyState.place(x,y,width,height);
            if(width<GuiSlateSettingsEmptyState.MIN_WIDTH||
               height<GuiSlateSettingsEmptyState.MIN_HEIGHT) {
                ok(f==null,"small settings pane omits empty state");
                continue;
            }
            ok(f!=null,"eligible pane receives empty panel");
            ok(f.x==x&&f.y==y&&f.width==width,
                "empty panel follows only existing settings paint origin");
            ok(f.height>=GuiSlateSettingsEmptyState.MIN_HEIGHT &&
                f.height<=GuiSlateSettingsEmptyState.MAX_HEIGHT,
                "panel maximum height stays bounded");
            ok(f.x+f.width<=x+width && f.y+f.height<=y+height,
                "paint never escapes pane or reserved footer");
            ok(f.titleX>=f.x+8&&f.separatorY>f.titleY&&
               f.hintY>f.separatorY&&f.hintY+9<f.y+f.height,
               "two independent text lines with comfortable baselines");
            ok(f.accentX>=f.x&&f.accentX<f.x+f.width,
                "accent stays inside surface");
            // Exact current-font widths rather than assumptions about the
            // default Minecraft 9px font or GUI Scale Auto.
            final int px=3+r.nextInt(10);
            java.util.function.ToIntFunction<String> font=
                value->value.length()*px;
            GuiSlateSettingsEmptyState.Copy copy=
                GuiSlateSettingsEmptyState.copy(f,font);
            if(copy!=null) {
                ok(!copy.title.contains("...")&&!copy.hint.contains("..."),
                    "neither caption is a misleading partial phrase");
                ok(font.applyAsInt(copy.title)<=f.width-24,
                    "measured heading fits horizontal inset");
                ok(font.applyAsInt(copy.hint)<=f.width-24,
                    "measured hint fits horizontal inset");
                ok(copy.textBudget==f.width-24,"rendered pixel budget matches UI");
            }
        }
        // At narrow compact widths, preserve meaningful shorter wording
        // instead of allowing the long heading to paint over the border.
        java.util.function.ToIntFunction<String> six=s->s.length()*6;
        GuiSlateSettingsEmptyState.Frame compact=
            GuiSlateSettingsEmptyState.place(10,15,110,52);
        GuiSlateSettingsEmptyState.Copy shortCopy=
            GuiSlateSettingsEmptyState.copy(compact,six);
        ok(shortCopy!=null,"compact has usable measured copy");
        ok(shortCopy.title.equals("NO SETTINGS"),"compact shortened heading");
        ok(shortCopy.hint.equals("TOGGLE / KEY"),"compact shortened hint");
        GuiSlateSettingsEmptyState.Copy wideCopy=
            GuiSlateSettingsEmptyState.copy(
                GuiSlateSettingsEmptyState.place(1,1,220,80),six);
        ok(wideCopy.title.equals("NO EXTRA SETTINGS"),
            "wide layout retains expressive heading");
        ok(wideCopy.hint.equals("TOGGLE OR SET A KEY ABOVE"),
            "wide layout retains full action hint");
        ok(GuiSlateSettingsEmptyState.copy(compact,str->1000)==null,
            "unusually large font skips caption rather than clipping");
        fails(()->GuiSlateSettingsEmptyState.copy(compact,str->-1),
            "negative invalid font measurement rejected");
        fails(()->GuiSlateSettingsEmptyState.copy(null,six),
            "null frame rejected");
        fails(()->GuiSlateSettingsEmptyState.copy(compact,null),
            "null font rejected");
        fails(()->GuiSlateSettingsEmptyState.place(0,0,-1,30),
            "negative available width rejected");
        fails(()->GuiSlateSettingsEmptyState.place(0,0,200,-2),
            "negative available height rejected");
        ok(GuiSlateSettingsEmptyState.place(0,0,109,90)==null,
            "too narrow for two lines");
        ok(GuiSlateSettingsEmptyState.place(0,0,120,46)==null,
            "too short to avoid clipping text");
        System.out.println("GUI_SLATE_SETTINGS_EMPTY_STATE_PASS checks="+checks);
    }
}
