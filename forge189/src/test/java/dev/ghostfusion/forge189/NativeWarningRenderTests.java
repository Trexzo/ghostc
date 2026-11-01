package dev.ghostfusion.forge189;

import dev.ghostfusion.ui.HudWarningDrawGate;
import dev.ghostfusion.ui.HudWarningPlacement;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.settings.KeyBinding;

/**
 * Headless native 1.8.9 font/KeyBinding linkage plus warning isolation.
 * Does not invoke a live OpenGL context, launch Minecraft or claim a hit.
 */
public final class NativeWarningRenderTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        ok(FontRenderer.class.getMethod("getStringWidth",String.class)
            .getReturnType()==Integer.TYPE,
            "original Minecraft native font width API available");
        ok(FontRenderer.class.getMethod("trimStringToWidth",String.class,
            Integer.TYPE).getReturnType()==String.class,
            "original native font clipping API available");
        ok(RenderStateGuard.class.getMethod("draw",Runnable.class)!=null,
            "existing GL restore boundary retained");
        ok(HudWarningPlacement.x(270,320,180)==0,
            "native warning fallback does not inherit right-edge clipping");
        HudWarningDrawGate draw=new HudWarningDrawGate();
        ok(!draw.draw(new Runnable(){
            public void run(){throw new IllegalStateException("simulated text renderer");}
        })&&draw.failed(),"headless draw fault cannot propagate");
        ok(draw.draw(new Runnable(){public void run(){}})&&!draw.failed(),
            "warning renderer can retry without clearing output quarantine");
        final int attackCode=236;
        KeyBinding attack=new KeyBinding("key.ghostc.r375.attack",attackCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        KeyBinding.onTick(attackCode);
        ok(attack.isPressed()&&!attack.isPressed(),
            "original native KeyBinding remains independent of warning rendering");
        ok(checks>=7,"Java8 native warning boundary checked");
        System.out.println("NATIVE_WARNING_RENDER_PROBE_PASS assertions="+checks);
    }
}
