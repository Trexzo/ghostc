package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Minecraft-independent font-aware HUD text fitting for remapped key labels
 * and click counters. No game, GL or input dependencies.
 */
public final class HudTextFit {
    private static final String DOTS="...";
    private static final int MAX_SOURCE_CODEPOINTS=128;
    private static final int MAX_VISIBLE_CODEPOINTS=64;
    private HudTextFit() {}

    private static int measured(ToIntFunction<String> font,String text) {
        int width=font.applyAsInt(text);
        if(width<0)throw new IllegalArgumentException("Negative font width");
        return width;
    }

    private static final class Cleaned {
        final String text;
        final boolean sourceClipped;
        Cleaned(String text,boolean sourceClipped) {
            this.text=text;this.sourceClipped=sourceClipped;
        }
    }
    private static Cleaned clean(String raw) {
        if(raw==null || raw.length()==0)return new Cleaned("?",false);
        StringBuilder out=new StringBuilder();
        int at=0,scanned=0,visible=0;
        while(at<raw.length() && scanned<MAX_SOURCE_CODEPOINTS &&
              visible<MAX_VISIBLE_CODEPOINTS) {
            int cp=raw.codePointAt(at);
            at+=Character.charCount(cp);
            scanned++;
            if(cp==0xA7) {
                if(at<raw.length() && scanned<MAX_SOURCE_CODEPOINTS) {
                    int format=raw.codePointAt(at);
                    at+=Character.charCount(format);
                    scanned++;
                }
                continue;
            }
            if(Character.isISOControl(cp) ||
               (cp>=Character.MIN_SURROGATE && cp<=Character.MAX_SURROGATE))
                continue;
            out.appendCodePoint(cp);
            visible++;
        }
        // A source scan cap is also a *truncation*, even when the 64-scalar
        // prefix fits the available font width. Never silently omit the tail.
        return new Cleaned(out.length()==0?"?":out.toString(),at<raw.length());
    }

    /**
     * Bounds text by its actual font width and never splits a Unicode scalar.
     * An ellipsis makes any truncation explicit instead of displaying a
     * misleading partial numeric value.
     */
    public static String fit(String raw,int budget,ToIntFunction<String> font) {
        if(budget<0 || font==null)
            throw new IllegalArgumentException("HUD font/width");
        if(budget==0)return "";
        Cleaned cleaned=clean(raw);
        String clean=cleaned.text;
        if(!cleaned.sourceClipped && measured(font,clean)<=budget)return clean;
        if(measured(font,DOTS)>budget)return "";
        StringBuilder prefix=new StringBuilder();
        String best=DOTS;
        for(int at=0;at<clean.length();) {
            int cp=clean.codePointAt(at);
            String attempt=prefix.toString()+new String(Character.toChars(cp))+DOTS;
            if(measured(font,attempt)>budget)break;
            prefix.appendCodePoint(cp);
            best=attempt;
            at+=Character.charCount(cp);
        }
        return best;
    }

    /**
     * Preserve the original CPS label when it fits; otherwise show both
     * exact numeric counters in a shorter form before using marked truncation.
     */
    public static String cps(int left,int right,boolean showRight,
                             int budget,ToIntFunction<String> font) {
        if(left<0 || right<0 || budget<0 || font==null)
            throw new IllegalArgumentException("CPS readout");
        String full=showRight ? "L "+left+" | R "+right+" CPS" : "L "+left+" CPS";
        if(measured(font,full)<=budget)return full;
        String[] compact=showRight
            ? new String[]{"L "+left+" | R "+right,
                           "L"+left+" R"+right,"L"+left+"/R"+right}
            : new String[]{"L "+left,"L"+left};
        for(String candidate:compact)
            if(measured(font,candidate)<=budget)return candidate;
        return fit(full,budget,font);
    }
}
