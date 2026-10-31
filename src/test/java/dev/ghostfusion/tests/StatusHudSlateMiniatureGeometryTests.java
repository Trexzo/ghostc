package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.ui.StatusHudSlateMiniatureGeometry;
import dev.ghostfusion.ui.UiState;
import java.util.Random;

/** 50k row-presence/ordering and bounds checks at varied editor scales. */
public final class StatusHudSlateMiniatureGeometryTests {
    private static long checks;
    private static void ok(boolean b,String note) {
        checks++;if(!b)throw new AssertionError(note);
    }
    private static void invalid(Runnable runnable,String note) {
        boolean caught=false;
        try{runnable.run();}catch(IllegalArgumentException expected){caught=true;}
        ok(caught,note);
    }
    private static final class Sink implements HudPreviewArt.Sink {
        final HudPreviewGeometry.Rect parent;
        int panel,primary,secondary,muted;
        Sink(HudPreviewGeometry.Rect bounds){this.parent=bounds;}
        public void box(int x,int y,int w,int h,int role) {
            ok(w>0&&h>0,"only positive painted rectangles");
            ok(x>=parent.x&&y>=parent.y&&
               (long)x+w<=(long)parent.x+parent.width&&
               (long)y+h<=(long)parent.y+parent.height,
                "all marks stay in existing Status editor bounds");
            switch(role) {
                case HudPreviewArt.PANEL:panel++;break;
                case HudPreviewArt.PRIMARY:primary++;break;
                case HudPreviewArt.SECONDARY:secondary++;break;
                case HudPreviewArt.MUTED:muted++;break;
                default:throw new AssertionError("unexpected metric visual role");
            }
        }
    }
    public static void main(String[] args) {
        Random rng=new Random(31102026L);
        UiState ui=new UiState();
        for(int i=0;i<50000;i++) {
            boolean status=(i&1)!=0,fps=(i&2)!=0,
                coordinates=(i&4)!=0,ping=(i&8)!=0;
            boolean selectablePlaceholder=!status&&!fps&&!coordinates&&!ping;
            int font=1+rng.nextInt(18);
            int statusWidth=1+rng.nextInt(200),statusHeight=1+rng.nextInt(60);
            StatusHudLayout layout=StatusHudLayout.calculate(ui,
                30+rng.nextInt(1500),20+rng.nextInt(1100),
                status||selectablePlaceholder,fps,coordinates,ping,
                statusWidth,statusHeight,1+rng.nextInt(90),
                1+rng.nextInt(270),1+rng.nextInt(120),font);
            int x=rng.nextInt(2500)-300,y=rng.nextInt(1500)-200;
            int w=1+rng.nextInt(240),h=1+rng.nextInt(120);
            HudPreviewGeometry.Rect bounds=new HudPreviewGeometry(
                x,y,w,h,layout.measuredWidth,layout.measuredHeight).canvas;
            Sink sink=new Sink(bounds);
            StatusHudSlateMiniatureGeometry.paint(bounds,layout,status,sink);
            ok(sink.panel==1,"Status editor has exactly one base surface");
            ok(sink.primary==(status?1:0),
                "only an enabled status owns active marker");
            ok(sink.secondary==((status?1:0)+(fps?1:0)+(coordinates?1:0)),
                "only enabled Status/FPS/XYZ rows paint normal labels");
            ok(sink.muted==((selectablePlaceholder?1:0)+(ping?1:0)),
                "disabled editor placeholder and active ping are quiet");
            ok((layout.fpsY>=0)==fps &&
               (layout.coordinatesY>=0)==coordinates &&
               (layout.pingY>=0)==ping,"source row admission is unchanged");
        }
        StatusHudLayout sample=StatusHudLayout.calculate(ui,300,150,
            true,true,true,true,75,15,44,128,35,9);
        HudPreviewGeometry.Rect frame=new HudPreviewGeometry(
            0,0,160,80,300,150).canvas;
        invalid(()->StatusHudSlateMiniatureGeometry.paint(null,sample,true,
            new Sink(frame)),"null frame rejected");
        invalid(()->StatusHudSlateMiniatureGeometry.paint(frame,null,true,
            new Sink(frame)),"null layout rejected");
        invalid(()->StatusHudSlateMiniatureGeometry.paint(frame,sample,true,
            null),"null painter rejected");
        System.out.println("STATUS_HUD_SLATE_MINIATURE_PASS checks="+checks);
    }
}
