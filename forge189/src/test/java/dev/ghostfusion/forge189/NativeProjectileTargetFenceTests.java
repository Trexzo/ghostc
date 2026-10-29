package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileTargetFence;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Real original Minecraft 1.8.9 Forge class linkage for native entity
 * identity and current-world lookup. Does not launch Minecraft or fabricate
 * an actual world/entity/attack acceptance.
 */
public final class NativeProjectileTargetFenceTests {
    private static int count;
    private static void ok(boolean v,String why) {
        count++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method uuid=EntityPlayer.class.getMethod("getUniqueID");
        ok(uuid.getReturnType()==UUID.class,
            "genuine EntityPlayer native UUID accessor");
        Method entityId=Entity.class.getMethod("getEntityId");
        ok(entityId.getReturnType()==Integer.TYPE,
            "genuine native entity identifier accessor");
        Method worldLookup=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(Entity.class.isAssignableFrom(worldLookup.getReturnType()),
            "genuine current World entity lookup before target acceptance");
        ProjectileTargetFence gate=new ProjectileTargetFence();
        UUID initial=new UUID(1L,2L),second=new UUID(1L,3L);
        gate.selected(1,100,initial,42);
        ok(!gate.allowUse(1,101,second,43,true),
            "new valid crosshair opponent cannot inherit native rod use");
        gate.reset();
        gate.selected(1,100,initial,42);
        ok(gate.allowUse(1,101,initial,42,true),
            "exact original player and next client tick can use projectile");
        ok(!gate.allowUse(1,101,initial,42,true),
            "native projectile permission only once");
        ok(count>=6,"native identity API linkage executed");
        System.out.println("NATIVE_PROJECTILE_TARGET_FENCE_PASS assertions="+count);
    }
}
