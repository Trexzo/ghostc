package dev.ghostfusion.forge189;

import dev.ghostfusion.features.NativeAttackFrameGate;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Headless Java 8 linkage against genuine Minecraft 1.8.9 input, inventory
 * and client entrypoints. Never instantiates a game or generates a packet.
 */
public final class NativeAttackOutputRevalidationTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method nativeSword=ForgeCombatController.class.getDeclaredMethod(
            "nativeSword",EntityPlayerSP.class);
        ok(Modifier.isPrivate(nativeSword.getModifiers()) &&
           Modifier.isStatic(nativeSword.getModifiers()) &&
           nativeSword.getReturnType()==Boolean.TYPE,
            "original Forge combat holds a private live native sword probe");
        Method held=EntityPlayerSP.class.getMethod("getHeldItem");
        ok(held.getReturnType()==ItemStack.class,
            "Minecraft 1.8.9 player current held ItemStack linked");
        ok(ItemStack.class.getMethod("getItem").getReturnType()==Item.class,
            "Minecraft 1.8.9 native item class linked");
        ok(KeyBinding.class.getMethod("onTick",Integer.TYPE)
            .getReturnType()==Void.TYPE,
            "actual Minecraft 1.8.9 native key pulse API linked");

        Object p=new Object(),enemy=new Object(),other=new Object();
        ok(NativeAttackFrameGate.requireExactClickTarget(false,true,true),
            "Clicker without entity-only still requires a live Hit Select target");
        ok(!NativeAttackFrameGate.requireExactClickTarget(false,true,false),
            "opt-out Hit Select target preserves original Clicker semantics");
        ok(!NativeAttackFrameGate.target(enemy,other,enemy,p,false,20),
            "native crosshair target replacement suppresses a queued click");
        ok(!NativeAttackFrameGate.target(enemy,enemy,other,p,false,20),
            "native registry identity replacement suppresses a queued click");
        ok(!NativeAttackFrameGate.triggerSwordAtOutput(true,false),
            "removing sword between native acquisition and output suppresses trigger");
        ok(NativeAttackFrameGate.triggerSwordAtOutput(false,false),
            "no-sword mode remains permitted when explicitly configured");
        ok(!NativeAttackFrameGate.attackState(true,false,true,false,false),
            "manual press between scheduling and output suppresses trigger");
        ok(!NativeAttackFrameGate.attackState(false,true,false,false,false),
            "manual release between scheduling and output suppresses clicker");
        ok(checks>=12,"genuine Minecraft/Forge final-output linkage validated");
        System.out.println("NATIVE_ATTACK_OUTPUT_REVALIDATION_JAVA8_PASS assertions="+checks);
    }
}
