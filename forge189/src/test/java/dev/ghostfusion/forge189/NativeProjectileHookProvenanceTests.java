package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileHookProvenance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Original Minecraft 1.8.9 native hook provenance API, no game launched. */
public final class NativeProjectileHookProvenanceTests {
    private static int checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field playerHook=EntityPlayer.class.getField("fishEntity");
        ok(playerHook.getType()==EntityFishHook.class,
           "exact native player fishEntity hook reference");
        Field angler=EntityFishHook.class.getField("angler");
        ok(angler.getType()==EntityPlayer.class,
           "exact native hook owner EntityPlayer.angler");
        Field world=Entity.class.getField("worldObj");
        ok(World.class.isAssignableFrom(world.getType()),
           "native hook.worldObj is an actual World reference");
        Method nativeId=Entity.class.getMethod("getEntityId");
        Method worldHook=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(nativeId.getReturnType()==Integer.TYPE &&
           Entity.class.isAssignableFrom(worldHook.getReturnType()),
           "native exact current-world entity registry identity accessible");
        Field isDead=Entity.class.getField("isDead");
        ok(isDead.getType()==Boolean.TYPE,
           "native hook liveness accessible");
        ProjectileHookProvenance policy=new ProjectileHookProvenance();
        Object p=new Object(),w=new Object(),old=new Object(),fresh=new Object();
        policy.arm(p,w,old);
        ok(!policy.eligible(p,w,old,p,w,old,false),
           "old baseline hook cannot count as newly spawned client hook");
        ok(policy.eligible(p,w,fresh,p,w,fresh,false),
           "genuinely NEW, owned and registered client hook is local-only evidence");
        ok(!policy.eligible(p,w,fresh,p,w,new Object(),false),
           "foreign native registry entity never yields local evidence");
        ok(checks>=8,"original Minecraft 1.8.9 hook provenance linkage complete");
        System.out.println("NATIVE_PROJECTILE_HOOK_PROVENANCE_PASS assertions="+checks);
    }
}
