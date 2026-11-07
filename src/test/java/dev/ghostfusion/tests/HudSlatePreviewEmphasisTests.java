package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudSlatePreviewEmphasis;
import dev.ghostfusion.ui.HudThemeTokens;
import java.util.Random;

/** 50k seeded render-role and active-visibility contracts for Slate F7. */
public final class HudSlatePreviewEmphasisTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void invalid(Runnable r,String why) {
        boolean thrown=false;
        try{r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,why);
    }
    private static int original(HudThemeTokens t,int role) {
        if(role==HudPreviewArt.PANEL)return t.panel;
        if(role==HudPreviewArt.PRIMARY)return t.accent;
        if(role==HudPreviewArt.SECONDARY)return t.secondary;
        if(role==HudPreviewArt.TRACK)return t.track;
        return t.muted;
    }
    public static void main(String[] args) {
        HudThemeTokens[] skins={
            HudThemeTokens.of("slate"),HudThemeTokens.of("minimal"),
            HudThemeTokens.of("classic")};
        for(HudThemeTokens skin:skins) {
            for(int role=HudPreviewArt.PANEL;role<=HudPreviewArt.TRACK;role++) {
                int exact=HudSlatePreviewEmphasis.color(skin,role,true);
                ok(exact==original(skin,role),
                    "enabled miniature retains exact original HUD skin tokens");
                int muted=HudSlatePreviewEmphasis.color(skin,role,false);
                ok(role==HudPreviewArt.PANEL || muted!=skin.accent,
                    "inactive widget never claims accent-active ink");
                if(role!=HudPreviewArt.PANEL)
                    ok((muted>>>24)<255,
                        "inactive details use a subdued translucent tone");
            }
        }
        invalid(()->HudSlatePreviewEmphasis.color(null,0,true),
            "no undefined palette");
        invalid(()->HudSlatePreviewEmphasis.color(skins[0],-1,true),
            "invalid negative role");
        invalid(()->HudSlatePreviewEmphasis.color(skins[0],5,false),
            "invalid extra role");
        invalid(()->HudSlatePreviewEmphasis.visible(null,true,true,true),
            "no unknown widget");
        HudEditorControls.Item[] all=HudEditorControls.Item.values();
        Random rng=new Random(111202601L);
        for(int i=0;i<50000;i++) {
            HudEditorControls.Item item=all[i%all.length];
            boolean enabled=rng.nextBoolean();
            boolean metrics=rng.nextBoolean();
            boolean rows=rng.nextBoolean();
            boolean shown=HudSlatePreviewEmphasis.visible(
                item,enabled,metrics,rows);
            boolean expected=item==HudEditorControls.Item.STATUS
                ?enabled||metrics:
                item==HudEditorControls.Item.MODULE_LIST
                    ?enabled&&rows:enabled;
            ok(shown==expected,"truthful enabled-only visual visibility");
            if(item==HudEditorControls.Item.STATUS&&!enabled&&metrics)
                ok(shown,"metrics-only Status remains visually active");
            if(item==HudEditorControls.Item.MODULE_LIST&&enabled&&!rows)
                ok(!shown,"enabled but empty list shows placeholder not active");
            HudThemeTokens skin=skins[i%skins.length];
            for(int role=HudPreviewArt.PANEL;role<=HudPreviewArt.TRACK;role++) {
                int color=HudSlatePreviewEmphasis.color(skin,role,shown);
                if(shown)ok(color==original(skin,role),
                    "real visible widget keeps skin emphasis");
                else {
                    ok(role==HudPreviewArt.PANEL||color!=skin.accent,
                        "disabled miniatures have no false active accents");
                    ok(role==HudPreviewArt.PANEL||(color>>>24)<255,
                        "disabled details are perceptibly subdued");
                }
            }
        }
        System.out.println("HUD_SLATE_PREVIEW_EMPHASIS_PASS checks="+checks);
    }
}
