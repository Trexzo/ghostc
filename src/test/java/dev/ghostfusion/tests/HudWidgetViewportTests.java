package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.HudWidgetViewport;
import java.util.Random;

/** Zero/minimal screen gates and normal-size normalized/editor parity. */
public final class HudWidgetViewportTests {
    private static int checks;
    private static void ok(boolean condition,String why) {
        checks++;if(!condition)throw new AssertionError(why);
    }
    private static void rejects(Runnable callback,String why) {
        boolean rejected=false;
        try{callback.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,why);
    }
    private static HudWidgetViewport.Frame place(HudPositions p,HudPositions.Widget w,
                            int sw,int sh,boolean jump,boolean sneak,boolean hp) {
        return HudWidgetViewport.place(p,w,sw,sh,jump,sneak,hp);
    }
    private static void known() {
        HudPositions p=new HudPositions();
        rejects(()->place(null,HudPositions.Widget.KEYSTROKES,100,100,true,true,true),
                "null anchors fail");
        rejects(()->place(p,null,100,100,true,true,true),"null widget fails");
        ok(place(p,HudPositions.Widget.KEYSTROKES,82,120,true,true,true)==null,
           "83px keys never leak beyond 82px screen");
        ok(place(p,HudPositions.Widget.KEYSTROKES,83,76,true,true,true)==null,
           "77px six-key footprint needs complete height");
        HudWidgetViewport.Frame keys=place(p,HudPositions.Widget.KEYSTROKES,
            83,77,true,true,true);
        ok(keys!=null&&keys.x==0&&keys.y==0&&keys.width==83&&keys.height==77,
           "exact-size keystrokes card allowed");
        HudWidgetViewport.Frame compactKeys=place(p,HudPositions.Widget.KEYSTROKES,
            83,37,false,false,true);
        ok(compactKeys!=null&&compactKeys.height==37,
           "optional rows affect admission height");
        ok(place(p,HudPositions.Widget.TARGET,159,200,true,true,false)==null,
           "160px target cannot be painted on narrow screen");
        ok(place(p,HudPositions.Widget.TARGET,160,42,true,true,true)==null,
           "43px health target cannot extend beyond bottom");
        HudWidgetViewport.Frame target=place(p,HudPositions.Widget.TARGET,
            160,29,true,true,false);
        ok(target!=null&&target.height==29&&target.contained(160,29),
           "health-hidden target retains compact footprint");
        p.position(HudPositions.Widget.TARGET,.18,.91);
        target=place(p,HudPositions.Widget.TARGET,320,180,true,true,true);
        ok(target.x==p.pixelX(HudPositions.Widget.TARGET,320,160) &&
           target.y==p.pixelY(HudPositions.Widget.TARGET,180,43),
           "saved target anchors produce exactly previous coordinates");
        p.position(HudPositions.Widget.KEYSTROKES,.05,.65);
        keys=place(p,HudPositions.Widget.KEYSTROKES,1920,1080,true,true,true);
        ok(keys.x==p.pixelX(HudPositions.Widget.KEYSTROKES,1920,83) &&
           keys.y==p.pixelY(HudPositions.Widget.KEYSTROKES,1080,77),
           "large-screen Keystrokes placement unchanged");
        ok(place(p,HudPositions.Widget.KEYSTROKES,0,1080,true,true,true)==null &&
           place(p,HudPositions.Widget.TARGET,1080,-1,true,true,true)==null,
           "unusable scaled screens suppress, never draw");
    }
    private static void seeded() {
        Random r=new Random(2322026L);
        HudPositions p=new HudPositions();
        HudPositions.Widget[] widgets={
            HudPositions.Widget.KEYSTROKES,HudPositions.Widget.TARGET};
        for(int i=0;i<50000;i++) {
            int width=r.nextInt(2200),height=r.nextInt(1400);
            boolean jump=r.nextBoolean(),sneak=r.nextBoolean(),hp=r.nextBoolean();
            for(HudPositions.Widget widget:widgets) {
                double fx=r.nextDouble(),fy=r.nextDouble();
                p.position(widget,fx,fy);
                int expectedWidth=HudFootprints.width(widget);
                int expectedHeight=HudFootprints.height(widget,jump,sneak,hp);
                HudWidgetViewport.Frame frame=place(p,widget,width,height,
                    jump,sneak,hp);
                boolean fits=width>=expectedWidth&&height>=expectedHeight;
                ok((frame!=null)==fits,"exact full-widget admission "+i);
                if(frame==null)continue;
                ok(frame.contained(width,height),"full containment "+i);
                ok(frame.width==expectedWidth&&frame.height==expectedHeight,
                   "authoritative editor/renderer footprint "+i);
                ok(frame.x==p.pixelX(widget,width,expectedWidth)&&
                   frame.y==p.pixelY(widget,height,expectedHeight),
                   "existing placement preserved "+i);
            }
        }
    }
    public static void main(String[] args) {
        known();seeded();
        ok(checks>190000,"substantial bounded-widget viewport replay");
        System.out.println("HUD_WIDGET_VIEWPORT_PASS assertions="+checks);
    }
}
