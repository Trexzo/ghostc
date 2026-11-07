package dev.ghostfusion.tests;

import dev.ghostfusion.ui.DiagnosticsHudStyle;
import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.ui.UiState;

/** Slate diagnostics must not fall back to lavender on the default UI. */
public final class DiagnosticsHudStyleTests {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        DiagnosticsHudStyle slate=DiagnosticsHudStyle.of(UiState.Theme.SLATE);
        DiagnosticsHudStyle indigo=DiagnosticsHudStyle.of(UiState.Theme.INDIGO);
        DiagnosticsHudStyle graphite=DiagnosticsHudStyle.of(UiState.Theme.GRAPHITE);
        ok(slate!=indigo,"Slate keeps separate graphics");
        ok(indigo==graphite,"legacy themes retain exact original diagnostics");
        ok(indigo.panel==0xC31B2133 && indigo.title==0xFFAEAAFF &&
           indigo.body==0xFFC9D1DF,"legacy pixel colors unchanged");
        ok(slate.title==HudThemeTokens.of("slate").accent,
           "diagnostic header follows Slate ice accent");
        ok(slate.body==HudThemeTokens.of("slate").text,
           "diagnostic rows follow Slate off-white text");
        ok(slate.panel!=indigo.panel && slate.title!=indigo.title,
           "lavender is not used by Slate");
        for(int i=0;i<50000;i++)
            ok(DiagnosticsHudStyle.of((i&1)==0?UiState.Theme.SLATE:
                UiState.Theme.INDIGO)==((i&1)==0?slate:indigo),
                "immutable theme lookup "+i);
        boolean rejected=false;
        try{DiagnosticsHudStyle.of(null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"no unknown theme");
        System.out.println("DIAGNOSTICS_HUD_STYLE_PASS checks="+checks);
    }
}
