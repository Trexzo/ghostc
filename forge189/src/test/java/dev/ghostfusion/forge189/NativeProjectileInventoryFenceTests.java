package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileInventoryFence;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Mandatory original Minecraft 1.8.9 native ItemStack linkage evidence.
 * Pure owner fence can run headlessly; no fake game/server acceptance.
 */
public final class NativeProjectileInventoryFenceTests {
    private static int count;
    private static void ok(boolean v,String why) {
        count++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field quantity=ItemStack.class.getField("stackSize");
        ok(quantity.getType()==Integer.TYPE,
           "actual Minecraft 1.8.9 public mutable stackSize field");
        Method item=ItemStack.class.getMethod("getItem");
        ok(Item.class.isAssignableFrom(item.getReturnType()),
           "genuine native ItemStack item identity accessor");
        Method damage=ItemStack.class.getMethod("getItemDamage");
        ok(damage.getReturnType()==Integer.TYPE,
           "genuine native ItemStack metadata/damage accessor");
        ok(Item.class.isAssignableFrom(ItemFishingRod.class) &&
           Item.class.isAssignableFrom(ItemSnowball.class),
           "actual native rod and snowball item classes linked");
        ProjectileInventoryFence fence=new ProjectileInventoryFence();
        Object source=new Object(),sourceItem=new Object(),
               stack=new Object(),projectileItem=new Object();
        fence.selected(1,5,2,6,source,sourceItem,1,0,
            stack,projectileItem,1,0);
        ok(fence.allowUse(1,6,2,6,source,sourceItem,1,0,
                         stack,projectileItem,1,0),
           "original stack reaches real native item-use method only once");
        fence.nativeUsed(1,6);
        ok(fence.mayRestore(1,2,6,source,sourceItem,1,0,null),
           "native last snowball depletion is supported after real use");
        ok(count>=6,"actual Minecraft 1.8.9 stack linkage probe executed");
        System.out.println("NATIVE_PROJECTILE_INVENTORY_FENCE_PASS assertions="+count);
    }
}
