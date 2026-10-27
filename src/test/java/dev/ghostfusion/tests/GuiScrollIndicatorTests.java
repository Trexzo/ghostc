package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiScrollIndicator;
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
            if(total>visible&&visible>0&&track>0) {
                int end=GuiScrollIndicator.offset(track,total,visible,total-visible);
                ok(end+thumb==track,"max scroll reaches bottom "+i);
            }
        }
        System.out.println("GUI_SCROLL_INDICATOR_PASS checks="+checks);
    }
}
