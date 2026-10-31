package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimCameraReturnFence;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition;
import java.util.UUID;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/**
 * Checks original Minecraft 1.8.9 world-entity/UUID and crosshair linkage
 * with the exact native Aim switch/lock policy. No launched client or server.
 */
public final class NativeAimRetentionTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method id=Entity.class.getMethod("getEntityId");
        Method uuid=Entity.class.getMethod("getUniqueID");
        Method get=World.class.getMethod("getEntityByID",Integer.TYPE);
        Field crosshair=MovingObjectPosition.class.getField("entityHit");
        Field aim=Entity.class.getField("rotationYaw");
        Field pitch=Entity.class.getField("rotationPitch");
        ok(id.getReturnType()==Integer.TYPE && uuid.getReturnType()==UUID.class,
            "native Minecraft entity ID and UUID are independently exposed");
        ok(Entity.class.isAssignableFrom(get.getReturnType()),
            "native World entity registry supports exact object equality");
        ok(Entity.class.isAssignableFrom(crosshair.getType()),
            "native entity crosshair representation available");
        ok(aim.getType()==Float.TYPE&&pitch.getType()==Float.TYPE,
            "original native camera yaw/pitch fields are real");
        ok(Entity.class.isAssignableFrom(EntityPlayer.class),
            "real Minecraft EntityPlayer inherits native identity");
        ok(Minecraft.class.getField("theWorld")!=null,
            "real Minecraft current world boundary is available");

        AimTargetSwitchPolicy switcher=new AimTargetSwitchPolicy();
        AimTargetLock lock=new AimTargetLock();
        UUID player=new UUID(1,368);
        Object original=new Object(),reborn=new Object(),world=new Object();
        ok(switcher.considerNative(12,0,19,player,original,world,2),
            "first exact native entity may stage camera movement");
        switcher.appliedNative(12,0,19,player,original,world);
        ok(!switcher.considerNative(12,1,19,player,reborn,world,2),
            "SAME ID and SAME UUID but recreated player does not inherit native camera");
        ok(!switcher.considerNative(12,2,19,player,reborn,world,2),
            "replaced native entity must still wait configured delay");
        ok(switcher.considerNative(12,3,19,player,reborn,world,2),
            "replacement object independently earned fresh delay");
        switcher.appliedNative(12,3,19,player,reborn,world);
        ok(lock.preferred(12,3,true)==-1,"native hard lock begins with no hint");
        lock.appliedNative(12,3,19,player,reborn,world);
        ok(lock.preferred(12,4,true)==19 &&
           lock.sameNativeIdentity(player,reborn,world),
           "native hard lock follows last real same-world player object");
        ok(!lock.sameNativeIdentity(player,original,world),
            "old object may not impersonate retained native target");
        lock.reset();
        ok(!lock.sameNativeIdentity(player,reborn,world),
            "Panic/world reset clears hard-lock native references");
        AimCameraReturnFence returnFence=new AimCameraReturnFence();
        returnFence.yieldCamera(12,5);
        ok(!returnFence.consider(12,6,19,player,reborn,world,2),
            "Trigger camera yield does not fast-forward native Aim retention");
        ok(checks>=14,"real 1.8.9 native field and policy linkage checked");
        System.out.println("NATIVE_AIM_RETENTION_PASS assertions="+checks);
    }
}
