package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudSnapPolicy;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** Preview alignment oracle, edge and peer guides, disabled snapping and no mutation. */
public final class HudSnapPolicyTests {
    private static int checks;
    private static void ok(boolean yes,String reason) {
        checks++; if(!yes)throw new AssertionError(reason);
    }
    private static void fails(Runnable r,String reason) {
        boolean failed=false;
        try{r.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,reason);
    }
    private static HudPreviewGeometry.Rect rect(int x,int y,int w,int h) {
        // Rect is immutable and constructed by the trusted preview policy.
        HudPreviewGeometry geom=new HudPreviewGeometry(x,y,w,h,w,h);
        return geom.canvas;
    }
    private static void basics() {
        HudPreviewGeometry.Rect canvas=rect(100,200,320,180);
        HudPreviewGeometry.Rect[] none=new HudPreviewGeometry.Rect[0];
        HudSnapPolicy.Result left=HudSnapPolicy.snap(canvas,rect(104,260,40,20),none,5);
        ok(left.x==100 && left.guideX==100,"left edge magnet");
        HudSnapPolicy.Result right=HudSnapPolicy.snap(canvas,rect(377,260,40,20),none,5);
        ok(right.x==380 && right.guideX==420,"right edge magnet");
        HudSnapPolicy.Result top=HudSnapPolicy.snap(canvas,rect(180,204,40,20),none,5);
        ok(top.y==200 && top.guideY==200,"top edge magnet");
        HudSnapPolicy.Result bottom=HudSnapPolicy.snap(canvas,rect(180,356,40,20),none,5);
        ok(bottom.y==360 && bottom.guideY==380,"bottom edge magnet");
        HudSnapPolicy.Result centered=HudSnapPolicy.snap(canvas,rect(242,282,40,20),none,5);
        ok(centered.x==240 && centered.guideX==260 &&
           centered.y==280 && centered.guideY==290,"canvas center X/Y");
        HudSnapPolicy.Result free=HudSnapPolicy.snap(canvas,rect(192,256,40,20),none,5);
        ok(free.x==192 && free.y==256 &&
           free.guideX==HudSnapPolicy.NO_GUIDE &&
           free.guideY==HudSnapPolicy.NO_GUIDE,"outside tolerance no guide");
        HudPreviewGeometry.Rect neighbor=rect(160,240,60,30);
        HudSnapPolicy.Result align=HudSnapPolicy.snap(canvas,rect(164,274,40,20),
            new HudPreviewGeometry.Rect[]{neighbor},5);
        ok(align.x==160 && align.guideX==160 &&
           align.y==270 && align.guideY==270,"peer left and adjacency align");
        HudSnapPolicy.Result noMove=HudSnapPolicy.snap(canvas,rect(164,279,40,20),
            new HudPreviewGeometry.Rect[]{neighbor},0);
        ok(noMove.x==164 && noMove.y==279,"zero tolerance preserves drag");
        fails(()->HudSnapPolicy.snap(canvas,rect(104,260,40,20),none,-1),
            "negative snap tolerance");
        fails(()->HudSnapPolicy.snap(canvas,rect(104,260,40,20),none,25),
            "excessive tolerance");
        fails(()->HudSnapPolicy.snap(canvas,rect(104,260,40,20),
            new HudPreviewGeometry.Rect[]{null},5),"null peers rejected");
        fails(()->HudSnapPolicy.snap(canvas,rect(500,260,40,20),none,5),
            "offscreen moving widget rejected");
    }
    private static void randomized() {
        Random rng=new Random(261026L);
        for(int i=0;i<26000;i++) {
            int cw=20+rng.nextInt(600),ch=20+rng.nextInt(300);
            HudPreviewGeometry.Rect canvas=rect(-30,40,cw,ch);
            int w=1+rng.nextInt(cw),h=1+rng.nextInt(ch);
            int x=canvas.x+rng.nextInt(cw-w+1),
                y=canvas.y+rng.nextInt(ch-h+1);
            HudPreviewGeometry.Rect moving=rect(x,y,w,h);
            HudPreviewGeometry.Rect peer=rect(canvas.x+rng.nextInt(cw),
                canvas.y+rng.nextInt(ch),1+rng.nextInt(12),1+rng.nextInt(12));
            int tolerance=rng.nextInt(7);
            HudSnapPolicy.Result result=HudSnapPolicy.snap(canvas,moving,
                new HudPreviewGeometry.Rect[]{peer},tolerance);
            ok(result.x>=canvas.x && result.y>=canvas.y &&
               (long)result.x+w<=(long)canvas.x+cw &&
               (long)result.y+h<=(long)canvas.y+ch,"seeded bounded coordinates "+i);
            ok(Math.abs(result.x-x)<=tolerance && Math.abs(result.y-y)<=tolerance,
               "snap never teleports more than threshold "+i);
            if(result.guideX==HudSnapPolicy.NO_GUIDE)ok(result.x==x,"unsnapped X "+i);
            if(result.guideY==HudSnapPolicy.NO_GUIDE)ok(result.y==y,"unsnapped Y "+i);
        }
        UiState ui=new UiState();
        HudPreviewGeometry g=new HudPreviewGeometry(80,50,260,140,1920,1080);
        double orig=ui.hudX();
        HudSnapPolicy.Result result=HudSnapPolicy.snap(g.canvas,g.status(ui,124,22),
            new HudPreviewGeometry.Rect[0],5);
        ok(ui.hudX()==orig,"pure policy never mutates persisted anchors");
        HudPositions.Widget widget=HudPositions.Widget.TARGET;
        double oldWidget=ui.widgets().x(widget);
        HudSnapPolicy.snap(g.canvas,g.widget(ui.widgets(),widget),
            new HudPreviewGeometry.Rect[0],5);
        ok(ui.widgets().x(widget)==oldWidget,"peer anchor unchanged");
    }
    public static void main(String[] args) {
        basics();randomized();
        ok(checks>52000,"substantial snap invariants exercised");
        System.out.println("HUD_SNAP_POLICY_PASS checks="+checks);
    }
}
