package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Read-only Slate numeric labels in both wide and compact layouts. Prefer the complete source-formatted
 * value, then a complete unitless number. Never paint a partially clipped
 * number as though it were the actual setting. No input or setting mutations.
 */
public final class GuiSlateNumberReadout {
    private static final String PREFIX="VALUE  ";
    private GuiSlateNumberReadout() { }

    private static int measured(ToIntFunction<String> font,String text) {
        int pixels=font.applyAsInt(text);
        if(pixels<0)throw new IllegalArgumentException("Native font width");
        return pixels;
    }

    public static String inline(String id,double value,int budget,
                                ToIntFunction<String> font) {
        if(id==null||!Double.isFinite(value)||budget<0||font==null)
            throw new IllegalArgumentException("Slate numeric label");
        String full=GuiSettingPresentation.number(id,value);
        if(measured(font,full)<=budget)return full;
        // Remove only the unit suffix, retaining the displayed percentage scale.
        String plain=GuiSettingPresentation.numberWithoutSuffix(id,value);
        if(measured(font,plain)<=budget)return plain;
        return "";
    }

    /** The existing warning-safe footer reveals units only when all fit. */
    public static String footer(String id,double value,String displayed,
                                int budget,ToIntFunction<String> font) {
        if(id==null||!Double.isFinite(value)||displayed==null||
           budget<0||font==null)
            throw new IllegalArgumentException("Slate numeric footer");
        String full=GuiSettingPresentation.number(id,value);
        if(displayed.equals(full))return null;
        String result=PREFIX+full;
        return measured(font,result)<=budget?result:null;
    }
}
