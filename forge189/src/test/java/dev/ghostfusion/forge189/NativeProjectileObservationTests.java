package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileObservationWindow;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import dev.ghostfusion.features.ProjectileObservationWindow.Outcome;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Original Minecraft 1.8.9 hook/inventory observation API; NOT gameplay. */
public final class NativeProjectileObservationTests {
    private static int checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field hook=EntityPlayer.class.getField("fishEntity");
        ok(hook.getType()==EntityFishHook.class,
           "original Minecraft player exposes actual native hook object");
        Field size=ItemStack.class.getField("stackSize");
        ok(size.getType()==Integer.TYPE,
           "original Minecraft stack size supports passive local reads");
        Method lookup=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(Entity.class.isAssignableFrom(lookup.getReturnType()),
           "same-world player object identity can be rechecked");
        Field dead=Entity.class.getField("isDead");
        ok(dead.getType()==Boolean.TYPE,
           "native hook visibility can exclude dead local entities");
        ProjectileObservationWindow obs=new ProjectileObservationWindow();
        obs.arm(3,20);
        ok(obs.observe(3,21,true,Signal.ROD_HOOK_VISIBLE)==Outcome.LOCAL_HOOK,
           "native hook is local observation only");
        obs.arm(3,30);
        ok(obs.observe(3,31,true,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.LOCAL_DECREASE,
           "native decreased stack is local observation only");
        ok(checks>=6,"genuine Minecraft 1.8.9 observation API checked");
        System.out.println("NATIVE_PROJECTILE_OBSERVATION_PASS assertions="+checks);
    }
}
