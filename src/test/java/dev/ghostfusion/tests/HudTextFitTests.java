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
        String sixtyFour=new String(new char[64]).replace((char)0,'X');
        ok(sixtyFour.equals(HudTextFit.fit(sixtyFour,1000,FONT)),
           "exact 64-scalar label preserves full text");
        ok((sixtyFour+"...").equals(HudTextFit.fit(sixtyFour+"EXTRA",1000,FONT)),
           "bounded source truncation is always visibly marked");
        String rocket=new String(Character.toChars(0x1F680));
        StringBuilder longUnicode=new StringBuilder();
        for(int i=0;i<65;i++)longUnicode.append(rocket);
        String clipped=HudTextFit.fit(longUnicode.toString(),1000,FONT);
        ok(clipped.endsWith("...") && clipped.codePointCount(0,clipped.length())==67,
           "long Unicode label fits by scalar without silently losing a tail");
        ok(clipped.charAt(clipped.length()-4)==rocket.charAt(1),
           "last retained Unicode scalar includes its trailing surrogate");
        final int[] widths={0};
        ToIntFunction<String> measured=new ToIntFunction<String>() {
            public int applyAsInt(String txt) {widths[0]++;return FONT.applyAsInt(txt);}
        };
        StringBuilder huge=new StringBuilder();
        for(int i=0;i<10000;i++)huge.append("VeryLongClickGUIName");
        String bounded=HudTextFit.fit(huge.toString(),1000,measured);
        ok(bounded.endsWith("...") && widths[0]<=70,
           "giant untrusted GUI label gets explicit ellipsis in bounded font measurements");
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
        // GUI names and configuration strings can exceed the 64-scalar
        // sanitization cap; every silently clipped tail must get an ellipsis.
        String emoji=new String(Character.toChars(0x1F30D));
        for(int i=0;i<5000;i++) {
            StringBuilder longName=new StringBuilder();
            int size=65+r.nextInt(450);
            for(int j=0;j<size;j++) {
                if(j%17==0)longName.append(emoji);
                else longName.append(chars.charAt(r.nextInt(chars.length())));
            }
            int width=9+r.nextInt(450);
            String fitted=HudTextFit.fit(longName.toString(),width,FONT);
            ok(FONT.applyAsInt(fitted)<=width,"long GUI label within actual width "+i);
            ok(fitted.endsWith("..."),"long GUI label always marks truncation "+i);
            ok(fitted.codePointCount(0,fitted.length())<=67,
               "long GUI label bounded to 64 scalars + ellipsis "+i);
            ok(!fitted.contains("\u00a7"),"no Minecraft formatting in GUI label "+i);
            for(int j=0;j<fitted.length();j++) {
                char ch=fitted.charAt(j);
                if(Character.isHighSurrogate(ch))
                    ok(j+1<fitted.length() && Character.isLowSurrogate(fitted.charAt(j+1)),
                       "GUI name high surrogate always paired "+i);
                if(Character.isLowSurrogate(ch))
                    ok(j>0 && Character.isHighSurrogate(fitted.charAt(j-1)),
                       "GUI name low surrogate always paired "+i);
            }
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
