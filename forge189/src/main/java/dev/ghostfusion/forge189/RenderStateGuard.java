package dev.ghostfusion.forge189;

import org.lwjgl.opengl.GL11;

/**
 * Restore projection and modelview stacks, matrix mode and server GL attributes
 * after a HUD/world callback, even when a feature throws. Caller is the Forge
 * render thread with a current OpenGL context.
 */
public final class RenderStateGuard {
    private RenderStateGuard() { }
    public static void draw(Runnable action) {
        drawScoped(action,GL11.GL_ALL_ATTRIB_BITS);
    }
    /**
     * Minecraft FontRenderer caches texture bindings in GlStateManager.
     * Restoring GL_TEXTURE_BIT behind that cache after ClickGUI painting can
     * silently bind a different atlas, turning every glyph into colored tiles.
     * UI uses Minecraft's own drawRect/FontRenderer state handling; only the
     * matrix stack and original matrix mode need independent protection.
     * World/ESP callbacks retain the original full-attribute guard.
     */
    public static void drawGui(Runnable action) {
        drawScoped(action,GL11.GL_TRANSFORM_BIT);
    }
    private static void drawScoped(Runnable action,int attributes) {
        if (action == null) throw new IllegalArgumentException("render callback");
        GL11.glPushAttrib(attributes);
        boolean projectionPushed=false, modelviewPushed=false;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed=true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelviewPushed=true;
            action.run();
        } finally {
            try {
                if (modelviewPushed) {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            } finally {
                try {
                    if (projectionPushed) {
                        GL11.glMatrixMode(GL11.GL_PROJECTION);
                        GL11.glPopMatrix();
                    }
                } finally {
                    // GL_TRANSFORM_BIT restores the caller's original matrix mode.
                    GL11.glPopAttrib();
                }
            }
        }
    }
}
