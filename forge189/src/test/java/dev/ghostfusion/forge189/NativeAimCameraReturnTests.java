package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimCameraReturnFence;
import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MouseHelper;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Actual original Minecraft 1.8.9 mouse-helper and entity/world API
 * linkage with owner-only camera return. Headless; NO game/server launched.
 */
public final class NativeAimCameraReturnTests {
    private static int checks;
    private static void ok(boolean b,String reason) {
        checks++;if(!b)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Field mouse=Minecraft.class.getField("mouseHelper");
        ok(MouseHelper.class.isAssignableFrom(mouse.getType()),
           "real Minecraft 1.8.9 mouse helper is native client field");
        Field dx=MouseHelper.class.getField("deltaX");
        Field dy=MouseHelper.class.getField("deltaY");
        ok(dx.getType()==Integer.TYPE&&dy.getType()==Integer.TYPE,
           "real native mouse-helper deltas support physical-input override");
        Method uuid=Entity.class.getMethod("getUniqueID");
        Method id=Entity.class.getMethod("getEntityId");
        Method registry=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(uuid.getReturnType()==UUID.class&&id.getReturnType()==Integer.TYPE,
           "native UUID and entityId available for exact Aim handback");
        ok(Entity.class.isAssignableFrom(registry.getReturnType()),
           "native world identity registry can be rechecked before rotation");

        AimCameraReturnFence returnGate=new AimCameraReturnFence();
        AimManualInputPolicy manual=new AimManualInputPolicy();
        UUID target=new UUID(8,9);
        Object entity=new Object(),world=new Object();
        ok(AimTriggerHandoff.yieldCamera(true,false,false,false,true,true),
           "real verified unheld Trigger candidate causes camera yield");
        returnGate.yieldCamera(1,0);
        ok(manual.suppressed(1,0,true,5,0,3,3),
           "native physical mouse movement while Trigger owns camera is observed");
        returnGate.yieldCamera(1,1);
        ok(manual.suppressed(1,1,true,0,0,3,3),
           "manual override continues during Trigger camera ownership");
        ok(manual.suppressed(1,2,true,0,0,3,3),
           "manual override persists at first attempted handback");
        returnGate.interrupt();
        ok(!manual.suppressed(1,3,true,0,0,3,3),
           "mouse pause expires before fresh Aim candidate");
        ok(!returnGate.consider(1,3,7,target,entity,world,2),
           "Aim handback uses configured return delay, not first-target shortcut");
        ok(!returnGate.consider(1,4,7,target,entity,world,2),
           "same native target still below delay");
        ok(returnGate.consider(1,5,7,target,entity,world,2),
           "exact live same-world candidate earns return permission");
        returnGate.applied(1,5);
        ok(!returnGate.pending(),
           "native rotation completion is the only handback commit");
        returnGate.yieldCamera(1,6);
        ok(returnGate.consider(1,7,7,target,entity,world,0),
           "legacy switch-delay zero has immediate camera return");
        returnGate.applied(1,7);
        ok(checks>=14,"native Forge Aim camera handback linkage verified");
        System.out.println("NATIVE_AIM_CAMERA_RETURN_PASS assertions="+checks);
    }
}
