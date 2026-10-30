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
        }
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
