package dev.ghostfusion.features;

import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * First-party Target HUD text layout. All widths are measured using the
 * calling Minecraft font; no long name/health label may overlap its neighbour.
 * Contains no Minecraft, GL, packet or network dependencies.
 */
public final class TargetHudTextLayout {
    private static final String ELLIPSIS="...";
    // Independent from the 64 visible-codepoint limit.
    private static final int MAX_SCANNED_CODEPOINTS=256;
    private TargetHudTextLayout() {}
    public static final class Result {
        public final String name,healthLabel;
        public final int nameWidth,healthLabelWidth;
        public final double healthFraction;
        Result(String name,String healthLabel,int nameWidth,int healthLabelWidth,
               double fraction) {
            this.name=name;this.healthLabel=healthLabel;this.nameWidth=nameWidth;
            this.healthLabelWidth=healthLabelWidth;this.healthFraction=fraction;
        }
    }
    private static int pixels(ToIntFunction<String> widths,String text) {
        int n=widths.applyAsInt(text);
        if(n<0)throw new IllegalArgumentException("Negative font width");
        return n;
    }
    private static String sanitize(String raw,int fallbackId) {
        if(raw==null || raw.length()==0)return "Player #"+fallbackId;
        StringBuilder clean=new StringBuilder();
        int at=0,chars=0,scanned=0;
        while(at<raw.length() && chars<64 && scanned<MAX_SCANNED_CODEPOINTS) {
            int cp=raw.codePointAt(at);
            at+=Character.charCount(cp);
            scanned++;
            // Minecraft formatting marker takes a following style character.
            if(cp==0xA7) {
                if(at<raw.length()){
                    int next=raw.codePointAt(at);
                    at+=Character.charCount(next);
                }
                continue;
            }
            // Never narrow full Unicode scalars to 16-bit chars.
            if(Character.isISOControl(cp) ||
               (cp>=Character.MIN_SURROGATE && cp<=Character.MAX_SURROGATE))
                continue;
            clean.appendCodePoint(cp);
            chars++;
        }
        if(clean.length()==0)return "Player #"+fallbackId;
        return clean.toString();
    }
    private static String fit(String value,int budget,ToIntFunction<String> font) {
        if(budget<=0)return "";
        if(pixels(font,value)<=budget)return value;
        if(pixels(font,ELLIPSIS)>budget)return "";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<value.length();) {
            int cp=value.codePointAt(i);
            int next=i+Character.charCount(cp);
            String candidate=out.toString()+new String(Character.toChars(cp))+ELLIPSIS;
            if(pixels(font,candidate)>budget)break;
            out.appendCodePoint(cp);
            i=next;
        }
        return out.toString()+ELLIPSIS;
    }
    public static Result layout(String rawName,int fallbackId,boolean showHealth,
                                String mode,double current,double maximum,
                                int cardWidth,ToIntFunction<String> font) {
        if(font==null||cardWidth<30)throw new IllegalArgumentException("HUD width/font");
        if(!"values".equals(mode)&&!"percent".equals(mode))
            throw new IllegalArgumentException("HUD health text mode");
        boolean valid=Double.isFinite(current)&&Double.isFinite(maximum)&&
                      current>=0&&maximum>0;
        double fraction=valid?Math.max(0,Math.min(1,current/maximum)):0;
        String label="";
        int labelWidth=0;
        if(showHealth) {
            if(!valid)label="?";
            else if("percent".equals(mode)) label=Math.round(fraction*100)+"%";
            else label=String.format(Locale.ROOT,"%.1f / %.1f",current,maximum);
            // Keep a bounded health readout; exceptionally large custom
            // health values degrade to percentage rather than obscuring name.
            int labelBudget=Math.min(72,Math.max(0,cardWidth-24));
            if(pixels(font,label)>labelBudget)
                label=valid?Math.round(fraction*100)+"%":"?";
            if(pixels(font,label)>labelBudget)label=fit(label,labelBudget,font);
            labelWidth=pixels(font,label);
        }
        // Card margins (9 each), and 6px gap between simultaneous labels.
        int nameBudget=Math.max(0,cardWidth-18-(showHealth?labelWidth+6:0));
        String name=fit(sanitize(rawName,fallbackId),nameBudget,font);
        return new Result(name,label,pixels(font,name),labelWidth,fraction);
    }
}
