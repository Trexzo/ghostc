package dev.ghostfusion.forge189;

import org.lwjgl.opengl.GL11;

/** Isolate all GUI/HUD OpenGL state, including blending, depth, scissor and matrix. */
public final class RenderStateGuard {
    private RenderStateGuard() { }
    public static void draw(Runnable action) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try { action.run(); }
        finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
