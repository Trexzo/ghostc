package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateSmoothPlacement;
import dev.ghostfusion.ui.GuiSlateSmoothRaster;
import java.util.Random;

/** 50k randomized baseline admission checks, plus real AWT glyphs. */
public final class GuiSlateSmoothPlacementTests {
    private static long count;
    private static void ok(boolean condition,String description) {
        count++;if(!condition)throw new AssertionError(description);
    }
    private static void invalid(Runnable task,String description) {
        boolean caught=false;
        try{task.run();}catch(IllegalArgumentException e){caught=true;}
        ok(caught,description);
    }
    public static void main(String[] args) {
        Random rng=new Random(30102026L);
        for(int i=0;i<50000;i++) {
            int h=1+rng.nextInt(48),baseline=rng.nextInt(49);
            int budget=1+rng.nextInt(48);
            int y=GuiSlateSmoothPlacement.top(h,baseline,budget);
            if(y!=-1) {
                ok(y>=0&&y+h<=budget,"glyph inside admitted row");
                ok(y+baseline==budget-3,"all letters share baseline");
            } else {
                ok(budget-3-baseline<0 ||
                   budget-3-baseline+h>budget,
                   "rejection exactly matches unsafe glyph box");
            }
        }
        int accepted=0;
        String[] samples={"GHOSTC","Auto Clicker","RANGE",
            "gjpqy","III","WWW","Type & Shift"};
        for(int size:new int[]{8,10}) {
            for(int budget:new int[]{11,12,13,14,17}) {
                for(String sample:samples) {
                    GuiSlateSmoothRaster.Run glyph=
                        GuiSlateSmoothRaster.raster(sample,size,size==10);
                    int top=GuiSlateSmoothPlacement.top(glyph.height,
                        glyph.baselineFromTop,budget);
                    if(top>=0) {
                        accepted++;
                        ok(top+glyph.baselineFromTop==budget-3,
                           "AA font preserves common text baseline");
                        ok(top+glyph.height<=budget,
                           "descenders remain in budget");
                    }
                }
            }
        }
        ok(accepted>5,"real font fits multiple original compact rows");
        invalid(()->GuiSlateSmoothPlacement.top(0,1,10),"empty glyph");
        invalid(()->GuiSlateSmoothPlacement.top(5,-1,10),"negative ascent");
        invalid(()->GuiSlateSmoothPlacement.top(5,2,49),"oversized row");
        System.out.println("GUI_SLATE_SMOOTH_PLACEMENT_PASS checks="+count);
    }
}
