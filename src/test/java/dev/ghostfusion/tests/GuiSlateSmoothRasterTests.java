package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateSmoothRaster;
import java.util.Random;

/** Headless-safe AWT glyph raster and exact output budgets. */
public final class GuiSlateSmoothRasterTests {
    private static long checks;
    private static void ok(boolean valid,String why) {
        checks++;if(!valid)throw new AssertionError(why);
    }
    private static void reject(Runnable action,String why) {
        boolean seen=false;
        try{action.run();}catch(IllegalArgumentException expected){seen=true;}
        ok(seen,why);
    }
    public static void main(String[] args) {
        String[] samples={"GHOSTC","Auto Clicker","Aim Assist","RSHIFT",
            "Combat / RSHIFT","W W W","III","A😀B","§aTest",""};
        Random random=new Random(20261029L);
        for(int i=0;i<25000;i++) {
            String raw=samples[random.nextInt(samples.length)];
            int size=7+random.nextInt(16),budget=random.nextInt(513);
            boolean bold=random.nextBoolean();
            String text=GuiSlateSmoothRaster.fit(raw,size,bold,budget);
            ok(text.codePointCount(0,text.length())<=64,
                "bounded number of glyphs");
            if(!text.isEmpty() && budget>=4 && i%45==0) {
                GuiSlateSmoothRaster.Run run=GuiSlateSmoothRaster.raster(
                    text,size,bold);
                ok(run.width<=budget,"actual antialiased bitmap fits pixel budget");
                ok(run.width*run.height==run.pixels.length,
                    "packed RGBA exact footprint");
                ok(run.height<=48&&run.width<=GuiSlateSmoothRaster.MAX_WIDTH,
                    "bounded glyph upload area");
                int nonzero=0;
                for(int pixel:run.pixels)if((pixel>>>24)!=0)nonzero++;
                ok(nonzero>0,"real rasterized ink pixels");
            }
        }
        GuiSlateSmoothRaster.Run wide=GuiSlateSmoothRaster.raster(
            "GHOSTC",11,true);
        ok(wide.inkWidth>0&&wide.width>wide.inkWidth,
            "glyph side padding is explicit");
        reject(()->GuiSlateSmoothRaster.fit("Name",6,false,100),
            "too small font rejected");
        reject(()->GuiSlateSmoothRaster.fit("Name",23,false,100),
            "large font rejected");
        reject(()->GuiSlateSmoothRaster.fit("Name",11,false,513),
            "excessively wide GPU budget rejected");
        reject(()->GuiSlateSmoothRaster.raster("",11,true),
            "empty glyph rejected");
        System.out.println("GUI_SLATE_SMOOTH_RASTER_PASS checks="+checks);
    }
}
