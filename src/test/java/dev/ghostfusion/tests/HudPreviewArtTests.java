package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudPositions;
import java.util.Random;

/** Bounded, deterministic schematics for editor-only previews. */
public final class HudPreviewArtTests {
    private static int checks;
    private static void ok(boolean cond,String msg) {
        checks++;if(!cond)throw new AssertionError(msg);
    }
    private static HudPreviewGeometry.Rect rect(int x,int y,int w,int h) {
        return new HudPreviewGeometry(x,y,w,h,w,h).canvas;
    }
    private static final class Collector implements HudPreviewArt.Sink {
        HudPreviewGeometry.Rect parent;
        int count,panelCount,accentCount;
        Collector(HudPreviewGeometry.Rect bounds){parent=bounds;}
        public void box(int x,int y,int w,int h,int role) {
            ok(w>0&&h>0,"nonempty miniature marks");
            ok((long)x>=parent.x&&(long)y>=parent.y&&
               (long)x+w<=(long)parent.x+parent.width&&
               (long)y+h<=(long)parent.y+parent.height,"strict preview clipping");
            ok(role>=HudPreviewArt.PANEL&&role<=HudPreviewArt.TRACK,
                "known visual role");
            count++;
            if(role==HudPreviewArt.PANEL)panelCount++;
            if(role==HudPreviewArt.PRIMARY)accentCount++;
        }
    }
    public static void main(String[] args) {
        HudPositions.Widget[] kinds=HudPositions.Widget.values();
        HudPreviewGeometry.Rect roomy=rect(5,11,200,80);
        int[] distinct=new int[kinds.length+1];
        for(int i=-1;i<kinds.length;i++){
            HudPositions.Widget widget=i<0?null:kinds[i];
            Collector sink=new Collector(roomy);
            HudPreviewArt.paint(roomy,widget,sink);
            distinct[i+1]=sink.count;
            ok(sink.panelCount==1&&sink.accentCount>=1,
                "each widget has a panel and characteristic detail");
            ok(sink.count>=4&&sink.count<=16,"bounded preview primitives");
        }
        ok(distinct[2]!=distinct[3]||distinct[3]!=distinct[4],
            "different HUD widgets have distinct miniature compositions");
        boolean rejected=false;
        try{HudPreviewArt.paint(null,null,new Collector(roomy));}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"invalid dimensions rejected");
        rejected=false;
        try{HudPreviewArt.paint(roomy,null,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"null paint sink rejected");
        Random random=new Random(20261026L);
        for(int i=0;i<30000;i++){
            int x=random.nextInt(250)-120,y=random.nextInt(250)-120;
            int w=1+random.nextInt(500),h=1+random.nextInt(350);
            HudPreviewGeometry.Rect parent=rect(x,y,w,h);
            Collector sink=new Collector(parent);
            HudPositions.Widget widget=i%5==0?null:kinds[(i-1)%kinds.length];
            HudPreviewArt.paint(parent,widget,sink);
            ok(sink.panelCount==1,"one base panel for all miniatures");
            ok(sink.count<=16,"frame primitive upper bound");
        }
        System.out.println("HUD_PREVIEW_ART_PASS checks="+checks);
    }
}
