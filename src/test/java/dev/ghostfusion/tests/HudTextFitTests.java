package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudTextFit;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Seeded font, supplementary Unicode, bounded CPS and fallback regressions. */
public final class HudTextFitTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static final ToIntFunction<String> FONT=new ToIntFunction<String>() {
        public int applyAsInt(String s) {
            int width=0;
            for(int i=0;i<s.length();) {
                int cp=s.codePointAt(i);
                i+=Character.charCount(cp);
                width+=cp=='W'?10:cp=='i'?2:cp=='.'?3:
                       cp>0xffff?10:6;
            }
            return width;
        }
    };
    private static void rejects(Runnable r,String why) {
        boolean thrown=false;
        try {r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,why);
    }
    private static void cases() {
        ok("W".equals(HudTextFit.fit("W",20,FONT)),
           "short remapped label unchanged");
        ok("...".equals(HudTextFit.fit("WWWW",10,FONT)),
           "oversized remap has clear ellipsis");
        ok("".equals(HudTextFit.fit("WWWW",3,FONT)),
           "too narrow screen suppresses label safely");
        ok("?".equals(HudTextFit.fit(null,6,FONT)),
           "null key binding has fallback");
        ok("Hi".equals(HudTextFit.fit("\u00a7bHi\n",20,FONT)),
           "format and newline removed without altering printable key label");
        String scalar=new String(Character.toChars(0x1D800));
        ok((scalar+"A").equals(HudTextFit.fit(scalar+"A",100,FONT)),
           "valid supplementary scalar survives surrogate-valued low bits");
        ok("A".equals(HudTextFit.fit("\uD800A\uDC00",100,FONT)),
           "lone surrogate code units removed");
        ok("L 10 | R 11 CPS".equals(HudTextFit.cps(10,11,true,100,FONT)),
           "full two-counter legacy text preserved");
        ok("L 7 CPS".equals(HudTextFit.cps(7,9,false,90,FONT)),
           "left-only legacy text unchanged");
        String crowded=HudTextFit.cps(12345,67890,true,80,FONT);
        ok(crowded.startsWith("L")&&!crowded.equals("L 12345 | R 67890 CPS"),
           "high CPS switches to compact form");
        ok(FONT.applyAsInt(crowded)<=80,"compact count fits screen");
        rejects(()->HudTextFit.fit("A",-1,FONT),"negative width rejected");
        rejects(()->HudTextFit.fit("A",10,null),"missing font rejected");
        rejects(()->HudTextFit.cps(-1,4,true,100,FONT),"negative CPS rejected");
        rejects(()->HudTextFit.cps(1,2,true,-1,FONT),"negative budget rejected");
        rejects(()->HudTextFit.cps(1,2,true,20,null),"null CPS font rejected");
        ToIntFunction<String> corrupt=new ToIntFunction<String>() {
            public int applyAsInt(String s){return -1;}
        };
        rejects(()->HudTextFit.fit("X",20,corrupt),"invalid font width rejected");
        rejects(()->HudTextFit.cps(1,1,false,30,corrupt),"CPS rejects invalid font");
    }
    private static void seeded() {
        Random r=new Random(2312026L);
        String chars="WimabcZd.gJX";
        for(int i=0;i<18000;i++) {
            StringBuilder name=new StringBuilder();
            int size=r.nextInt(190);
            for(int j=0;j<size;j++)name.append(chars.charAt(r.nextInt(chars.length())));
            if(i%13==0)name.append("\u00a7a");
            if(i%27==0)name.append("\uD83D\uDE80");
            int width=r.nextInt(180);
            String fitted=HudTextFit.fit(name.toString(),width,FONT);
            ok(FONT.applyAsInt(fitted)<=width,"font width bounded "+i);
            ok(!fitted.contains("\u00a7"),"format stripped "+i);
            ok(fitted.length()<200,"bounded output "+i);
            ok(!fitted.endsWith("\uD800")&&!fitted.endsWith("\uDC00"),
               "no split surrogate "+i);
        }
        for(int i=0;i<15000;i++) {
            int l=r.nextInt(10000000),rt=r.nextInt(10000000);
            boolean show=r.nextBoolean();
            int budget=r.nextInt(150);
            String value=HudTextFit.cps(l,rt,show,budget,FONT);
            ok(FONT.applyAsInt(value)<=budget,"bounded numeric label "+i);
            ok(!value.contains("\u00a7"),"CPS no formatting "+i);
            if(show&&budget>=100)ok(value.startsWith("L"),
                "left count remains leading "+i);
        }
    }
    public static void main(String[] args) {
        cases();seeded();
        ok(checks>95000,"substantial seeded font/CPS coverage");
        System.out.println("HUD_TEXT_FIT_PASS assertions="+checks);
    }
}
