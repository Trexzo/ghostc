package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiScrollIndicator;
import dev.ghostfusion.ui.GuiScrollDrag;
import java.util.Random;
public final class GuiScrollIndicatorTests {
    private static int checks;
    private static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args) {
        ok(GuiScrollIndicator.height(300,20,10)==150,"proportional thumb");
        ok(GuiScrollIndicator.offset(300,20,10,0)==0,"scroll start");
        ok(GuiScrollIndicator.offset(300,20,10,10)==150,"scroll end");
        ok(GuiScrollIndicator.height(300,10,10)==0,"no scrollbar without overflow");
        ok(GuiScrollIndicator.height(0,20,10)==0,"zero track safe");
        ok(GuiScrollIndicator.firstAt(300,20,10,0)==0,"inverse track top");
        ok(GuiScrollIndicator.firstAt(300,20,10,150)==10,"inverse track bottom");
        ok(GuiScrollIndicator.firstAt(300,20,10,75)==5,"inverse midpoint");
        ok(GuiScrollIndicator.firstAt(300,20,10,-200)==0,"drag before track clamps");
        ok(GuiScrollIndicator.firstAt(300,20,10,900)==10,"drag past track clamps");
        GuiScrollDrag capture=new GuiScrollDrag();
        int y=110,trackX=700;
        ok(capture.begin(GuiScrollDrag.Lane.MODULES,trackX,y+77,
                         trackX,y,300,20,10,5,1280,720)==5,
           "thumb click preserves first row");
        ok(capture.active()&&capture.lane()==GuiScrollDrag.Lane.MODULES,
           "active module-lane capture");
        ok(capture.sameLayout(1280,720,20,10,300),"capture tied to layout");
        ok(!capture.sameLayout(1024,720,20,10,300),"resize cancels capture");
        ok(capture.move(y+500)==10,"drag to last module row");
        ok(capture.move(y-500)==0,"drag to first module row");
        capture.end();
        ok(!capture.active(),"release revokes capture");
        ok(capture.begin(GuiScrollDrag.Lane.SETTINGS,trackX,y+290,
                         trackX,y,300,20,10,0,1280,720)==10,
           "empty track click jumps thumb down");
        capture.end();
        ok(capture.begin(GuiScrollDrag.Lane.MODULES,trackX+8,y+100,
                         trackX,y,300,20,10,0,1280,720)==-1,
           "outside 6px gutter never captures");
        ok(capture.begin(GuiScrollDrag.Lane.MODULES,trackX,y+100,
                         trackX,y,300,8,10,0,1280,720)==-1,
           "non-scrollable list cannot capture");
        ok(!capture.active(),"misses retain no capture");
        Random rng=new Random(271026L);
        for(int i=0;i<50000;i++){
            int track=rng.nextInt(1000);
            int total=rng.nextInt(600);
            int visible=rng.nextInt(100);
            int first=rng.nextInt(2000);
            int thumb=GuiScrollIndicator.height(track,total,visible);
            int pos=GuiScrollIndicator.offset(track,total,visible,first);
            ok(thumb>=0&&thumb<=track,"track contains thumb "+i);
            ok(pos>=0&&pos+thumb<=track,"offset contains thumb "+i);
            if(thumb==0)ok(pos==0,"hidden thumb has zero offset "+i);
            int inverse=GuiScrollIndicator.firstAt(track,total,visible,pos);
            ok(inverse>=0&&inverse<=Math.max(0,total-visible),
               "inverse row inside count "+i);
            if(thumb>0) {
                int roundTrip=GuiScrollIndicator.offset(track,total,visible,inverse);
                ok(Math.abs(roundTrip-pos)<=1||track-thumb==0,
                   "thumb round-trip remains within a painted pixel "+i);
            }
            if(total>visible&&visible>0&&track>0) {
                int end=GuiScrollIndicator.offset(track,total,visible,total-visible);
                ok(end+thumb==track,"max scroll reaches bottom "+i);
            }
        }
        System.out.println("GUI_SCROLL_INDICATOR_PASS checks="+checks);
    }
}
