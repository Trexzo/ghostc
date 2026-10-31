package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import java.lang.reflect.Field;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MouseHelper;

/**
 * Original Minecraft 1.8.9 input API linkage and read-only handoff probe.
 * Headless Java8 only; no launched game, actual mouse or server event.
 */
public final class NativeProjectileCombatHandoffTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field dx=MouseHelper.class.getField("deltaX");
        Field dy=MouseHelper.class.getField("deltaY");
        ok(dx.getType()==Integer.TYPE&&dy.getType()==Integer.TYPE,
            "genuine vanilla mouse helper motion fields");
        KeyBinding attack=new KeyBinding("key.ghostc.r369.attack",227,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown(),"vanilla key initially released");
        ok(ProjectileCombatHandoff.reserved(ProjectileOwnerArbiter.Owner.ROD,false,false),
            "native rod cancellation frame remains exclusive");
        ok(ProjectileCombatHandoff.reserved(ProjectileOwnerArbiter.Owner.SNOWBALL,false,false),
            "native snowball cancellation frame remains exclusive");
        AimManualInputPolicy mouse=new AimManualInputPolicy();
        ok(mouse.suppressed(5,0,true,4,0,3,3),
            "native helper semantics start physical pause during select");
        ok(mouse.suppressed(5,1,true,0,0,3,3),
            "manual mouse history survives native projectile use");
        ok(mouse.suppressed(5,2,true,0,0,3,3),
            "manual mouse history survives native restore");
        ok(!mouse.suppressed(5,3,true,0,0,3,3),
            "no extra camera wait beyond configured manual deadline");
        KeyBinding.setKeyBindState(227,true);
        ok(attack.isKeyDown(),"native physical key down observable");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown(),"native key release observable");
        ok(checks>=10,"original 1.8.9 linkage and handoff policy executed");
        System.out.println("NATIVE_PROJECTILE_COMBAT_HANDOFF_PASS assertions="+checks);
    }
}
