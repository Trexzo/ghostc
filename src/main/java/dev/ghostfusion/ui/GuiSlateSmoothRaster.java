package dev.ghostfusion.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.ToIntFunction;

/**
 * Original first-party Slate smooth-text rasterizer, with no bundled font.
 * Uses the JRE's logical Dialog family. Exact glyph metrics and pixels are
 * produced together; OpenGL upload is intentionally a separate concern.
 *
 * Bounded by 64 visible Unicode scalars and <=512 output pixels. This class
 * has no Minecraft, LWJGL, profile, native input or server dependencies.
 */
public final class GuiSlateSmoothRaster {
    public static final int MAX_WIDTH=512;
    public static final int MAX_SIZE=22;
    public static final int PADDING=1;
    private GuiSlateSmoothRaster() { }

    public static final class Run {
        public final String text;
        public final int width,height,inkWidth;
        public final int[] pixels;
        private Run(String text,int w,int h,int inkWidth,int[] pixels) {
            this.text=text;this.width=w;this.height=h;
            this.inkWidth=inkWidth;this.pixels=pixels;
        }
    }

    private static void require(int size,int budget) {
        if(size<7||size>MAX_SIZE||budget<0||budget>MAX_WIDTH)
            throw new IllegalArgumentException("Slate smooth font size/width");
    }

    private static Graphics2D prepare(BufferedImage image,Font face) {
        Graphics2D g=image.createGraphics();
        g.setFont(face);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        return g;
    }

    /** Return sanitized and font-fitted text without allocating a GPU object. */
    public static String fit(String raw,int size,boolean bold,int budget) {
        require(size,budget);
        if(budget<=2*PADDING)return "";
        Font face=new Font(Font.DIALOG,bold?Font.BOLD:Font.PLAIN,size);
        BufferedImage probe=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=prepare(probe,face);
        try {
            final FontMetrics fm=g.getFontMetrics(face);
            return HudTextFit.fit(raw,budget-2*PADDING,
                new ToIntFunction<String>() {
                    public int applyAsInt(String value) {
                        return fm.stringWidth(value);
                    }
                });
        } finally {
            g.dispose();
        }
    }

    /** Antialiased white glyphs; renderer applies user-selected tint/opacity. */
    public static Run raster(String cleaned,int size,boolean bold) {
        require(size,MAX_WIDTH);
        if(cleaned==null||cleaned.isEmpty()||
           cleaned.codePointCount(0,cleaned.length())>64)
            throw new IllegalArgumentException("Slate smooth glyph run");
        Font face=new Font(Font.DIALOG,bold?Font.BOLD:Font.PLAIN,size);
        BufferedImage probe=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D metrics=prepare(probe,face);
        int advance,height,baseline;
        try {
            FontMetrics fm=metrics.getFontMetrics(face);
            advance=fm.stringWidth(cleaned);
            height=fm.getHeight()+2*PADDING;
            baseline=fm.getAscent()+PADDING;
        } finally {
            metrics.dispose();
        }
        int width=advance+2*PADDING;
        if(width<1||width>MAX_WIDTH||height<1||height>48)
            throw new IllegalArgumentException("Slate smooth glyph bounds");
        BufferedImage image=new BufferedImage(width,height,
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=prepare(image,face);
        try {
            g.setColor(java.awt.Color.WHITE);
            g.drawString(cleaned,PADDING,baseline);
        } finally {
            g.dispose();
        }
        // FontMetrics.getHeight() reserves ascent, descent and line gap,
        // not the visible glyph bounding box. Keeping those blank scanlines
        // made 8/10px text fail compact 11/12px height budgets and silently
        // fall back to Minecraft's bitmap font. Trim only transparent rows;
        // retain full measured horizontal advance and one AA guard row.
        int[] full=image.getRGB(0,0,width,height,null,0,width);
        int minInk=height,maxInk=-1;
        for(int row=0;row<height;row++) {
            for(int col=0;col<width;col++) {
                if((full[row*width+col]>>>24)!=0) {
                    if(row<minInk)minInk=row;
                    if(row>maxInk)maxInk=row;
                }
            }
        }
        if(maxInk<0)
            return new Run(cleaned,width,1,advance,new int[width]);
        int first=Math.max(0,minInk-1);
        int last=Math.min(height,maxInk+2);
        int croppedHeight=last-first;
        int[] pixels=new int[width*croppedHeight];
        System.arraycopy(full,first*width,pixels,0,pixels.length);
        return new Run(cleaned,width,croppedHeight,advance,pixels);
    }
}
