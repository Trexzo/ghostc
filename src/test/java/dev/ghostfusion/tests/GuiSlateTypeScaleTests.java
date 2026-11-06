package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateTypeScale;
import java.util.Random;
import java.util.function.ToIntFunction;

/** 60k varied actual-glyph-proxy widths and GUI-scale text budgets. */
public final class GuiSlateTypeScaleTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++; if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable runnable,String why) {
        boolean failed=false;
        try{runnable.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
    public static void main(String[] args) {
        final ToIntFunction<String> font=new ToIntFunction<String>(){
            public int applyAsInt(String text){
                int width=0;
                for(int i=0;i<text.length();) {
                    int cp=text.codePointAt(i);
                    width+=cp=='I'||cp=='l'?2:cp=='W'||cp=='M'?7:6;
                    i+=Character.charCount(cp);
                }
                return width;
            }
        };
        ok(GuiSlateTypeScale.displayHeight(9,GuiSlateTypeScale.PRIMARY)==11,
            "primary 9px glyph line occupies 11px");
        ok(GuiSlateTypeScale.displayHeight(9,GuiSlateTypeScale.SECONDARY)==8,
            "secondary 9px glyph line occupies 8px");
        ok(2+11<16,"wide/compact primary name clears second baseline");
        ok(16+8<25,"wide secondary clears row separator");
        ok(14+8<=25,"compact secondary remains inside 25px row");
        ok(21+11<34&&34+8<44,
            "details title and breadcrumb clear the divider");
        rejects(()->GuiSlateTypeScale.sourceBudget(-1,1),"negative bound");
        rejects(()->GuiSlateTypeScale.sourceBudget(100,Double.NaN),"nonfinite scale");
        rejects(()->GuiSlateTypeScale.sourceBudget(100,0),"zero scale");
        rejects(()->GuiSlateTypeScale.fit("Hello",100,-2,font),"invalid font scale");
        rejects(()->GuiSlateTypeScale.fit("Hello",100,1,null),"null font");
        Random rng=new Random(29102026L);
        String[] words={"Auto Clicker","Velocity","Scaffold","Target HUD",
            "SuperLongModuleTitleThatNeedsClipping","A\u00a7cTest","A😀longName",
            "Combat / RSHIFT","III WWW lll",""};
        for(int i=0;i<60000;i++) {
            int budget=rng.nextInt(350);
            String value=words[rng.nextInt(words.length)];
            double scale=(i&1)==0?GuiSlateTypeScale.PRIMARY:
                GuiSlateTypeScale.SECONDARY;
            String fitted=GuiSlateTypeScale.fit(value,budget,scale,font);
            int sourceWidth=font.applyAsInt(fitted);
            ok(GuiSlateTypeScale.displayWidth(sourceWidth,scale)<=budget,
               "measured glyph run never exceeds output budget");
            ok(GuiSlateTypeScale.sourceBudget(budget,scale)>=0,
               "source budget nonnegative");
            if(fitted.length()>0)
                ok(fitted.charAt(fitted.length()-1)!='\uD83D',
                   "never splits supplementary characters");
        }
        System.out.println("GUI_SLATE_TYPE_SCALE_PASS checks="+checks);
    }
}
