package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimFinalCameraWriteGate;
import dev.ghostfusion.features.AimFinalCameraWriteGate.SourcePose;
import dev.ghostfusion.features.AimFinalCameraWriteGate.TargetState;
import dev.ghostfusion.features.AimFinalCameraWriteGate.Inputs;
import dev.ghostfusion.features.AimFinalCameraWriteGate.Ticket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MouseHelper;
import net.minecraft.world.World;
import java.lang.reflect.Field;
import java.util.UUID;

/** Java8 native API linkage, NOT launched Minecraft gameplay. */
public final class NativeAimFinalCameraWriteTests {
    private static int checks;
    private static void ok(boolean b,String description) {
        checks++;if(!b)throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Field world=Minecraft.class.getField("theWorld");
        Field local=Minecraft.class.getField("thePlayer");
        Field focus=Minecraft.class.getField("inGameHasFocus");
        Field gui=Minecraft.class.getField("currentScreen");
        Field settings=Minecraft.class.getField("gameSettings");
        Field dx=MouseHelper.class.getField("deltaX");
        Field dy=MouseHelper.class.getField("deltaY");
        Field attack=GameSettings.class.getField("keyBindAttack");
        Field use=GameSettings.class.getField("keyBindUseItem");
        Field yaw=Entity.class.getField("rotationYaw");
        Field pitch=Entity.class.getField("rotationPitch");
        ok(World.class.isAssignableFrom(world.getType()),
           "actual original Minecraft world ref available for final veto");
        ok(EntityPlayerSP.class.isAssignableFrom(local.getType()),
           "actual original client player identity available for final veto");
        ok(focus.getType()==Boolean.TYPE&&
           GuiScreen.class.isAssignableFrom(gui.getType()),
           "actual focus/GUI state can veto before native write");
        ok(GameSettings.class.isAssignableFrom(settings.getType())&&
           KeyBinding.class.isAssignableFrom(attack.getType())&&
           KeyBinding.class.isAssignableFrom(use.getType()),
           "actual native attack/use keys exposed for final input check");
        ok(dx.getType()==Integer.TYPE&&dy.getType()==Integer.TYPE,
           "actual original physical mouse deltas exposed for final veto");
        ok(yaw.getType()==Float.TYPE&&pitch.getType()==Float.TYPE,
           "real native camera writes use yaw/pitch fields");
        ok(Entity.class.isAssignableFrom(EntityPlayer.class),
           "native Minecraft target derives from entity class");

        AimFinalCameraWriteGate gate=new AimFinalCameraWriteGate();
        Object currentWorld=new Object(),me=new Object(),snap=new Object(),target=new Object();
        SourcePose pose=new SourcePose(0,2,3,10,5,1.62);
        TargetState live=new TargetState(4,2,8,20,false,false);
        Inputs inputs=new Inputs(true,true,true,true,false,true,true,false,
                                 false,true,0,0);
        UUID uuid=new UUID(370,8);
        Ticket ticket=gate.capture(6,9,currentWorld,me,snap,target,7,uuid,
                                   pose,live,inputs);
        ok(gate.permits(ticket,6,9,currentWorld,me,snap,target,7,uuid,
                        pose,live,inputs),
           "same native session/pose/input is eligible after arbitration");
        Inputs mouseMoved=new Inputs(true,true,true,true,false,true,true,false,
                                     false,true,6,0);
        ok(!gate.permits(ticket,6,9,currentWorld,me,snap,target,7,uuid,
                         pose,live,mouseMoved),
           "actual native mouse movement invalidates previously granted camera");
        ok(!gate.permits(ticket,6,9,new Object(),me,snap,target,7,uuid,
                         pose,live,inputs),
           "world change vetoes a previously chosen camera owner");
        ok(!gate.permits(ticket,6,9,currentWorld,me,snap,target,7,uuid,
                         new SourcePose(0,2,3,11,5,1.62),live,inputs),
           "native player camera delta before write cannot be overwritten");
        ok(checks>=11,"native 1.8.9 prewrite linkage and veto tests executed");
        System.out.println("NATIVE_AIM_FINAL_CAMERA_PASS assertions="+checks);
    }
}
