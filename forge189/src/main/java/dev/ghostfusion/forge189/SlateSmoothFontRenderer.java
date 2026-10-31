package dev.ghostfusion.forge189;

import dev.ghostfusion.ui.GuiSlateSmoothRaster;
import dev.ghostfusion.ui.GuiSlateSmoothPlacement;
import dev.ghostfusion.ui.GuiSlateSmoothTrialMetrics;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Experimental original Slate font renderer, never enabled by default.
 * Java2D raster work is separate from bounded GL texture allocation.
 *
 * Render thread only. Every draw restores GL enable/color/texture attributes;
 * the owning GUI releases textures when closed. Any raster/upload failure
 * disables this optional renderer and the caller uses vanilla FontRenderer.
 */
final class SlateSmoothFontRenderer {
    private static final int MAX_TEXTURES=96;
    private static final int MAX_FITTED=192;
    private static final int MAX_DENIED=96;
    private static final int MAX_PIXELS=GuiSlateSmoothRaster.MAX_WIDTH*48;
    // Most module captions are unchanged for hundreds of frames. Avoid
    // constructing AWT FontMetrics/Graphics2D anew on each paint.
    private final LinkedHashMap<FitKey,String> fitted=
        new LinkedHashMap<FitKey,String>(256,0.75f,true);
    private final LinkedHashMap<Key,Texture> textures=
        new LinkedHashMap<Key,Texture>(128,0.75f,true);
    // Cache only the dimensions of non-fitting glyph runs. Without this,
    // small native GUI rows rasterize the same rejected text every frame.
    // A larger row can retry, and closing the GUI clears the bounded cache.
    private final LinkedHashMap<Key,Bounds> denied=
        new LinkedHashMap<Key,Bounds>(128,0.75f,true);
    private final boolean requested=
        Boolean.getBoolean("ghostc.slate.smoothFontExperimental");
    // Aggregate-only local trial output; no glyph strings, player/module
    // names, personal identifiers or gameplay data are ever emitted.
    private final boolean diagnostics=requested &&
        Boolean.getBoolean("ghostc.slate.fontDiagnostics");
    private final GuiSlateSmoothTrialMetrics trial=
        new GuiSlateSmoothTrialMetrics();
    private boolean failed;

    boolean enabled() {return requested&&!failed;}

    boolean draw(String raw,int x,int y,int argb,int size,boolean bold,
                 int pixelBudget,int heightBudget) {
        if(!enabled() || raw==null || raw.isEmpty() || pixelBudget<4 ||
           heightBudget<8)return false;
        final long started=diagnostics?System.nanoTime():0L;
        boolean painted=false;
        try {
            int allowed=Math.min(GuiSlateSmoothRaster.MAX_WIDTH,pixelBudget);
            FitKey request=new FitKey(raw,size,bold,allowed);
            String text=fitted.get(request);
            if(text==null) {
                text=GuiSlateSmoothRaster.fit(raw,size,bold,allowed);
                fitted.put(request,text);
                if(fitted.size()>MAX_FITTED)
                    fitted.remove(fitted.keySet().iterator().next());
            } else if(diagnostics) {
                trial.hit();
            }
            if(text.isEmpty())return false;
            Key key=new Key(text,size,bold);
            Texture texture=textures.get(key);
            if(texture!=null&&!GL11.glIsTexture(texture.id)) {
                // A recreated OpenGL context invalidates *all* prior numeric
                // texture identities. Never delete those stale IDs: a new
                // context may already have reused the same integers.
                textures.clear();
                denied.clear();
                texture=null;
            }
            int top;
            if(texture==null) {
                Bounds refusal=denied.get(key);
                if(refusal!=null &&
                   GuiSlateSmoothPlacement.admittedTop(refusal.width,
                       refusal.height,refusal.baselineFromTop,
                       pixelBudget,heightBudget)<0)return false;
                GuiSlateSmoothRaster.Run raster=
                    GuiSlateSmoothRaster.raster(text,size,bold);
                if(diagnostics)trial.raster();
                if(raster.width*raster.height>MAX_PIXELS)return false;
                // Admit actual glyph geometry *before* allocating a GPU
                // texture. Rejecting after upload wasted GPU work in short
                // Slate rows and repeated costly AWT work every frame.
                top=GuiSlateSmoothPlacement.admittedTop(raster.width,
                    raster.height,raster.baselineFromTop,
                    pixelBudget,heightBudget);
                if(top<0) {
                    denied.put(key,new Bounds(raster.width,raster.height,
                        raster.baselineFromTop));
                    if(denied.size()>MAX_DENIED)
                        denied.remove(denied.keySet().iterator().next());
                    return false;
                }
                texture=upload(raster);
                if(diagnostics)trial.upload();
                textures.put(key,texture);
                denied.remove(key);
                if(textures.size()>MAX_TEXTURES) {
                    Iterator<Map.Entry<Key,Texture>> i=textures.entrySet().iterator();
                    Map.Entry<Key,Texture> eldest=i.next();
                    i.remove();
                    GL11.glDeleteTextures(eldest.getValue().id);
                    if(diagnostics)trial.evict();
                }
            } else {
                top=GuiSlateSmoothPlacement.admittedTop(texture.width,
                    texture.height,texture.baselineFromTop,
                    pixelBudget,heightBudget);
                if(top<0)return false;
            }
            paint(texture,x,y+top,argb);
            painted=true;
            return true;
        } catch(RuntimeException error) {
            failed=true;
            if(diagnostics)trial.error();
            close();
            return false;
        } catch(LinkageError error) {
            failed=true;
            if(diagnostics)trial.error();
            close();
            return false;
        } finally {
            if(diagnostics)trial.draw(painted,
                Math.max(0L,System.nanoTime()-started),
                textures.size(),fitted.size());
        }
    }

    private static Texture upload(GuiSlateSmoothRaster.Run raster) {
        int id=GL11.glGenTextures();
        if(id<=0)throw new IllegalStateException("Slate font texture");
        try {
            ByteBuffer rgba=BufferUtils.createByteBuffer(raster.pixels.length*4);
            for(int pixel:raster.pixels) {
                rgba.put((byte)255).put((byte)255).put((byte)255)
                    .put((byte)(pixel>>>24));
            }
            rgba.flip();
            // Scope texture, pixel-store and binding state during upload.
            GL11.glPushAttrib(GL11.GL_TEXTURE_BIT|GL11.GL_PIXEL_MODE_BIT);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S,33071); // GL_CLAMP_TO_EDGE
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T,33071);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,
                    raster.width,raster.height,0,GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,rgba);
            } finally {
                GL11.glPopAttrib();
            }
            return new Texture(id,raster.width,raster.height,
                raster.baselineFromTop);
        } catch(RuntimeException problem) {
            GL11.glDeleteTextures(id);
            throw problem;
        }
    }

    private static void paint(Texture glyph,int x,int y,int argb) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|
            GL11.GL_TEXTURE_BIT|GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            // Minecraft's default alpha-test threshold would discard the
            // faint outer AA pixels. GL_ENABLE_BIT restores it afterwards.
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,glyph.id);
            // A caller may have left the fixed-function texture environment
            // as REPLACE. Force MODULATE so Slate's text color and alpha
            // multiply with white glyph coverage; GL_TEXTURE_BIT restores it.
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,
                GL11.GL_MODULATE);
            GL11.glColor4f((argb>>>16&255)/255F,(argb>>>8&255)/255F,
                (argb&255)/255F,(argb>>>24&255)/255F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);
            GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+glyph.width,y);
            GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+glyph.width,y+glyph.height);
            GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+glyph.height);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    void reportAndClose() {
        try {
            if(diagnostics)System.out.println(trial.report());
        } finally {
            close();
        }
    }

    void close() {
        // Resource teardown must never prevent the original Minecraft GUI
        // from closing, or prevent fallback after a failed GL operation.
        try {
            for(Texture t:textures.values()) {
                try {
                    GL11.glDeleteTextures(t.id);
                } catch(RuntimeException ignored) {
                    // A destroyed GL context can reject deletion.
                } catch(LinkageError ignored) {
                    // Optional renderer must not break vanilla shutdown.
                }
            }
        } finally {
            textures.clear();
            fitted.clear();
            denied.clear();
        }
    }

    private static final class FitKey {
        final String text;
        final int size,budget;
        final boolean bold;
        FitKey(String text,int size,boolean bold,int budget) {
            this.text=text;this.size=size;this.bold=bold;this.budget=budget;
        }
        @Override public int hashCode() {
            return ((text.hashCode()*31+size)*31+(bold?1:0))*31+budget;
        }
        @Override public boolean equals(Object obj) {
            if(!(obj instanceof FitKey))return false;
            FitKey that=(FitKey)obj;
            return size==that.size&&bold==that.bold&&budget==that.budget&&
                text.equals(that.text);
        }
    }

    private static final class Bounds {
        final int width,height,baselineFromTop;
        Bounds(int width,int height,int baselineFromTop) {
            this.width=width;this.height=height;
            this.baselineFromTop=baselineFromTop;
        }
    }
    private static final class Texture {
        final int id,width,height,baselineFromTop;
        Texture(int id,int width,int height,int baselineFromTop) {
            this.id=id;this.width=width;this.height=height;
            this.baselineFromTop=baselineFromTop;
        }
    }
    private static final class Key {
        final String text;
        final int size;
        final boolean bold;
        Key(String text,int size,boolean bold) {
            this.text=text;this.size=size;this.bold=bold;
        }
        @Override public int hashCode() {
            return (text.hashCode()*31+size)*31+(bold?1:0);
        }
        @Override public boolean equals(Object other) {
            if(!(other instanceof Key))return false;
            Key key=(Key)other;
            return size==key.size&&bold==key.bold&&text.equals(key.text);
        }
    }
}
