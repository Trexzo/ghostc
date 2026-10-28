package dev.ghostfusion.forge189;

import dev.ghostfusion.features.JumpResetPolicy;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Real Forge 1.8.9 class-linkage check. A headless JDK8 probe cannot
 * manufacture an authorized world/player to jump; real gameplay remains
 * unverified until launched original Minecraft.
 */
public final class NativeJumpResetTests {
    private static int checks;
    private static void ok(boolean value,String reason) {
        checks++;if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Method jump=EntityPlayerSP.class.getMethod("jump");
        ok(Modifier.isPublic(jump.getModifiers()) &&
           jump.getReturnType()==Void.TYPE &&
           jump.getParameterTypes().length==0,
           "genuine Minecraft 1.8.9 player exposes native public jump()");
        ok(EntityPlayer.class.isAssignableFrom(EntityPlayerSP.class),
           "native player identity is the genuine local Minecraft player type");
        JumpResetPolicy gate=new JumpResetPolicy();
        ok(!gate.preview(1,0,10,true,8,12),
           "native initial hurt baseline must never create a jump");
        ok(!gate.preview(1,1,0,true,8,12),
           "native decay does not create a jump");
        ok(gate.preview(1,2,10,true,8,12),
           "only fresh damage admits the original game jump entrypoint");
        gate.cancelPrepared();
        ok(!gate.preview(1,3,9,true,8,12),
           "native veto cannot replay damage on a later tick");
        ok(checks>=6,"native 1.8.9 jump method check executed");
        System.out.println("NATIVE_JUMP_RESET_PASS assertions="+checks);
    }
}
