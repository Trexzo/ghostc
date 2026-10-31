package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Original Minecraft 1.8.9 type linkage, not a launched client. */
public final class NativeAimIdentityTests {
    private static int checks;
    private static void ok(boolean valid,String why) {
        checks++;if(!valid)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method unique=Entity.class.getMethod("getUniqueID");
        Method entityId=Entity.class.getMethod("getEntityId");
        Method getById=World.class.getMethod("getEntityByID",Integer.TYPE);
        Field yaw=Entity.class.getField("rotationYaw");
        Field pitch=Entity.class.getField("rotationPitch");
        ok(unique.getReturnType()==UUID.class,"real native UUID accessor");
        ok(entityId.getReturnType()==Integer.TYPE,"real native numeric ID");
        ok(Entity.class.isAssignableFrom(getById.getReturnType()),
           "native world entity registry");
        ok(yaw.getType()==Float.TYPE&&pitch.getType()==Float.TYPE,
           "real native camera rotation fields");
        ok(Entity.class.isAssignableFrom(EntityPlayer.class),
           "real Minecraft EntityPlayer subtype");
        Object world=new Object(),a=new Object(),replacement=new Object();
        UUID id=UUID.fromString("123e4567-e89b-12d3-a456-426614173367");
        AimTargetSwitchPolicy p=new AimTargetSwitchPolicy();
        ok(p.considerNative(3,0,17,id,a,world,2),"first native target admitted");
        p.appliedNative(3,0,17,id,a,world);
        ok(!p.considerNative(3,1,17,id,replacement,world,2),
           "same UUID/ID replacement is delayed");
        ok(!p.considerNative(3,2,17,id,replacement,world,2),
           "respawn cannot inherit native rotation ownership");
        ok(p.considerNative(3,3,17,id,replacement,world,2),
           "new native entity earns full dwell");
        p.appliedNative(3,3,17,id,replacement,world);
        AimTargetLock lock=new AimTargetLock();
        lock.preferred(3,3,true);
        lock.appliedNative(3,3,17,id,replacement,world);
        ok(lock.sameNativeIdentity(id,replacement,world),"exact hard lock valid");
        ok(!lock.sameNativeIdentity(id,a,world),"old entity cannot inherit hard lock");
        ok(checks>=11,"native API and policy checks executed");
        System.out.println("NATIVE_AIM_IDENTITY_PASS assertions="+checks);
    }
}
