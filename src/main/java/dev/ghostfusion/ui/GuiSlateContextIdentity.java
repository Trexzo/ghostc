package dev.ghostfusion.ui;

/**
 * Tracks OpenGL context ownership by reference identity, not integer texture
 * names. A new context may reuse the exact same numeric GL texture ID for an
 * unrelated image, so glIsTexture(id) alone cannot validate a cached glyph.
 *
 * CPU-only. The caller must query the active context on its render thread.
 */
public final class GuiSlateContextIdentity {
    private Object context;

    /** Returns true only when a previously observed context was replaced. */
    public boolean observe(Object current) {
        if(current==null)throw new IllegalArgumentException("OpenGL context");
        if(context==current)return false;
        boolean replaced=context!=null;
        context=current;
        return replaced;
    }

    /** Whether cached IDs may be touched in the current context. */
    public boolean owns(Object current) {
        return current!=null && context==current;
    }

    /** Forget IDs after their owner releases them or the context is lost. */
    public void forget() {context=null;}
}
