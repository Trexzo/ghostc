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
        HudThemeTokens slate=HudThemeTokens.of("slate");
        ok(classic!=minimal && minimal!=slate && slate!=classic,"independent themes");
        ok(slate.accent!=slate.secondary && slate.sheen!=0 &&
            slate.track!=slate.panel && slate.frame!=slate.sheen,
            "Slate keeps subtle accent hierarchy");
        for(int i=0;i<20000;i++)
            ok(HudThemeTokens.of(i%2==0?"slate":"minimal")==
                (i%2==0?slate:minimal),"allocation-free palette lookup "+i);
        ok(dev.ghostfusion.features.TargetHudPalette.of("slate").background==slate.panel &&
            dev.ghostfusion.features.TargetHudPalette.of("slate").accent==slate.accent,
            "Target HUD and Module List derive the same Slate palette");
        boolean refused=false;
        try{HudThemeTokens.of("unknown");}catch(IllegalArgumentException expected){refused=true;}
        ok(refused,"reject invalid style");
        Path dir=Files.createTempDirectory("ghostc-module-style");
        HostRuntime host=new HostRuntime(dir);
        ok("slate".equals(host.moduleListAppearance()) &&
            !host.moduleListCompact(),"fresh profiles use Slate and regular spacing");
        ok(!host.moduleListBackground()&&!host.moduleListAccent(),
            "Slate ArrayList defaults to text only without boxed rows");
        ok("slate".equals(host.moduleListAppearance()),"Slate is the initial style");
        host.uiActions().bool("hud.modulelist","compact");
        host.persist();host.close();
        HostRuntime reopened=new HostRuntime(dir);
        ok("slate".equals(reopened.moduleListAppearance()) &&
           reopened.moduleListCompact(),"style and compact persisted");
        reopened.uiActions().cycle("hud.cps","appearance");
        reopened.uiActions().cycle("hud.cps","appearance");
        ok("slate".equals(reopened.keysAppearance()) &&
           "minimal".equals(reopened.cpsAppearance()),
           "first-party Keystrokes and CPS style choices can differ");
        reopened.persist();
        reopened.close();
        HostRuntime again=new HostRuntime(dir);
        ok("slate".equals(again.keysAppearance()) &&
           "minimal".equals(again.cpsAppearance()),
           "Keystrokes/CPS styles survive profile restoration");
        again.uiActions().cycle("hud.cps","appearance");
        ok("slate".equals(again.cpsAppearance()),
           "CPS supports Slate");
        again.close();
        Path original=Files.createTempDirectory("ghostc-legacy-hud-");
        HostRuntime legacy=new HostRuntime(original);
        ok("slate".equals(legacy.keysAppearance()) &&
           "slate".equals(legacy.cpsAppearance()),
           "fresh installations have non-purple Keys and CPS visuals");
        legacy.close();
        System.out.println("HUD_THEME_TOKENS_PASS checks="+checked);
    }
}
