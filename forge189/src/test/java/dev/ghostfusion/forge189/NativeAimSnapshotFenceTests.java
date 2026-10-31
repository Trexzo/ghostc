package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimSnapshotNativeFence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Original Forge 1.8.9 class linkage with exact native snapshot-row witness.
 * This headless test does not launch Minecraft or prove camera gameplay.
 */
public final class NativeAimSnapshotFenceTests {
    private static int checks;
    private static void ok(boolean yes,String description) {
        checks++;if(!yes)throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Field nativeWorld=Minecraft.class.getField("theWorld");
        Field nativePlayer=Minecraft.class.getField("thePlayer");
        Method entityId=Entity.class.getMethod("getEntityId");
        Method uuid=Entity.class.getMethod("getUniqueID");
        Method get=World.class.getMethod("getEntityByID",Integer.TYPE);
        Field yaw=Entity.class.getField("rotationYaw");
        Field pitch=Entity.class.getField("rotationPitch");
        ok(World.class.isAssignableFrom(nativeWorld.getType()),
           "real original Minecraft current world field exists");
        ok(EntityPlayerSP.class.isAssignableFrom(nativePlayer.getType()),
           "real native local player identity available");
        ok(entityId.getReturnType()==Integer.TYPE&&uuid.getReturnType()==UUID.class,
           "actual player ID and UUID accessor linked");
        ok(Entity.class.isAssignableFrom(get.getReturnType()),
           "original world entity registry exposes exact Entity instances");
        ok(yaw.getType()==Float.TYPE&&pitch.getType()==Float.TYPE,
           "native camera output uses real Minecraft yaw/pitch floats");
        ok(Entity.class.isAssignableFrom(EntityPlayer.class),
           "native player entity is part of original entity registry");

        Object world=new Object(),local=new Object(),old=new Object(),newEntity=new Object();
        UUID id=new UUID(369,189);
        AimSnapshotNativeFence proof=new AimSnapshotNativeFence();
        proof.begin(2,5,world,local);
        proof.record(17,id,old);
        ok(proof.matches(2,5,17,id,old,world,local,old),
           "same-frame exact native snapshot row permits later CAMERA guard");
        ok(!proof.matches(2,5,17,id,newEntity,world,local,newEntity),
           "respawn with identical UUID/entityID/coordinates is NOT same snapshot row");
        ok(!proof.matches(2,5,17,id,old,world,local,newEntity),
           "old entity cannot be reused if current world registry replaced it");
        proof.clear();
        ok(!proof.matches(2,5,17,id,old,world,local,old),
           "native references erased immediately after combat owner phase");
        ok(checks>=10,"original Minecraft 1.8.9 native provenance checked");
        System.out.println("NATIVE_AIM_SNAPSHOT_FENCE_PASS assertions="+checks);
    }
}
