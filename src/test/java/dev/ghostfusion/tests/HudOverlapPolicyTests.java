package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudOverlapPolicy;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.HudPositions;
import java.util.Random;

/** Half-open bounds, symmetric detection and bounded preview-only behavior. */
public final class HudOverlapPolicyTests {
    private static int checks;
    private static void ok(boolean v,String msg) {
        checks++;if(!v)throw new AssertionError(msg);
    }
    private static HudPreviewGeometry.Rect rect(int x,int y,int w,int h) {
        return new HudPreviewGeometry(x,y,w,h,w,h).canvas;
    }
    public static void main(String[] args) {
        HudPreviewGeometry.Rect base=rect(10,10,40,20);
        ok(HudOverlapPolicy.overlaps(base,rect(15,15,40,20)),
            "intersecting cards are reported");
        ok(!HudOverlapPolicy.overlaps(base,rect(50,10,40,20)),
            "right edge contact is not overlap");
        ok(!HudOverlapPolicy.overlaps(base,rect(10,30,40,20)),
            "bottom edge contact is not overlap");
        ok(HudOverlapPolicy.overlaps(base,base),"same card overlaps itself");
        boolean rejected=false;
        try {HudOverlapPolicy.overlaps(base,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"invalid card rejected");
        UiState ui=new UiState();
        double before=ui.widgets().x(HudPositions.Widget.MODULE_LIST);
        HudPreviewGeometry preview=new HudPreviewGeometry(10,20,260,150,1920,1080);
        HudOverlapPolicy.overlaps(preview.status(ui,124,22),
            preview.widget(ui.widgets(),HudPositions.Widget.MODULE_LIST));
        ok(ui.widgets().x(HudPositions.Widget.MODULE_LIST)==before,
            "collision query never mutates UI anchors");
        Random rng=new Random(261026L);
        for(int i=0;i<24000;i++) {
            HudPreviewGeometry.Rect a=rect(rng.nextInt(400)-200,
                rng.nextInt(400)-200,1+rng.nextInt(300),1+rng.nextInt(250));
            HudPreviewGeometry.Rect b=rect(rng.nextInt(400)-200,
                rng.nextInt(400)-200,1+rng.nextInt(300),1+rng.nextInt(250));
            boolean ab=HudOverlapPolicy.overlaps(a,b);
            boolean ba=HudOverlapPolicy.overlaps(b,a);
            ok(ab==ba,"symmetric overlap "+i);
            boolean oracle=Math.max((long)a.x,(long)b.x)<
                    Math.min((long)a.x+a.width,(long)b.x+b.width) &&
                Math.max((long)a.y,(long)b.y)<
                    Math.min((long)a.y+a.height,(long)b.y+b.height);
            ok(ab==oracle,"true half-open intersection "+i);
        }
        System.out.println("HUD_OVERLAP_POLICY_PASS checks="+checks);
    }
}
