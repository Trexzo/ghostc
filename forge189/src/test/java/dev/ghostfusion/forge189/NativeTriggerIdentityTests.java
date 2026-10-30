package dev.ghostfusion.forge189;

import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.TriggerNativeIdentityFence;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Genuine Minecraft 1.8.9 native crosshair/UUID/world identity classes with
 * TriggerBot's pure owner-thread dwell. NO live game/server/hit is simulated.
 */
public final class NativeTriggerIdentityTests {
    private static int checks;
    private static void ok(boolean value,String description) {
        checks++;if(!value)throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Method unique=Entity.class.getMethod("getUniqueID");
        Method idMethod=Entity.class.getMethod("getEntityId");
        Method lookup=World.class.getMethod("getEntityByID",Integer.TYPE);
        Field crosshair=MovingObjectPosition.class.getField("entityHit");
        ok(unique.getReturnType()==UUID.class,"original native UUID accessor linked");
        ok(idMethod.getReturnType()==Integer.TYPE,"native runtime entity ID linked");
        ok(Entity.class.isAssignableFrom(lookup.getReturnType()),
           "original world entity registry method available");
        ok(Entity.class.isAssignableFrom(crosshair.getType()),
           "native Minecraft crosshair Entity reference available");
        ok(Entity.class.isAssignableFrom(EntityPlayer.class),
           "native player is a live Minecraft Entity subtype");

        KeyBinding attack=new KeyBinding("key.ghostc.r366.attack",223,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
        TriggerBotPolicy policy=new TriggerBotPolicy();
        Object world=new Object(),entity=new Object(),replacement=new Object();
        UUID uuid=UUID.fromString("123e4567-e89b-12d3-a456-426614173666");
        ok(!fence.observe(8,0,true,uuid,17,entity,world),
           "first native target observation starts fresh");
        ok(!policy.due(8,0,true,uuid,2,4),
           "first native acquisition tick cannot dispatch");
        ok(!fence.observe(8,1,true,uuid,17,entity,world),
           "same actual native object retains dwell");
        ok(!policy.due(8,1,true,uuid,2,4),"acquire still pending");
        ok(fence.observe(8,2,true,uuid,17,replacement,world),
           "native EntityPlayer replaced with SAME UUID/ID");
        policy.invalidateCandidate();
        ok(!policy.due(8,2,true,uuid,2,4),
           "replacement candidate receives full independent dwell");
        ok(!fence.permits(8,2,uuid,17,entity,world),
           "old native entity may not authorize KeyBinding");
        ok(fence.permits(8,2,uuid,17,replacement,world),
           "new entity is exact observed native target");
        ok(!fence.observe(8,3,true,uuid,17,replacement,world),
           "new entity remains stable");
        ok(!policy.due(8,3,true,uuid,2,4),
           "replacement cannot bypass acquire deadline");
        ok(!fence.observe(8,4,true,uuid,17,replacement,world),
           "native object remains identical");
        ok(policy.due(8,4,true,uuid,2,4),
           "new native EntityPlayer completed genuine dwell");
        ok(fence.permits(8,4,uuid,17,replacement,world),
           "final native identity checked before KeyBinding.onTick");
        KeyBinding.onTick(223);
        ok(attack.isPressed()&&!attack.isPressed(),
           "native attack output only after exact dwell and final fence");
        policy.dispatched(8,4,uuid);
        fence.invalidate();
        ok(!fence.permits(8,4,uuid,17,replacement,world),
           "stale dispatched native pulse cannot reuse target permission");
        ok(policy.state()==TriggerBotPolicy.State.COOLDOWN,
           "original trigger cooldown started only after real local native pulse");
        KeyBinding.unPressAllKeys();
        ok(checks>=18,"actual 1.8.9 linkage and identity/dwell native probe ran");
        System.out.println("NATIVE_TRIGGER_IDENTITY_PASS assertions="+checks);
    }
}
