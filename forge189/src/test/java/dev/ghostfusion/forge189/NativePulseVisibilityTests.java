package dev.ghostfusion.forge189;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Headless Java8 original Minecraft KeyBinding boundary. */
public final class NativePulseVisibilityTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        ok(Entity.class.isAssignableFrom(World.class.getMethod(
            "getEntityByID",Integer.TYPE).getReturnType()),
            "original Minecraft world registry API available");
        final int attackCode=235;
        KeyBinding key=new KeyBinding("key.ghostc.r374.attack",attackCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        NativePulseSettlement gate=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        ok(gate.reconcileNative(1,world,player),"initial game identity");
        gate.begin(Lane.ATTACK,1,0);
        KeyBinding.onTick(attackCode);
        gate.nativeReturned(Lane.ATTACK,1,0);
        gate.abandon(Lane.ATTACK,1,0);
        ok(key.isPressed() && !key.isPressed(),
            "only one original native key pulse enqueued");
        ok(gate.uncertainMask()==1,"uncertain native attack state observable");
        ok(gate.reconcileNative(2,world,player),"same world observed");
        ok(gate.uncertainMask()==1,"same-world reconnect keeps warning");
        ok(gate.reconcileNative(3,new Object(),player),"new verified native world");
        ok(gate.uncertainMask()==0,"verified recovery clears warning");
        ok(checks>=7,"Java8 native visibility acceptance probe");
        System.out.println("NATIVE_PULSE_VISIBILITY_PROBE_PASS assertions="+checks);
    }
}
