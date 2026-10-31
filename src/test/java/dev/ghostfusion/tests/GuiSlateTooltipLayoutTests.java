package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateTooltipLayout;
import dev.ghostfusion.ui.GuiSlateTooltipLayout.Frame;
import java.util.Random;

/** 50k seeded viewports, clamping, footer exclusion, pointer extremes. */
public final class GuiSlateTooltipLayoutTests {
    private static long checks;
    private static void ok(boolean b,String note) {
        checks++;if(!b)throw new AssertionError(note);
    }
    private static void rejects(Runnable r,String note) {
        boolean bad=false;
        try {r.run();}catch(IllegalArgumentException expected){bad=true;}
        ok(bad,note);
    }
    public static void main(String[] args) {
        Frame wide=GuiSlateTooltipLayout.place(110,60,610,480,200,100,212);
        ok(wide!=null && wide.width==224 && wide.textBudget==260,
            "typical text tooltip sized to real font");
        ok(wide.textX==wide.x+6&&wide.textY==wide.y+5,
            "text always fits padded surface");
        Frame longText=GuiSlateTooltipLayout.place(0,0,500,360,40,50,500);
        ok(longText!=null&&longText.textBudget==260&&longText.width==272,
            "unbounded source names never make an unbounded panel");
        Frame tiny=GuiSlateTooltipLayout.place(0,0,24,35,4,4,40);
        ok(tiny==null,"too-small GUI hides tooltip");
        rejects(()->GuiSlateTooltipLayout.place(0,0,0,100,1,1,30),
                "invalid panel rejected");
        rejects(()->GuiSlateTooltipLayout.place(0,0,100,100,1,1,-1),
                "invalid text width rejected");
        Random rand=new Random(28102026L);
        for(int i=0;i<50000;i++) {
            int px=rand.nextInt(1600)-500,py=rand.nextInt(1000)-300;
            int pw=1+rand.nextInt(1300),ph=1+rand.nextInt(850);
            int mx=rand.nextInt(3000)-900,my=rand.nextInt(2300)-700;
            int tw=rand.nextInt(1500);
            Frame f=GuiSlateTooltipLayout.place(px,py,pw,ph,mx,my,tw);
            if(f==null)continue;
            ok((long)f.x>=px+8L&&(long)f.x+f.width<=(long)px+pw-8,
                "horizontal panel containment "+i);
            ok((long)f.y>=py+2L&&(long)f.y+f.height<=(long)py+ph-20,
                "reserved footer never covered "+i);
            ok(f.textX>=f.x && f.textX+Math.min(tw,f.textBudget)<=f.x+f.width-6,
                "measured text remains padded "+i);
            ok(f.textBudget>=12&&f.textBudget<=260&&
                f.width==Math.min(tw,f.textBudget)+12,
                "text width bounded "+i);
            ok(f.height==19,"stable tooltip height "+i);
        }
        System.out.println("GUI_SLATE_TOOLTIP_LAYOUT_PASS checks="+checks);
    }
}
