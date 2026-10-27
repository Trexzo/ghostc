package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudPalette;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** R2.56 immutable skin tokens, classic parity and persisted GUI selection. */
public final class TargetHudPaletteTests {
    private static int checks;
    private static void ok(boolean valid,String why){
        checks++;
        if(!valid)throw new AssertionError(why);
    }
    private static void invalid(String value) {
        boolean refused=false;
        try{TargetHudPalette.of(value);}
        catch(IllegalArgumentException expected){refused=true;}
        ok(refused,"invalid skin is not silently coerced");
    }
    private static void colors() {
        TargetHudPalette classic=TargetHudPalette.of("classic");
        TargetHudPalette glass=TargetHudPalette.of("glass");
        ok(classic==TargetHudPalette.of("classic")&&
           glass==TargetHudPalette.of("glass"),
           "immutable palette instances reused");
        ok(classic.background==0xD0141A2B,"original panel ARGB unchanged");
        ok(classic.accent==0xFF9A8CFF,"original purple accent unchanged");
        ok(classic.track==0xFF414B60,"original health track unchanged");
        ok(classic.name==0xFFF1F2FF,"original name text unchanged");
        ok(classic.healthText==0xFFB6C2DD,"original health label unchanged");
        ok(classic.outline==0,"legacy classic never draws an extra outline");
        ok(glass.background!=classic.background &&
           glass.accent!=classic.accent &&
           glass.track!=classic.track &&
           glass.name!=classic.name &&
           glass.healthText!=classic.healthText,
           "opt-in glass has distinct coordinated palette");
        ok((glass.background>>>24)>0 &&
           (glass.accent>>>24)==0xFF &&
           (glass.outline>>>24)>0,
           "glass overlay colors retain visible ARGB channels");
        TargetHudPalette minimal=TargetHudPalette.of("minimal");
        TargetHudPalette slate=TargetHudPalette.of("slate");
        ok(minimal!=classic && slate!=glass && minimal!=slate,
            "modern skins retain separate immutable color identity");
        ok(minimal.secondary!=0 && slate.secondary!=0,
            "modern skins have distinct secondary accents");
        invalid(null);invalid("");invalid("GLASS");invalid("default");
        Random random=new Random(2562026L);
        for(int i=0;i<100000;i++){
            String choice=random.nextBoolean()?"classic":"glass";
            TargetHudPalette actual=TargetHudPalette.of(choice);
            ok(actual==(choice.equals("classic")?classic:glass),
               "100k deterministic frame palette reads are allocation-free "+i);
        }
    }
    private static void profile() throws Exception {
        Path path=Files.createTempDirectory("ghostc-r256-target-skin-");
        HostRuntime host=new HostRuntime(path);
        ok("slate".equals(host.targetSkin()),"fresh Target HUD defaults to Slate");
        ok("slate".equals(host.modules().get("hud.target").settings().get("skin").serialize()),
           "ClickGUI exposes the saved-compatible Slate skin ID");
        host.uiActions().cycle("hud.target","skin");
        ok("classic".equals(host.targetSkin()),"explicit Classic style remains available");
        host.uiActions().cycle("hud.target","skin");
        ok("glass".equals(host.targetSkin()),"GUI setting immediately selects glass");
        host.persist();
        host.close();
        HostRuntime loaded=new HostRuntime(path);
        ok("glass".equals(loaded.targetSkin()),"glass skin survives profile restoration");
        loaded.uiActions().cycle("hud.target","skin");
        ok("minimal".equals(loaded.targetSkin()),"GUI selects Minimal");
        loaded.uiActions().cycle("hud.target","skin");
        ok("slate".equals(loaded.targetSkin()),"GUI selects Slate");
        loaded.uiActions().cycle("hud.target","skin");
        ok("classic".equals(loaded.targetSkin()),"GUI cycles back to classic");
        // Persist Slate independently to confirm new choice roundtrips safely.
        loaded.uiActions().cycle("hud.target","skin");
        loaded.uiActions().cycle("hud.target","skin");
        loaded.uiActions().cycle("hud.target","skin");
        ok("slate".equals(loaded.targetSkin()),"reselect Slate through GUI");
        loaded.persist();loaded.close();
        HostRuntime slate=new HostRuntime(path);
        ok("slate".equals(slate.targetSkin()),"Slate survives profile reload");
        slate.uiActions().cycle("hud.target","skin");
        slate.persist();slate.close();
        HostRuntime classic=new HostRuntime(path);
        ok("classic".equals(classic.targetSkin()),"restored classic remains stable");
        classic.close();
    }
    public static void main(String[] args) throws Exception {
        colors();profile();
        ok(checks>100000,"substantial Target HUD skin regression coverage");
        System.out.println("TARGET_HUD_PALETTE_PASS checks="+checks);
    }
}
