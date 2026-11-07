package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSlateContextIdentity;
import java.util.Random;

/** Deterministic simulation of OpenGL context/texture-number reuse. */
public final class GuiSlateContextIdentityTests {
    private static long checks;
    private static void ok(boolean condition,String description) {
        checks++;
        if(!condition)throw new AssertionError(description);
    }
    private static final class EqualButDifferent {
        @Override public boolean equals(Object o) {
            return o instanceof EqualButDifferent;
        }
        @Override public int hashCode() {return 7;}
    }
    public static void main(String[] args) {
        GuiSlateContextIdentity guard=new GuiSlateContextIdentity();
        Object first=new Object(), replacement=new Object();
        ok(!guard.owns(first),"uninitialized owner has no texture rights");
        ok(!guard.observe(first),"first context is initialized without a reset");
        ok(guard.owns(first),"first context owns textures");
        ok(!guard.observe(first),"same context preserves existing textures");
        ok(guard.observe(replacement),"new context discards old numeric IDs");
        ok(!guard.owns(first),"old context no longer owns textures");
        ok(guard.owns(replacement),"replacement context becomes owner");
        guard.forget();
        ok(!guard.owns(replacement),"teardown revokes all former texture rights");
        ok(!guard.observe(replacement),"after teardown any context is initially new");
        EqualButDifferent a=new EqualButDifferent(), b=new EqualButDifferent();
        ok(a.equals(b),"adversarial contexts compare equal by value");
        ok(guard.observe(a),"new reference always detects context switch");
        ok(guard.observe(b),"identity comparison ignores misleading equals");
        ok(!guard.owns(a)&&guard.owns(b),"only exact context identity owns IDs");
        boolean invalid=false;
        try {guard.observe(null);}catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid,"no context cannot be admitted to GPU operations");
        ok(!guard.owns(null),"missing context cannot delete textures");
        Object[] contexts=new Object[24];
        for(int j=0;j<contexts.length;j++)contexts[j]=new Object();
        Random rng=new Random(20261107L);
        guard.forget();
        Object active=null;
        for(int i=0;i<100000;i++) {
            if(i%257==0) {guard.forget();active=null;}
            Object next=contexts[rng.nextInt(contexts.length)];
            boolean shouldReset=active!=null&&active!=next;
            ok(guard.observe(next)==shouldReset,
               "context switch and texture ID invalidation match");
            ok(guard.owns(next),"only current context can delete texture IDs");
            if(active!=null&&active!=next)
                ok(!guard.owns(active),"replaced context cannot delete IDs");
            active=next;
        }
        System.out.println("GUI_SLATE_CONTEXT_IDENTITY_PASS checks="+checks);
    }
}
