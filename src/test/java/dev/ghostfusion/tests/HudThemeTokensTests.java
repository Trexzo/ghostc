package dev.ghostfusion.tests;
import dev.ghostfusion.ui.HudThemeTokens;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HudThemeTokensTests {
    private static int checked;
    private static void ok(boolean b,String s){checked++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception {
        HudThemeTokens classic=HudThemeTokens.of("classic");
        HudThemeTokens minimal=HudThemeTokens.of("minimal");
        HudThemeTokens nebula=HudThemeTokens.of("nebula");
        ok(classic!=minimal && minimal!=nebula && nebula!=classic,"independent themes");
        ok(nebula.accent!=nebula.secondary && nebula.sheen!=0 &&
            nebula.track!=nebula.panel && nebula.frame!=nebula.sheen,
            "Nebula keeps subtle accent hierarchy");
        for(int i=0;i<20000;i++)
            ok(HudThemeTokens.of(i%2==0?"nebula":"minimal")==
                (i%2==0?nebula:minimal),"allocation-free palette lookup "+i);
        ok(dev.ghostfusion.features.TargetHudPalette.of("nebula").background==nebula.panel &&
            dev.ghostfusion.features.TargetHudPalette.of("nebula").accent==nebula.accent,
            "Target HUD and Module List derive the same Nebula palette");
        boolean refused=false;
        try{HudThemeTokens.of("unknown");}catch(IllegalArgumentException expected){refused=true;}
        ok(refused,"reject invalid style");
        Path dir=Files.createTempDirectory("ghostc-module-style");
        HostRuntime host=new HostRuntime(dir);
        ok("nebula".equals(host.moduleListAppearance()) &&
            !host.moduleListCompact(),"fresh profiles use Slate and regular spacing");
        ok(!host.moduleListBackground()&&!host.moduleListAccent(),
            "Slate ArrayList defaults to text only without boxed rows");
        ok("nebula".equals(host.moduleListAppearance()),"Slate is the initial style");
        host.uiActions().bool("hud.modulelist","compact");
        host.persist();host.close();
        HostRuntime reopened=new HostRuntime(dir);
        ok("nebula".equals(reopened.moduleListAppearance()) &&
           reopened.moduleListCompact(),"style and compact persisted");
        reopened.uiActions().cycle("hud.cps","appearance");
        reopened.uiActions().cycle("hud.cps","appearance");
        ok("nebula".equals(reopened.keysAppearance()) &&
           "minimal".equals(reopened.cpsAppearance()),
           "first-party Keystrokes and CPS style choices can differ");
        reopened.persist();
        reopened.close();
        HostRuntime again=new HostRuntime(dir);
        ok("nebula".equals(again.keysAppearance()) &&
           "minimal".equals(again.cpsAppearance()),
           "Keystrokes/CPS styles survive profile restoration");
        again.uiActions().cycle("hud.cps","appearance");
        ok("nebula".equals(again.cpsAppearance()),
           "CPS supports Slate");
        again.close();
        Path original=Files.createTempDirectory("ghostc-legacy-hud-");
        HostRuntime legacy=new HostRuntime(original);
        ok("nebula".equals(legacy.keysAppearance()) &&
           "nebula".equals(legacy.cpsAppearance()),
           "fresh installations have non-purple Keys and CPS visuals");
        legacy.close();
        System.out.println("HUD_THEME_TOKENS_PASS checks="+checked);
    }
}
