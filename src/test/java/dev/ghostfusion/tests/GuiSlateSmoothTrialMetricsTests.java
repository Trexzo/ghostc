package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateSmoothTrialMetrics;
import java.util.Random;

public final class GuiSlateSmoothTrialMetricsTests {
    private static long checks;
    private static void ok(boolean value,String message) {
        checks++;if(!value)throw new AssertionError(message);
    }
    private static void rejects(Runnable task,String message) {
        boolean rejected=false;
        try{task.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,message);
    }
    public static void main(String[] args) {
        GuiSlateSmoothTrialMetrics metric=new GuiSlateSmoothTrialMetrics();
        ok(metric.report().contains("attempts=0 painted=0 fallback=0"),
            "empty session reports zero events");
        Random r=new Random(30102026L);
        long painted=0;
        int maxTextures=0,maxLayouts=0;
        for(int i=0;i<50000;i++) {
            boolean success=r.nextBoolean();
            int textures=r.nextInt(97),layouts=r.nextInt(193);
            painted+=success?1:0;
            maxTextures=Math.max(maxTextures,textures);
            maxLayouts=Math.max(maxLayouts,layouts);
            metric.draw(success,r.nextInt(100000),textures,layouts);
            if((i%3)==0)metric.hit();
            if((i%4)==0)metric.raster();
            if((i%5)==0)metric.upload();
            if((i%7)==0)metric.evict();
            if((i%401)==0)metric.error();
        }
        String report=metric.report();
        ok(report.contains("attempts=50000"),"complete draw count");
        ok(report.contains("painted="+painted),"success counter");
        ok(report.contains("fallback="+(50000-painted)),"failed draw counter");
        ok(report.contains("peakTextures="+maxTextures),"bounded texture high water");
        ok(report.contains("peakLayouts="+maxLayouts),"bounded CPU high water");
        ok(report.contains("fitHits=16667"),"cache-hit counter exact");
        ok(report.contains("uploads=10000"),"upload counter exact");
        ok(!report.contains("RSHIFT")&&!report.contains("Player")&&
           !report.contains("Auto Clicker"),"summary contains no user text");
        rejects(()->metric.draw(false,-1,0,0),"negative duration");
        rejects(()->metric.draw(false,0,97,0),"GPU cache overflow");
        rejects(()->metric.draw(false,0,0,193),"CPU cache overflow");
        System.out.println("GUI_SLATE_SMOOTH_TRIAL_METRICS_PASS checks="+checks);
    }
}
