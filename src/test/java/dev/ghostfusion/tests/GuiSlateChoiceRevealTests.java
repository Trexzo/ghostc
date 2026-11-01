package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateChoiceReveal;
import dev.ghostfusion.ui.GuiSettingPresentation;
import java.util.Random;
import java.util.function.ToIntFunction;

/** 40k native-font-budget and value-reveal probes, no Forge dependency. */
public final class GuiSlateChoiceRevealTests {
    private static long checks;
    private static void ok(boolean condition,String context) {
        checks++;if(!condition)throw new AssertionError(context);
    }
    private static void reject(Runnable r,String context) {
        boolean thrown=false;
        try{r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,context);
    }
    public static void main(String[] args) {
        ToIntFunction<String> six=s->s.codePointCount(0,s.length())*6;
        String label="Closest Players First";
        ok(GuiSlateChoiceReveal.footer("closest_players_first",200,200,six)==null,
            "fully visible choice never consumes the footer");
        ok(("VALUE  "+label).equals(GuiSlateChoiceReveal.footer(
            "closest_players_first",50,200,six)),
            "clipped choice shows complete readable value when room permits");
        ok(GuiSlateChoiceReveal.footer("closest_players_first",50,18,six)==null,
            "tiny footer cannot show a misleading bare VALUE tag");
        ok(GuiSlateChoiceReveal.footer("closest_players_first",50,55,six)==null,
            "only dots after VALUE are not an informative reveal");
        reject(()->GuiSlateChoiceReveal.footer(null,10,90,six),"null raw");
        reject(()->GuiSlateChoiceReveal.footer("top_left",-1,90,six),
            "negative choice budget");
        reject(()->GuiSlateChoiceReveal.footer("top_left",10,-1,six),
            "negative footer budget");
        reject(()->GuiSlateChoiceReveal.footer("top_left",10,90,null),
            "missing font");
        reject(()->GuiSlateChoiceReveal.footer("top_left",10,90,s->-3),
            "invalid native font width");
        Random random=new Random(20261101L);
        String[] values={"top_left","closest_players_first",
            "very_long_value_with_many_sections_and_a_final_option",
            "render","smooth","two_words","cps","max_range"};
        for(int i=0;i<40000;i++) {
            String raw=values[random.nextInt(values.length)];
            int choiceBudget=random.nextInt(180);
            int footerBudget=random.nextInt(320);
            int glyph=3+random.nextInt(10);
            ToIntFunction<String> font=s->s.codePointCount(0,s.length())*glyph;
            String original=GuiSettingPresentation.choice(raw);
            String shown=GuiSlateChoiceReveal.footer(
                raw,choiceBudget,footerBudget,font);
            if(font.applyAsInt(original)<=choiceBudget) {
                ok(shown==null,"no redundant footer when choice fits "+i);
            } else if(shown!=null) {
                ok(shown.startsWith("VALUE  "),"readout has clear identity "+i);
                ok(shown.length()>"VALUE  ...".length(),
                    "useful visible value, not just ellipsis "+i);
                ok(font.applyAsInt(shown)<=footerBudget,
                    "readout remains within actual footer pixel budget "+i);
                String suffix=shown.substring("VALUE  ".length());
                ok(suffix.equals(original)||suffix.endsWith("..."),
                    "truncation is explicit, not a false full value "+i);
            }
        }
        System.out.println("GUI_SLATE_CHOICE_REVEAL_PASS checks="+checks);
    }
}
