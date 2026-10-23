package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiFooterText;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** R2.76: bounded footer geometry and deterministic error-first priority. */
public final class GuiFooterTextTests {
    private static int count;
    private static void ok(boolean pass,String reason) {
        count++; if(!pass)throw new AssertionError(reason);
    }
    private static void rejects(Runnable call) {
        boolean denied=false;
        try{call.run();}catch(IllegalArgumentException expected){denied=true;}
        ok(denied,"invalid footer argument rejected");
    }
    private static void priorities() {
        ok("".equals(GuiFooterText.warning("","")),"empty footer");
        ok("host".equals(GuiFooterText.warning("","host")),
            "host warning when no local validation message");
        ok("local".equals(GuiFooterText.warning("local","host")),
            "local validation error owns footer");
        ok("local".equals(GuiFooterText.warning("local","")),
            "standalone validation error preserved");
        StringBuilder longValue=new StringBuilder();
        for(int i=0;i<5000;i++)longValue.append("界🧪");
        String msg=longValue.toString();
        ok(GuiFooterText.warning(msg,"host")==msg,
            "text priority never edits Unicode scalar sequence");
        rejects(()->GuiFooterText.warning(null,""));
        rejects(()->GuiFooterText.warning("",null));
        rejects(()->GuiFooterText.pixelBudget(-1,0));
        rejects(()->GuiFooterText.pixelBudget(400,-1));
        ok(GuiFooterText.pixelBudget(250,240)==0,
            "tiny content area clamps to zero");
        ok(GuiFooterText.pixelBudget(Integer.MAX_VALUE,0)==Integer.MAX_VALUE-32,
            "large viewport does not overflow integer arithmetic");
    }
    private static void randomized() {
        Random random=new Random(2762026L);
        for(int i=0;i<85000;i++) {
            int width=random.nextInt(6000);
            int sidebar=random.nextInt(6000);
            int expected=(int)Math.max(0L,(long)width-sidebar-32L);
            int actual=GuiFooterText.pixelBudget(width,sidebar);
            ok(actual==expected,"correct bounded budget "+i);
            ok(actual>=0 && (long)actual+sidebar+32L<=width ||
                actual==0, "no positive budget exceeds panel "+i);
            String local=random.nextBoolean()?"":"validation_"+i;
            String host=random.nextBoolean()?"":"host_"+i;
            String chosen=GuiFooterText.warning(local,host);
            ok(chosen.equals(local.isEmpty()?host:local),
                "local message has fixed priority "+i);
            if(i%2==0) {
                GuiLayout g=GuiLayout.calculate(1+width,1+random.nextInt(2160),
                    (i%4)==0?UiState.Layout.COMPACT:UiState.Layout.WIDE);
                int bounded=GuiFooterText.pixelBudget(g.width,g.sidebar);
                ok(bounded>=0 && (bounded==0 ||
                    (long)bounded+g.sidebar+32L<=g.width),
                    "real compact/wide panel footer remains inside "+i);
            }
        }
    }
    public static void main(String[] args) {
        priorities();randomized();
        ok(count>250000,"meaningful footer policy coverage");
        System.out.println("GUI_FOOTER_TEXT_PASS assertions="+count);
    }
}
