package dev.ghostfusion.forge189;

import dev.ghostfusion.features.NativeAttackFrameGate;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * Headless JDK8 linkage with genuine Minecraft 1.8.9 KeyBinding and World.
 * No client launch, real hardware input, combat packet or server hit.
 */
public final class NativeAttackOutputTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method getCode=KeyBinding.class.getMethod("getKeyCode");
        Method keyDown=KeyBinding.class.getMethod("isKeyDown");
        Method registry=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(getCode.getReturnType()==Integer.TYPE,
           "real Minecraft native attack/use remap accessor");
        ok(keyDown.getReturnType()==Boolean.TYPE,
           "real native physical binding state accessor");
        ok(Entity.class.isAssignableFrom(registry.getReturnType()),
           "real native current-world player registry accessor");
        KeyBinding attack=new KeyBinding("key.ghostc.r370.attack",229,
                                        "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r370.use",230,
                                     "key.categories.misc");
        KeyBinding.unPressAllKeys();
        Object player=new Object(),world=new Object();
        ok(NativeAttackFrameGate.sameFrame(player,player,world,world,
            attack.getKeyCode(),use.getKeyCode(),attack.getKeyCode(),
            use.getKeyCode(),true,false,true),"unmodified current bindings admit frame");
        KeyBinding.setKeyBindState(229,true);
        ok(attack.isKeyDown(),"physical keybind state visible to final gate");
        ok(NativeAttackFrameGate.attackState(false,true,attack.isKeyDown(),
            use.isKeyDown(),false),"sampled and current held state admit Clicker");
        KeyBinding.setKeyBindState(229,false);
        ok(!NativeAttackFrameGate.attackState(false,true,attack.isKeyDown(),
            use.isKeyDown(),false),"native key release cancels stale output");
        KeyBinding.setKeyBindState(230,true);
        ok(!NativeAttackFrameGate.attackState(true,false,attack.isKeyDown(),
            use.isKeyDown(),false),"native USE takes priority over TriggerBot");
        KeyBinding.unPressAllKeys();
        ok(NativeAttackFrameGate.attackState(true,false,attack.isKeyDown(),
            use.isKeyDown(),false),"unheld trigger can proceed to independent target fence");
        ok(checks>=9,"Java8 original Minecraft input/output probe executed");
        System.out.println("NATIVE_ATTACK_OUTPUT_PASS assertions="+checks);
    }
}
