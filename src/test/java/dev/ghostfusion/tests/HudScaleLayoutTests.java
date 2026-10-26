package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.HudFootprints;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.HudScaleLayout;
import dev.ghostfusion.ui.HudWidgetViewport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** 1x legacy parity, nonunit scaled bounds, and profile persistence. */
public final class HudScaleLayoutTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void invalid(Runnable r,String why) {
        boolean rejected=false;
        try{r.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,why);
    }
    public static void main(String[] args)throws Exception {
        HudPositions positions=new HudPositions();
        positions.position(HudPositions.Widget.KEYSTROKES,0.23,0.61);
        for(HudPositions.Widget widget:new HudPositions.Widget[]{
                HudPositions.Widget.KEYSTROKES,HudPositions.Widget.CPS,
                HudPositions.Widget.TARGET}) {
            int w=HudFootprints.width(widget);
            int h=HudFootprints.height(widget,true,true,true);
            HudScaleLayout.Frame one=HudScaleLayout.place(positions,widget,1280,720,w,h,1);
            if(widget!=HudPositions.Widget.CPS) {
                HudWidgetViewport.Frame legacy=HudWidgetViewport.place(
                    positions,widget,1280,720,true,true,true);
                ok(one.x==legacy.x&&one.y==legacy.y&&
                   one.width==legacy.width&&one.height==legacy.height,
                   "scale one exactly equals legacy placement "+widget);
            }
            ok(one.width==w&&one.height==h,"one scale exact footprint");
            ok(HudScaleLayout.place(positions,widget,w-1,h,w,h,1)==null,
                "too-narrow view never draws partial widget");
        }
        invalid(()->HudScaleLayout.extent(83,Double.NaN),"NaN scale rejected");
        invalid(()->HudScaleLayout.extent(83,0.5),"scale lower bound rejected");
        invalid(()->HudScaleLayout.extent(83,2.0),"scale upper bound rejected");
        invalid(()->HudScaleLayout.extent(0,1.0),"zero logical width rejected");
        ok(HudScaleLayout.extent(83,1.25)==104,"ceil protects fractional scaled edge");
        ok(HudScaleLayout.logicalViewport(640,1.25)==512,
            "unscaled layout screen follows transformed pixels");
        Random random=new Random(261026L);
        for(int i=0;i<24000;i++) {
            HudPositions.Widget widget=HudPositions.Widget.values()[random.nextInt(4)];
            double scale=0.75+random.nextInt(101)/100.0;
            int vw=1+random.nextInt(2100),vh=1+random.nextInt(1250);
            int w=1+random.nextInt(170),h=1+random.nextInt(100);
            double fx=random.nextDouble(),fy=random.nextDouble();
            positions.position(widget,fx,fy);
            HudScaleLayout.Frame frame=HudScaleLayout.place(
                positions,widget,vw,vh,w,h,scale);
            boolean fits=HudScaleLayout.extent(w,scale)<=vw &&
                         HudScaleLayout.extent(h,scale)<=vh;
            ok((frame!=null)==fits,"full-screen bounded admission "+i);
            if(frame!=null) {
                ok(frame.contained(vw,vh),"scaled content contained "+i);
                ok(frame.x==(int)Math.round(fx*(vw-frame.width))&&
                   frame.y==(int)Math.round(fy*(vh-frame.height)),
                   "saved anchor matches scaled visible footprint "+i);
            }
        }
        Path dir=Files.createTempDirectory("ghostc-hud-scale");
        HostRuntime host=new HostRuntime(dir);
        ok(host.keysScale()==1.0&&host.cpsScale()==1.0&&
           host.targetScale()==1.0&&host.moduleListScale()==1.0,
           "old profiles preserve exact 1x visuals");
        host.uiActions().fraction("hud.keystrokes","scale",0.50);
        host.uiActions().fraction("hud.cps","scale",0.25);
        host.uiActions().fraction("hud.target","scale",0.75);
        host.uiActions().fraction("hud.modulelist","scale",0.75);
        ok(host.keysScale()>1.0&&host.cpsScale()<1.0&&
           host.targetScale()>1.0&&host.moduleListScale()>1.0,
           "independent scale settings are user-adjustable");
        double[] scales={host.keysScale(),host.cpsScale(),
            host.targetScale(),host.moduleListScale()};
        host.close();
        HostRuntime reloaded=new HostRuntime(dir);
        ok(reloaded.keysScale()==scales[0]&&reloaded.cpsScale()==scales[1]&&
           reloaded.targetScale()==scales[2]&&
           reloaded.moduleListScale()==scales[3],
           "all four scales persist across sessions");
        reloaded.close();
        System.out.println("HUD_SCALE_LAYOUT_PASS checks="+checks);
    }
}
