package dev.ghostfusion.demo;

import dev.ghostfusion.core.BoundedIngress;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.InputRouter;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;

/** Headless standalone sanity check. NOT a Minecraft mod or an injector. */
public final class DemoMain {
    public static void main(String[] args) {
        final ModuleRegistry registry = new ModuleRegistry();
        final Setting.Bool hudVisible = new Setting.Bool("visible", true);
        registry.register(new GhostModule() {
            public String id() { return "overlay.demo"; }
            public String displayName() { return "Demo Overlay"; }
            public Category category() { return Category.RENDER; }
            public void onEnable() { System.out.println("overlay enabled"); }
            public void onDisable() { System.out.println("overlay disabled"); }
            public void onEvent(GameEvent e) {
                if (e.kind() == GameEvent.Kind.RENDER_2D && hudVisible.get())
                    System.out.println("render-event #" + e.sequence());
            }
        }, 67, hudVisible);
        InputRouter keys = new InputRouter(registry);
        keys.keyEvent(67, true, false);
        keys.keyEvent(67, false, false);
        BoundedIngress ingress = new BoundedIngress(32);
        ingress.submit(new GameEvent(GameEvent.Kind.RENDER_2D, 1));
        ingress.drain(registry, 32);
        registry.shutdown();
        System.out.println("GhostFusion Core R0.1: DEMO_PASS (no Minecraft attached)");
    }
}
