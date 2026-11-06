package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.TargetHudSlateMiniatureGeometry;
import java.util.Random;

/** Setting-driven geometry without simulated target data or hitbox edits. */
public final class TargetHudSlateMiniatureGeometryTests {
    private static long checks;
    private static void ok(boolean yes,String message) {
        checks++;if(!yes)throw new AssertionError(message);
    }
    private static void invalid(Runnable task,String message) {
        boolean thrown=false;
        try{task.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,message);
    }
    private static final class Inspect implements HudPreviewArt.Sink {
        HudPreviewGeometry.Rect expected;
        int count,panel,primary,secondary,muted,track;
        Inspect(HudPreviewGeometry.Rect expected){this.expected=expected;}
        public void box(int x,int y,int w,int h,int role) {
            ok(w>=1&&h>=1,"nonempty painted rectangle");
            ok(x>=expected.x&&y>=expected.y&&
               (long)x+w<=(long)expected.x+expected.width&&
               (long)y+h<=(long)expected.y+expected.height,
               "miniature never leaves actual widget thumbnail");
            count++;
            switch(role) {
                case HudPreviewArt.PANEL:panel++;break;
                case HudPreviewArt.PRIMARY:primary++;break;
                case HudPreviewArt.SECONDARY:secondary++;break;
                case HudPreviewArt.MUTED:muted++;break;
                case HudPreviewArt.TRACK:track++;break;
                default:throw new AssertionError("unknown paint role");
            }
        }
    }
    public static void main(String[] args) {
        Random rng=new Random(31102026L);
        for(int i=0;i<50000;i++) {
            int x=rng.nextInt(1200)-300,y=rng.nextInt(1200)-300;
            int width=1+rng.nextInt(280),height=1+rng.nextInt(170);
            HudPreviewGeometry.Rect rect=new HudPreviewGeometry(
                x,y,width,height,160,43).canvas;
            boolean health=(i&1)!=0,distance=(i&2)!=0;
            Inspect sink=new Inspect(rect);
            TargetHudSlateMiniatureGeometry.paint(rect,health,distance,sink);
            ok(sink.panel==1&&sink.primary==1&&sink.secondary==2,
                "target miniature always has graphite and identity hierarchy");
            ok(sink.track==(health?1:0),
                "track follows actual health-display configuration");
            ok(sink.muted==(health?2:0)+(distance?2:0),
                "health metadata and distance track real toggles");
            ok(sink.count==4+(health?3:0)+(distance?2:0),
                "no fictional HP fill or extra widgets");
        }
        HudPreviewGeometry.Rect sample=new HudPreviewGeometry(
            7,9,160,43,160,43).canvas;
        Inspect absent=new Inspect(sample);
        TargetHudSlateMiniatureGeometry.paint(sample,false,false,absent);
        ok(absent.track==0&&absent.muted==0,
            "health-off and distance-off render no false readouts");
        invalid(()->TargetHudSlateMiniatureGeometry.paint(null,true,true,absent),
            "null viewport rejected");
        invalid(()->TargetHudSlateMiniatureGeometry.paint(sample,true,true,null),
            "null painter rejected");
        System.out.println("TARGET_HUD_SLATE_MINIATURE_PASS checks="+checks);
    }
}
