package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSettingPresentation;
import dev.ghostfusion.ui.GuiSlateNumberReadout;
import java.util.Random;
import java.util.function.ToIntFunction;

/** 50k native-font budgets; preserve numeric truth without touching input. */
public final class GuiSlateNumberReadoutTests {
    private static long checks;
    private static void ok(boolean pass,String context) {
        checks++;if(!pass)throw new AssertionError(context);
    }
    private static void reject(Runnable task,String context) {
        boolean threw=false;
        try{task.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,context);
    }
    public static void main(String[] args) {
        ToIntFunction<String> six=s->s.codePointCount(0,s.length())*6;
        ok("4.5".equals(GuiSlateNumberReadout.inline(
            "range",4.5,36,six)),
            "compact range keeps the exact value instead of 4.5 blo...");
        ok("4.5 blocks".equals(GuiSlateNumberReadout.inline(
            "range",4.5,90,six)),"full range and units fit normally");
        ok("4.5".equals(GuiSlateNumberReadout.inline(
            "range",4.5,40,six)),"wide slider retains whole number before units");
        ok("VALUE  4.5 blocks".equals(GuiSlateNumberReadout.footer(
            "range",4.5,"4.5",180,six)),"wide footer also reveals full units");
        ok("82%".equals(GuiSlateNumberReadout.inline(
            "opacity",0.82,36,six)),"short meaningful unit is preserved");
        ok("100".equals(GuiSlateNumberReadout.inline(
            "opacity",1.0,18,six)),
            "100 percent must not become raw one without its suffix");
        ok("82".equals(GuiSlateNumberReadout.inline(
            "opacity",0.82,12,six)),
            "displayed 82 percent must retain percent magnitude");
        ok("12.5".equals(GuiSlateNumberReadout.inline(
            "fade_min_opacity",0.125,24,six)),
            "fractional percentage remains truthful when units do not fit");
        ok("VALUE  100%".equals(GuiSlateNumberReadout.footer(
            "opacity",1.0,"100",180,six)),
            "hover recovers the full percentage with units");
        ok("".equals(GuiSlateNumberReadout.inline(
            "range",4.5,2,six)),"never present a misleading partial number");
        ok("VALUE  4.5 blocks".equals(GuiSlateNumberReadout.footer(
            "range",4.5,"4.5",180,six)),
            "hover reveals complete range and source units");
        ok(GuiSlateNumberReadout.footer(
            "range",4.5,"4.5 blocks",180,six)==null,
            "fully visible number does not consume the footer");
        ok(GuiSlateNumberReadout.footer(
            "range",4.5,"4.5",20,six)==null,
            "insufficient footer space never truncates a numeric value");
        reject(()->GuiSlateNumberReadout.inline(
            null,1,36,six),"null id");
        reject(()->GuiSlateNumberReadout.inline(
            "range",Double.NaN,36,six),"nonfinite value");
        reject(()->GuiSlateNumberReadout.inline(
            "range",1,-1,six),"negative inline budget");
        reject(()->GuiSlateNumberReadout.inline(
            "range",1,36,null),"missing font");
        reject(()->GuiSlateNumberReadout.inline(
            "range",1,36,s->-1),"invalid native font width");
        reject(()->GuiSlateNumberReadout.footer(
            "range",1,null,40,six),"null displayed value");
        reject(()->GuiSlateNumberReadout.footer(
            "range",1,"1",-1,six),"negative footer budget");
        Random random=new Random(2026110403L);
        String[] ids={"range","scale","cps","fov","opacity",
            "interval_ticks","min_gap_ticks","degrees_per_tick",
            "fill_opacity","fade_min_opacity","red"};
        for(int i=0;i<50000;i++) {
            String id=ids[random.nextInt(ids.length)];
            double value=(random.nextInt(50001)-25000)/100.0;
            int glyph=3+random.nextInt(10),budget=random.nextInt(170);
            int footerBudget=random.nextInt(270);
            ToIntFunction<String> font=s->s.codePointCount(0,s.length())*glyph;
            String full=GuiSettingPresentation.number(id,value);
            String plain=GuiSettingPresentation.numberWithoutSuffix(id,value);
            String shown=GuiSlateNumberReadout.inline(
                id,value,budget,font);
            ok(shown.equals(full)||shown.equals(plain)||shown.isEmpty(),
                "only complete source-formatted or complete numeric content "+i);
            ok(font.applyAsInt(shown)<=budget,
                "inline stays inside actual font pixel budget "+i);
            if(font.applyAsInt(full)<=budget)
                ok(shown.equals(full),"never strip units unnecessarily "+i);
            else if(font.applyAsInt(plain)<=budget)
                ok(shown.equals(plain),"numeric truth before clipping "+i);
            else ok(shown.isEmpty(),"no partial digits "+i);
            String footer=GuiSlateNumberReadout.footer(
                id,value,shown,footerBudget,font);
            if(shown.equals(full)) {
                ok(footer==null,"no redundant tooltip "+i);
            } else if(footer!=null) {
                ok(footer.equals("VALUE  "+full),
                    "footer contains complete real formatted value "+i);
                ok(font.applyAsInt(footer)<=footerBudget,
                    "footer keeps original pixel limits "+i);
            }
        }
        System.out.println("GUI_SLATE_NUMBER_READOUT_PASS checks="+checks);
    }
}
