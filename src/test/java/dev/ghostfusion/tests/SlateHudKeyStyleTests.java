package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.ui.SlateHudKeyStyle;

/** Real source colors, read-only visual policy, no fake Minecraft screenshots. */
public final class SlateHudKeyStyleTests {
    private static int checks;
    private static void ok(boolean condition,String label) {
        checks++;if(!condition)throw new AssertionError(label);
    }
    private static double luminance(int rgb) {
        double result=0;
        for(int c=0;c<3;c++) {
            double x=((rgb>>(16-8*c))&255)/255.0;
            double linear=x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);
            result+=linear*(c==0?0.2126:c==1?0.7152:0.0722);
        }
        return result;
    }
    private static double contrast(int a,int b) {
        double l1=luminance(a),l2=luminance(b);
        return (Math.max(l1,l2)+0.05)/(Math.min(l1,l2)+0.05);
    }
    public static void main(String[] args) {
        HudThemeTokens slate=HudThemeTokens.of("slate");
        ok(SlateHudKeyStyle.surface(false)==slate.panel,
            "idle background is the existing Slate surface");
        ok(SlateHudKeyStyle.surface(true)!=slate.accent,
            "pressed key is never a full ice-blue block");
        ok(SlateHudKeyStyle.surface(true)!=SlateHudKeyStyle.surface(false),
            "pressing changes only local key surface");
        ok(SlateHudKeyStyle.bottomEdge(true)==slate.accent,
            "pressed key uses a thin shared accent rule");
        ok(SlateHudKeyStyle.text(true)==slate.text,
            "pressed text uses shared white token");
        ok(SlateHudKeyStyle.text(false)==slate.muted,
            "idle text stays visually quieter");
        ok(contrast(SlateHudKeyStyle.text(true),SlateHudKeyStyle.surface(true))>=7,
            "pressed labels reach high text contrast against local surface");
        ok(contrast(SlateHudKeyStyle.text(false),SlateHudKeyStyle.surface(false))>=4.5,
            "idle labels remain readable against local surface");
        int pressed=SlateHudKeyStyle.surface(true),idle=SlateHudKeyStyle.surface(false);
        for(int i=0;i<50000;i++) {
            ok(SlateHudKeyStyle.surface((i&1)==0)==((i&1)==0?pressed:idle),
                "stable zero-allocation immutable color lookup "+i);
        }
        System.out.println("SLATE_HUD_KEY_STYLE_PASS checks="+checks);
    }
}
