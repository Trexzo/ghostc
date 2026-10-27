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
        ok(ui.theme()==UiState.Theme.NEBULA,"Slate is the first-run default");
        GuiThemePalette indigo=GuiThemePalette.of(UiState.Theme.INDIGO);
        GuiThemePalette graphite=GuiThemePalette.of(UiState.Theme.GRAPHITE);
        GuiThemePalette nebula=GuiThemePalette.of(UiState.Theme.NEBULA);
        ok(indigo.background==0xF3161A2C && indigo.panel==0xFF22283E &&
           indigo.accent==0xFF9A8CFF,"original INDIGO chrome unchanged");
        ok(graphite.background==0xF31A1C20 && graphite.panel==0xFF303339 &&
           graphite.accent==0xFF69D9C6,"original GRAPHITE chrome unchanged");
        ok(indigo.label.equals("INDIGO") && graphite.label.equals("MINT"),
            "legacy theme button labels unchanged");
        ok(nebula.nebula && !indigo.nebula && !graphite.nebula &&
           "SLATE".equals(nebula.label),"Slate retains persisted Nebula ID");
        ok(nebula.background==0xF5121519 &&
           nebula.panel==0xFF1B2026,"matte charcoal panels");
        ok(nebula.accent==0xFFA7D3E0 &&
           nebula.text==0xFFF1F4F6,"the active accent is non-purple");
        ok(nebula.accent==HudThemeTokens.of("nebula").accent &&
           nebula.secondary==HudThemeTokens.of("nebula").secondary &&
           nebula.text==HudThemeTokens.of("nebula").text,
           "Nebula chrome and HUD share first-party tokens");
        ok(GuiThemePalette.next(UiState.Theme.INDIGO)==UiState.Theme.GRAPHITE &&
           GuiThemePalette.next(UiState.Theme.GRAPHITE)==UiState.Theme.NEBULA &&
           GuiThemePalette.next(UiState.Theme.NEBULA)==UiState.Theme.INDIGO,
           "theme button is deterministic three-stage cycle");
        invalid(()->GuiThemePalette.of(null),"null style rejected");
        invalid(()->GuiThemePalette.next(null),"null cycle rejected");
        Random rng=new Random(261026L);
        for(int i=0;i<35000;i++) {
            UiState.Theme current=UiState.Theme.values()[rng.nextInt(3)];
            ok(GuiThemePalette.of(current)==GuiThemePalette.of(current),
                "immutable singleton reused "+i);
        }
        Path dir=Files.createTempDirectory("ghostc-r4-nebula-clickgui");
        UiSettingsStore store=new UiSettingsStore(dir,ui);
        ui.theme(UiState.Theme.NEBULA);
        ui.layout(UiState.Layout.COMPACT);
        store.save();
        UiState reloaded=new UiState();
        new UiSettingsStore(dir,reloaded).load();
        ok(reloaded.theme()==UiState.Theme.NEBULA &&
           reloaded.layout()==UiState.Layout.COMPACT,"NEBULA persists schema-1");
        ui.theme(UiState.Theme.GRAPHITE);
        store.save();
        new UiSettingsStore(dir,reloaded).load();
        ok(reloaded.theme()==UiState.Theme.GRAPHITE,
            "switching back to legacy theme persists");
        System.out.println("GUI_THEME_PALETTE_PASS checks="+checks);
    }
}
