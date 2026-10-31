package dev.ghostfusion.tests;

import dev.ghostfusion.ui.CpsSlateMiniatureGeometry;
import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import java.util.Random;

/** 50k multi-scale probes for setting-aware Slate CPS F7 artwork. */
public final class CpsSlateMiniatureGeometryTests {
    private static long assertions;
    private static void ok(boolean b,String msg) {
        assertions++;if(!b)throw new AssertionError(msg);
    }
    private static void invalid(Runnable r,String msg) {
        boolean caught=false;
        try{r.run();}catch(IllegalArgumentException expected){caught=true;}
        ok(caught,msg);
    }
    private static final class Sink implements HudPreviewArt.Sink {
        final HudPreviewGeometry.Rect frame;
        int panel,accent,identity,muted,divider;
        Sink(HudPreviewGeometry.Rect frame){this.frame=frame;}
        public void box(int x,int y,int w,int h,int role) {
            ok(w>0&&h>0,"every painted mark is visible");
            ok(x>=frame.x&&y>=frame.y&&
                (long)x+w<=(long)frame.x+frame.width&&
                (long)y+h<=(long)frame.y+frame.height,
                "miniature rectangles never escape actual editor frame");
            switch(role) {
                case HudPreviewArt.PANEL:panel++;break;
                case HudPreviewArt.PRIMARY:accent++;break;
                case HudPreviewArt.SECONDARY:identity++;break;
                case HudPreviewArt.MUTED:muted++;break;
                case HudPreviewArt.TRACK:divider++;break;
                default:throw new AssertionError("invalid CPS visual role");
            }
        }
    }
    public static void main(String[] args) {
        Random random=new Random(31102026L);
        for(int i=0;i<50000;i++) {
            int x=random.nextInt(2200)-500,y=random.nextInt(1300)-300;
            int w=1+random.nextInt(320),h=1+random.nextInt(200);
            HudPreviewGeometry.Rect frame=new HudPreviewGeometry(
                x,y,w,h,103,17).canvas;
            boolean showRight=(i&1)!=0;
            Sink sink=new Sink(frame);
            CpsSlateMiniatureGeometry.paint(frame,showRight,sink);
            ok(sink.panel==1&&sink.accent==1,
                "one neutral card and one subtle accent");
            ok(sink.identity==(showRight?2:1),
                "right identity present only when right counter enabled");
            ok(sink.divider==(showRight?1:0),
                "no invented second counter divider for left-only mode");
            ok(sink.muted==(showRight?4:3),
                "count placeholder count matches display option");
        }
        HudPreviewGeometry.Rect r=new HudPreviewGeometry(
            2,4,103,17,103,17).canvas;
        Sink sink=new Sink(r);
        invalid(()->CpsSlateMiniatureGeometry.paint(null,false,sink),
            "null frame rejected");
        invalid(()->CpsSlateMiniatureGeometry.paint(r,false,null),
            "null output sink rejected");
        System.out.println("CPS_SLATE_MINIATURE_PASS checks="+assertions);
    }
}
