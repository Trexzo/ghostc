package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.FramePerformance;
import dev.ghostfusion.ui.HudWarningPlacement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** R2.10: tick-time focus fences and independent warning viewport geometry. */
public final class LifecycleVisibilityTests {
    private static int checks;
    private static void ok(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    private static void rejected(Runnable r,String why) {
        boolean failed=false;
        try{r.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
    private static void focusWithoutOverlayCallback() throws Exception {
        Path root=Files.createTempDirectory("ghostc-focus-r210");
        HostRuntime h=new HostRuntime(root);
        h.worldPresence(true);
        h.uiActions().toggle("hud.diagnostics");
        h.performanceFrame(1000000000L,1000000,true);
        h.performanceFrame(1020000000L,2000000,true);
        ok(h.performanceSnapshot().frames==1 && h.performanceSnapshot().medianFps==50,
            "focused 50 FPS observed");
        // These calls represent END client ticks while the 2D overlay callback
        // never runs, exactly as a menu or chat screen may behave in Forge.
        h.inputFocus(true);
        for(int i=0;i<200;i++)h.inputFocus(true);
        h.performanceFrame(101020000000L,2000000,true);
        ok(h.performanceSnapshot().frames==1,
            "blocked focus rejects stray overlay callback, not a fake FPS sample");
        h.inputFocus(false);
        h.performanceFrame(101040000000L,3000000,true);
        ok(h.performanceSnapshot().frames==1,
            "first resumed frame establishes reference, not stale GUI interval");
        h.performanceFrame(101060000000L,3000000,true);
        ok(h.performanceSnapshot().frames==2 && h.performanceSnapshot().minFps==50,
            "normal cadence resumes after GUI without outlier");
        h.inputFocus(true);
        h.worldPresence(false);
        h.worldPresence(true);
        h.inputFocus(false);
        h.performanceFrame(201000000000L,3000000,true);
        ok(h.performanceSnapshot().frames==2,"new world first frame establishes reference");
        h.performanceFrame(201010000000L,4000000,true);
        ok(h.performanceSnapshot().frames==3 &&
            h.performanceSnapshot().maxFps==100,"new world cadence sampled");
        h.close();
    }
    private static void smallViewport() {
        rejected(()->HudWarningPlacement.y(1,0,9),"zero viewport refused");
        rejected(()->HudWarningPlacement.y(1,10,0),"zero font refused");
        ok(HudWarningPlacement.y(2,180,9)==2,"HUD absent uses top fallback");
        ok(HudWarningPlacement.y(90,120,9)==90,"status HUD bottom preserved");
        ok(HudWarningPlacement.y(300,120,9)==110,"warning below viewport clamped");
        ok(HudWarningPlacement.y(-200,120,9)==0,"negative baseline clamped");
        ok(HudWarningPlacement.y(100,4,9)==0,"font larger than window handled");
        Random random=new Random(2102026L);
        for(int i=0;i<13000;i++) {
            int height=1+random.nextInt(1200);
            int font=1+random.nextInt(40);
            int want=random.nextInt(3200)-900;
            int y=HudWarningPlacement.y(want,height,font);
            ok(y>=0&&y<height,"warning anchor never entirely below viewport "+i);
            if(font<height)ok(y+font<height,"warning fits with bottom margin "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        focusWithoutOverlayCallback();smallViewport();
        ok(checks>20000,"comprehensive warning position replay");
        System.out.println("LIFECYCLE_VISIBILITY_PASS assertions="+checks);
    }
}
