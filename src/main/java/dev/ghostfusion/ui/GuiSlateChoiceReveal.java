package dev.ghostfusion.ui;

import java.util.function.ToIntFunction;

/**
 * Reveal a clipped Slate setting choice in the existing noninteractive
 * settings footer, not in a tooltip obscuring its clickable control.
 * Uses the exact Minecraft font metrics supplied by the caller.
 * Input, settings, saved profiles, HUD painting and layouts are unchanged.
 */
public final class GuiSlateChoiceReveal {
    private static final String PREFIX="VALUE  ";
    private GuiSlateChoiceReveal() { }

    /**
     * null means no useful readout is needed or there is not enough room.
     * A partially displayed long value ends in explicit dots; never silently
     * pretend its truncated prefix is the complete configured choice.
     */
    public static String footer(String raw,int choicePixels,int footerPixels,
                                ToIntFunction<String> font) {
        if(raw==null||choicePixels<0||footerPixels<0||font==null)
            throw new IllegalArgumentException("Slate choice reveal");
        String original=GuiSettingPresentation.choice(raw);
        int full=font.applyAsInt(original);
        int marker=font.applyAsInt(PREFIX);
        if(full<0||marker<0)throw new IllegalArgumentException("Native font metrics");
        if(full<=choicePixels)return null;
        int available=footerPixels-marker;
        if(available<=0)return null;
        String fitted=HudTextFit.fit(original,available,font);
        if(fitted.isEmpty()||"...".equals(fitted))return null;
        return PREFIX+fitted;
    }
}
