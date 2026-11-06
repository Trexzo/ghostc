package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.host.HostRuntime;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Headless Java8 original Forge 1.8.9 linkage; no Minecraft launch/server. */
public final class NativeCombatFrameFaultTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method tick=ForgeEventAdapter.class.getDeclaredMethod(
            "onClientTick",TickEvent.ClientTickEvent.class);
        ok(tick.getReturnType()==Void.TYPE,
            "native Forge END tick adapter remains linked");
        Method status=HostRuntime.class.getMethod("combatDispatchStatus",Boolean.TYPE);
        ok(status.getReturnType()==Void.TYPE,
            "shared frame quarantine reports through actual host");
        Method nativeMask=ForgeCombatController.class.getDeclaredMethod(
            "nativePulseUncertainMask");
        ok(nativeMask.getReturnType()==Integer.TYPE,
            "native pulse settlement remains separately observable");

        final int attackCode=239;
        KeyBinding attack=new KeyBinding("key.ghostc.r388.attack",attackCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        CombatFrameFaultFence shared=new CombatFrameFaultFence();
        NativePulseSettlement pulses=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        ok(shared.observe(1,world,player),"native identity observed");
        ok(pulses.reconcileNative(1,world,player),"native pulse identity pinned");
        pulses.begin(Lane.ATTACK,1,0);
        KeyBinding.onTick(attackCode);
        pulses.nativeReturned(Lane.ATTACK,1,0);
        pulses.abandon(Lane.ATTACK,1,0);
        ok(attack.isPressed()&&!attack.isPressed(),
            "exactly one real Minecraft KeyBinding pulse consumed");
        shared.quarantine();
        ok(shared.quarantined() && pulses.uncertainMask()==1,
            "shared fault cannot erase uncertain native attack");
        ok(!shared.observe(2,world,player) &&
           !pulses.admitNative(Lane.ATTACK,2,world,player),
           "same-world reconnect does not permit replay");
        ok(!shared.observe(3,null,null),
            "world absence does not become recovery evidence");
        Object newWorld=new Object();
        ok(shared.observe(4,newWorld,player),
            "new generation plus native world identity reopens shared controller");
        ok(pulses.reconcileNative(4,newWorld,player) &&
           pulses.uncertainMask()==0,
           "native pulse has its own independent recovery proof");
        ok(!attack.isPressed(),"shared quarantine did not enqueue any extra keys");
        ok(checks>=12,"native Java8 exception-fence linkage exercised");
        System.out.println("NATIVE_COMBAT_FRAME_FAULT_PASS assertions="+checks);
    }
}
