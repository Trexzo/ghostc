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
        // The old renderer used the full metrics line (ascent, descent and
        // leading), so a 10px title occupied 16px and could not render in
        // the native 12/14px Slate rows. Only actual ink plus AA guard rows
        // should count against that native row-height budget.
        for(int size:new int[]{8,10}) {
            for(String caption:new String[]{"GHOSTC","Auto Clicker",
                    "Combat / RSHIFT","RANGE","gjpqy"}) {
                GuiSlateSmoothRaster.Run run=
                    GuiSlateSmoothRaster.raster(caption,size,size==10);
                java.awt.Font font=new java.awt.Font(java.awt.Font.DIALOG,
                    size==10?java.awt.Font.BOLD:java.awt.Font.PLAIN,size);
                java.awt.Graphics2D graphics=new java.awt.image.BufferedImage(
                    1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
                int original=graphics.getFontMetrics(font).getHeight()+2;
                graphics.dispose();
                ok(run.height>=1&&run.height<=original,
                    "visible glyph rectangle never exceeds full metrics line");
                int first=-1,last=-1;
                for(int row=0;row<run.height;row++) {
                    for(int x=0;x<run.width;x++) {
                        if((run.pixels[row*run.width+x]>>>24)!=0) {
                            if(first<0)first=row;
                            last=row;
                        }
                    }
                }
                ok(first>=0 && first<=1,
                    "cropped glyph top has at most one transparent guard row");
                ok(last>=run.height-2 && last<run.height,
                    "cropped glyph bottom has at most one AA guard row");
            }
        }
        GuiSlateSmoothRaster.Run whitespace=GuiSlateSmoothRaster.raster(
            "   ",8,false);
        ok(whitespace.height==1,"whitespace uses minimal transparent row");
        ok(whitespace.pixels.length==whitespace.width,
            "whitespace allocation is width bounded");
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
