package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.ui.StatusHudLayout;
import dev.ghostfusion.ui.UiState;
import java.nio.file.Files;
import java.util.Random;

/** R2.2: actual combined Status/FPS/coordinate footprints and fault isolation. */
public final class StatusHudLayoutTests {
    private static int assertions;
    private static void ok(boolean value,String reason) {
        assertions++;
        if(!value)throw new AssertionError(reason);
    }
    private static void rejects(Runnable task,String reason) {
        boolean rejected=false;
        try{task.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,reason);
    }
    private static StatusHudLayout calculate(UiState ui,int w,int h,
            boolean status,boolean fps,boolean coordinates,
            int statusWidth,int statusHeight,int fpsWidth,int coordWidth,int fontHeight) {
        return StatusHudLayout.calculate(ui,w,h,status,fps,coordinates,
            statusWidth,statusHeight,fpsWidth,coordWidth,fontHeight);
    }
    private static void knownCases() {
        UiState ui=new UiState();
        ui.position(1,1);
        StatusHudLayout both=calculate(ui,320,180,true,true,true,
            60,18,45,150,9);
        ok(both.measuredWidth==152,"coordinate text, not status, determines width");
        ok(both.measuredHeight==40,"exact status plus two text lines");
        ok(both.x==168 && both.y==140,"entire block bottom-right anchored");
        ok(both.statusY==0&&both.fpsY==18&&both.coordinatesY==29,
           "row baselines nonoverlapping");
        ok(both.fitsViewport(320,180),"entire mixed overlay fits");
        StatusHudLayout metricsOnly=calculate(ui,320,180,false,true,true,
            60,18,45,150,9);
        ok(metricsOnly.measuredWidth==152,"hidden status does not set metrics width");
        ok(metricsOnly.measuredHeight==22,"hidden status occupies zero vertical space");
        ok(metricsOnly.x==168&&metricsOnly.y==158,"metrics-only anchored to full footprint");
        ok(metricsOnly.statusY==-1&&metricsOnly.fpsY==0&&
           metricsOnly.coordinatesY==11,"metrics-only row origins");
        StatusHudLayout fpsOnly=calculate(ui,320,180,false,true,false,
            200,40,45,300,9);
        ok(fpsOnly.measuredWidth==47&&fpsOnly.measuredHeight==11,
           "unused text dimensions excluded");
        StatusHudLayout tiny=calculate(ui,5,3,true,true,true,500,32,120,1000,12);
        ok(tiny.x==0 && tiny.y==0&&tiny.width==5&&tiny.height==3,
           "extreme layout constrained to viewport");
        ok(tiny.fitsViewport(5,3),"extreme layout fits by clipping");
        ok(tiny.measuredWidth==1002&&tiny.measuredHeight==60,
           "full footprint still retained for diagnostics");
        rejects(()->calculate(ui,320,180,false,false,false,0,0,0,0,9),
            "empty feature set rejected");
        rejects(()->calculate(ui,0,180,true,false,false,1,1,0,0,9),
            "zero width rejected");
        rejects(()->calculate(ui,320,180,true,false,false,0,1,0,0,9),
            "missing status measurement rejected");
        rejects(()->calculate(ui,320,180,true,false,false,1,-1,0,0,9),
            "negative status height rejected");
        rejects(()->calculate(ui,320,180,false,true,false,0,0,-1,0,9),
            "invalid metrics measurement rejected");
        rejects(()->calculate(null,320,180,true,false,false,1,1,0,0,9),
            "null UI rejected");
    }
    private static void seeded() {
        UiState ui=new UiState();
        Random rng=new Random(2026101922L);
        for(int i=0;i<60000;i++) {
            int sw=1+rng.nextInt(3840),sh=1+rng.nextInt(2160);
            int statusWidth=1+rng.nextInt(1500),statusHeight=1+rng.nextInt(110);
            int fpsWidth=rng.nextInt(900),coordsWidth=rng.nextInt(1600);
            int fontHeight=1+rng.nextInt(26);
            boolean showStatus=rng.nextBoolean(),showFps=rng.nextBoolean(),
                showCoords=rng.nextBoolean();
            if(!showStatus&&!showFps&&!showCoords)showStatus=true;
            double fx=rng.nextDouble(),fy=rng.nextDouble();
            ui.position(fx,fy);
            StatusHudLayout l=calculate(ui,sw,sh,showStatus,showFps,showCoords,
                statusWidth,statusHeight,fpsWidth,coordsWidth,fontHeight);
            long expectedWidth=Math.max(showStatus?statusWidth:0,
                Math.max(showFps?fpsWidth+2L:0,showCoords?coordsWidth+2L:0));
            int expectedHeight=(showStatus?statusHeight:0)+
                (showFps?fontHeight+2:0)+(showCoords?fontHeight+2:0);
            ok(l.measuredWidth==expectedWidth,"full width accounting "+i);
            ok(l.measuredHeight==expectedHeight,"full height accounting "+i);
            ok(l.width==Math.min(sw,expectedWidth)&&
               l.height==Math.min(sh,expectedHeight),"visible clipping "+i);
            ok(l.fitsViewport(sw,sh),"canvas contained "+i);
            ok(l.x==ui.pixelX(sw,l.width)&&l.y==ui.pixelY(sh,l.height),
               "normalized anchor uses entire block "+i);
            ok(l.statusY==(showStatus?0:-1),"status offset "+i);
            ok(l.fpsY==(showFps?(showStatus?statusHeight:0):-1),
               "fps offset "+i);
            ok(l.coordinatesY==(showCoords?
               (showStatus?statusHeight:0)+(showFps?fontHeight+2:0):-1),
               "coords offset "+i);
        }
    }
    private static void isolatedFailures() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-status-r22"));
        h.worldPresence(true);
        ok(h.featureEnabled("hud.status"),"status enabled by default");
        ok(!h.featureEnabled("hud.metrics"),"metrics opt-in");
        h.uiActions().toggle("hud.metrics");
        ok(h.featureEnabled("hud.metrics"),"metrics enabled");
        final int[] drawn={0};
        ok(!h.runFeature("hud.status",()->{
            throw new IllegalStateException("PRIVATE_SENSITIVE_SERVER");
        }),"broken status is quarantined");
        ok(!h.featureEnabled("hud.status"),"broken status disabled");
        ok(h.featureEnabled("hud.metrics"),"unrelated metrics survives");
        ok(h.runFeature("hud.metrics",()->drawn[0]++),"metrics callback can draw");
        ok(drawn[0]==1,"metrics paint callback ran");
        ok(!h.faultWarning().contains("PRIVATE_SENSITIVE_SERVER"),
           "sensitive fault message never displayed");
        ok(!h.runFeature("hud.metrics",()->{
            throw new IllegalArgumentException("OTHER_PRIVATE_DATA");
        }),"broken metrics quarantined independently");
        ok(!h.featureEnabled("hud.metrics"),"metrics disabled");
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==2,
           "exactly two independent feature faults");
        h.uiActions().toggle("hud.status");
        ok(h.runFeature("hud.status",()->drawn[0]++),
           "status re-enables independently");
        ok(drawn[0]==2,"status callback resumes");
        h.close();
    }
    public static void main(String[] args) throws Exception {
        knownCases();seeded();isolatedFailures();
        ok(assertions>400000,"large status/metrics layout and lifecycle regression");
        System.out.println("STATUS_HUD_LAYOUT_PASS assertions="+assertions);
    }
}
