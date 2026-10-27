package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiThemePalette;
import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.UiSettingsStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * UI chrome theme compatibility, persistence and immutable visual tokens.
 * No Minecraft runtime is needed for this exact-contract regression.
 */
public final class GuiThemePaletteTests {
    private static int checks;
    private static void ok(boolean b,String msg) {
        checks++; if(!b)throw new AssertionError(msg);
    }
    private static void invalid(Runnable action,String label) {
        boolean rejected=false;
        try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,label);
    }
    public static void main(String[] args)throws Exception {
        UiState ui=new UiState();
        ok(ui.theme()==UiState.Theme.SLATE,"Slate is the first-run default");
        GuiThemePalette indigo=GuiThemePalette.of(UiState.Theme.INDIGO);
        GuiThemePalette graphite=GuiThemePalette.of(UiState.Theme.GRAPHITE);
        GuiThemePalette slate=GuiThemePalette.of(UiState.Theme.SLATE);
        ok(indigo.background==0xF3161A2C && indigo.panel==0xFF22283E &&
           indigo.accent==0xFF9A8CFF,"original INDIGO chrome unchanged");
        ok(graphite.background==0xF31A1C20 && graphite.panel==0xFF303339 &&
           graphite.accent==0xFF69D9C6,"original GRAPHITE chrome unchanged");
        ok(indigo.label.equals("INDIGO") && graphite.label.equals("MINT"),
            "legacy theme button labels unchanged");
        ok(slate.slate && !indigo.slate && !graphite.slate &&
           "SLATE".equals(slate.label),"Slate retains persisted Slate ID");
        ok(slate.background==0xF5121519 &&
           slate.panel==0xFF1B2026,"matte charcoal panels");
        ok(slate.accent==0xFFA7D3E0 &&
           slate.text==0xFFF1F4F6,"the active accent is non-purple");
        ok(slate.accent==HudThemeTokens.of("slate").accent &&
           slate.secondary==HudThemeTokens.of("slate").secondary &&
           slate.text==HudThemeTokens.of("slate").text,
           "Slate chrome and HUD share first-party tokens");
        ok(GuiThemePalette.next(UiState.Theme.INDIGO)==UiState.Theme.GRAPHITE &&
           GuiThemePalette.next(UiState.Theme.GRAPHITE)==UiState.Theme.SLATE &&
           GuiThemePalette.next(UiState.Theme.SLATE)==UiState.Theme.INDIGO,
           "theme button is deterministic three-stage cycle");
        invalid(()->GuiThemePalette.of(null),"null style rejected");
        invalid(()->GuiThemePalette.next(null),"null cycle rejected");
        Random rng=new Random(261026L);
        for(int i=0;i<35000;i++) {
            UiState.Theme current=UiState.Theme.values()[rng.nextInt(3)];
            ok(GuiThemePalette.of(current)==GuiThemePalette.of(current),
                "immutable singleton reused "+i);
        }
        Path dir=Files.createTempDirectory("ghostc-r4-slate-clickgui");
        UiSettingsStore store=new UiSettingsStore(dir,ui);
        ui.theme(UiState.Theme.SLATE);
        ui.layout(UiState.Layout.COMPACT);
        store.save();
        UiState reloaded=new UiState();
        new UiSettingsStore(dir,reloaded).load();
        ok(reloaded.theme()==UiState.Theme.SLATE &&
           reloaded.layout()==UiState.Layout.COMPACT,"SLATE persists schema-1");
        ui.theme(UiState.Theme.GRAPHITE);
        store.save();
        new UiSettingsStore(dir,reloaded).load();
        ok(reloaded.theme()==UiState.Theme.GRAPHITE,
            "switching back to legacy theme persists");
        System.out.println("GUI_THEME_PALETTE_PASS checks="+checks);
    }
}
