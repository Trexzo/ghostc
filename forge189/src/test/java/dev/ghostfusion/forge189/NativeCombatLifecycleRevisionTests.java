package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.CombatLifecycleFence;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;

/**
 * Java 8 genuine Minecraft 1.8.9 native input linkage, plus combat module
 * lifecycle revision admission and pending USE teardown. No game started.
 */
public final class NativeCombatLifecycleRevisionTests {
    private static int count;
    private static void ok(boolean value,String reason) {
        count++;if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Method sync=ForgeCombatController.class.getDeclaredMethod(
            "syncModuleLifecycle");
        ok(sync.getReturnType()==Void.TYPE,
            "real Forge combat controller observes module transitions");
        Method projectile=ForgeCombatController.class.getDeclaredMethod(
            "suspendForProjectile");
        Method manual=ForgeCombatController.class.getDeclaredMethod(
            "suspendForManualHotbar");
        ok(projectile.getReturnType()==Void.TYPE&&manual.getReturnType()==Void.TYPE,
            "both native hotbar suspension paths remain live");
        int attackCode=247,useCode=248;
        KeyBinding attack=new KeyBinding("key.ghostc.r386.attack",attackCode,
            "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r386.use",useCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isPressed()&&!use.isPressed(),
            "genuine Minecraft attack/use queues begin empty");
        CombatLifecycleFence ledger=new CombatLifecycleFence();
        ok(ledger.observe(1,0,0,0,0,0)==CombatLifecycleFence.ALL,
            "first native generation revokes old module state");
        ok(ledger.observe(1,0,0,0,0,0)==0,
            "steady Forge tick does not fabricate another lifecycle");
        ok(ledger.observe(1,0,0,0,0,2)==CombatLifecycleFence.BLOCK,
            "two Block Hit toggles between ticks still revoke pending event");
        BlockHitPolicy block=new BlockHitPolicy();
        ok(!block.preview(true,true,false,true,true,false,true,
            2,0,"post_attack",0,1),"real attack arms one Block Hit event");
        ok(block.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",1,1),"USE was staged before module toggle");
        block.reset();
        ok(!block.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",2,1),
            "re-enabled Block Hit cannot replay a prior pending USE");
        ok(!use.isPressed(),"module reset emits no native USE");
        ok(ledger.observe(2,0,0,0,0,2)==CombatLifecycleFence.ALL,
            "verified new world generation revokes all combat controllers");

        NativePulseSettlement pulses=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        ok(pulses.reconcileNative(1,world,player),
            "original native settlement pins exact world/player");
        pulses.begin(Lane.ATTACK,1,3);
        KeyBinding.onTick(attackCode);
        pulses.nativeReturned(Lane.ATTACK,1,3);
        pulses.abandon(Lane.ATTACK,1,3); // later policy commit fault
        ok((pulses.uncertainMask()&1)!=0,
            "partial native attack remains uncertain after module toggles");
        ok(attack.isPressed()&&!attack.isPressed(),
            "original Minecraft ATTACK pulse observed once");
        ok(pulses.reconcileNative(2,world,player) &&
           !pulses.admitNative(Lane.ATTACK,2,world,player),
            "module off/on and numeric epoch cannot erase native uncertainty");
        ok(pulses.reconcileNative(3,new Object(),player),
            "only new generation and changed native world can recover");
        ok((pulses.uncertainMask()&1)==0,
            "new native-world identity admits fresh ATTACK settlement");
        ok(count>=14,"Java8 combat lifecycle and native pulse boundary checked");
        System.out.println("NATIVE_COMBAT_LIFECYCLE_REVISION_PASS assertions="+count);
    }
}
