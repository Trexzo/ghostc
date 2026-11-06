package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.KeystrokesSlateMiniatureGeometry;
import java.util.Random;

/** Real display-setting combinations and strictly clipped preview marks. */
public final class KeystrokesSlateMiniatureGeometryTests {
    private static long checks;
    private static void ok(boolean valid,String message) {
        checks++;if(!valid)throw new AssertionError(message);
    }
    private static void invalid(Runnable action,String message) {
        boolean rejected=false;
        try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,message);
    }
    private static final class Inspect implements HudPreviewArt.Sink {
        final HudPreviewGeometry.Rect bounds;
        int panel,secondary,track,all;
        Inspect(HudPreviewGeometry.Rect bounds){this.bounds=bounds;}
        public void box(int x,int y,int width,int height,int role) {
            ok(width>0&&height>0,"all painted marks are positive");
            ok(x>=bounds.x&&y>=bounds.y&&
                (long)x+width<=(long)bounds.x+bounds.width&&
                (long)y+height<=(long)bounds.y+bounds.height,
                "scaled key is entirely inside editor's original bounds");
            if(role==HudPreviewArt.PANEL)panel++;
            else if(role==HudPreviewArt.TRACK)track++;
            else if(role==HudPreviewArt.SECONDARY)secondary++;
            else throw new AssertionError("false pressed/accent state");
            all++;
        }
    }
    public static void main(String[] args) {
        Random random=new Random(31102026L);
        for(int i=0;i<50000;i++) {
            boolean jump=(i&1)!=0,sneak=(i&2)!=0;
            int nominal=HudFootprints.height(
                HudPositions.Widget.KEYSTROKES,jump,sneak,true);
            ok(nominal==37+(jump?20:0)+(sneak?20:0),
                "preview height follows native Keystrokes dimensions");
            int x=random.nextInt(1200)-400,y=random.nextInt(1200)-400;
            int w=1+random.nextInt(400),h=1+random.nextInt(290);
            HudPreviewGeometry.Rect rect=new HudPreviewGeometry(
                x,y,w,h,83,nominal).canvas;
            Inspect paint=new Inspect(rect);
            KeystrokesSlateMiniatureGeometry.paint(rect,jump,sneak,paint);
            int tiles=4+(jump?1:0)+(sneak?1:0);
            ok(paint.panel==1&&paint.track==tiles&&
               paint.secondary==tiles&&paint.all==1+2*tiles,
                "WASD and only the enabled optional rows are represented");
        }
        HudPreviewGeometry.Rect normal=new HudPreviewGeometry(
            0,0,83,77,83,77).canvas;
        invalid(()->KeystrokesSlateMiniatureGeometry.paint(null,false,false,
                new Inspect(normal)),"missing frame rejected");
        invalid(()->KeystrokesSlateMiniatureGeometry.paint(normal,false,false,
                null),"missing painter rejected");
        System.out.println("KEYSTROKES_SLATE_MINIATURE_PASS checks="+checks);
    }
}
