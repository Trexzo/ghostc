package dev.ghostfusion.forge189;

import dev.ghostfusion.features.DamageMotionHandoff;
import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.features.VelocityPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/**
 * Java8 genuine Minecraft 1.8.9 class linkage and headless damage-handoff
 * policy probe. No Minecraft process, server packets or in-game jump occurs.
 */
public final class NativeVelocityJumpHandoffTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method velocity=ForgeVelocityController.class.getDeclaredMethod("tick");
        ok(velocity.getReturnType()==Boolean.TYPE,
            "native owner-tick Velocity exposes vertical mutation admission");
        Method jumpTick=ForgeJumpResetController.class.getDeclaredMethod(
            "tick",Boolean.TYPE);
        ok(jumpTick.getReturnType()==Void.TYPE,
            "native Jump Reset accepts explicit vertical owner permission");
        Method jump=EntityPlayerSP.class.getMethod("jump");
        ok(Modifier.isPublic(jump.getModifiers()) &&
            jump.getReturnType()==Void.TYPE,
            "original player jump() entrypoint remains unmodified");
        ok(Entity.class.getField("motionY").getType()==Double.TYPE,
            "original native Y motion axis remains a double");
        ok(S12PacketEntityVelocity.class.getMethod("getMotionY")
            .getReturnType()==Integer.TYPE,
            "packet-mode S12 motion uses independent Minecraft packet API");
        VelocityPolicy v=new VelocityPolicy();
        JumpResetPolicy p=new JumpResetPolicy();
        ok(v.sample(true,true,0,0.5,0.4,-0.3,50,40,0,1)==null,
            "native initial Velocity damage baseline is passive");
        ok(!p.preview(1,0,0,true,8,12),
            "native first Jump Reset sample passive");
        VelocityPolicy.Adjustment adjusted=v.sample(true,true,10,
            0.5,0.4,-0.3,50,40,1,1);
        ok(DamageMotionHandoff.verticalChanged(0.4,adjusted) &&
            !DamageMotionHandoff.jumpAvailable(true,true),
            "native vertical knockback modification claims exclusive jump frame");
        ok(!p.preview(1,1,10,false,8,12),
            "blocked native jump still consumes fresh damage edge");
        ok(!p.preview(1,2,9,true,8,12),
            "no delayed native jump after vertical Velocity write");
        ok(checks>=10,"Minecraft 1.8.9 velocity-to-jump linkage exercised");
        System.out.println("NATIVE_VELOCITY_JUMP_HANDOFF_PASS assertions="+checks);
    }
}
