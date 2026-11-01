package dev.ghostfusion.forge189;

import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.features.WTapPolicy.Decision;
import java.lang.reflect.Method;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/**
 * Headless original Forge 1.8.9 Java8 native method linkage plus the exact
 * policy edge replay. Does not instantiate Minecraft or claim server hits.
 */
public final class NativeWTapJumpContinuityTests {
    private static int assertions;
    private static void ok(boolean yes,String reason) {
        assertions++;if(!yes)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Method observer=ForgeJumpResetController.class.getDeclaredMethod(
            "observeWTapReservation");
        ok(observer.getReturnType()==Void.TYPE,
            "native Jump Reset has a separate suppressed-frame observer");
        Method owned=ForgeJumpResetController.class.getDeclaredMethod(
            "tick",Boolean.TYPE);
        ok(owned.getReturnType()==Void.TYPE,
            "R3.77 native Velocity vertical handoff retained");
        ok(EntityPlayerSP.class.getMethod("jump").getReturnType()==Void.TYPE,
            "genuine Minecraft 1.8.9 jump entrypoint unchanged");
        ok(AttackEntityEvent.class.getField("target")!=null,
            "genuine Forge native WTap attack event still linked");
        WTapPolicy tap=new WTapPolicy();
        JumpResetPolicy jump=new JumpResetPolicy();
        ok(!jump.preview(1,0,0,true,8,12),
            "initial native damage baseline is passive");
        ok(jump.preview(1,1,10,true,8,12),
            "prior native damage edge may grant Jump Reset");
        jump.committed(1,1);
        ok(tap.attack(true,true,true,2,2,1),
            "native attack event reserves WTap sprint timing");
        ok(!jump.preview(1,2,9,false,8,12),
            "WTap reservation consumes hurt without native jump");
        ok(tap.tick(true,true,3,1)==Decision.RESET,
            "WTap sprint-off timing remains original");
        ok(!jump.preview(1,3,10,false,8,12),
            "suppressed new damage cannot synthesize jump");
        ok(tap.tick(true,true,4,1)==Decision.HOLD &&
           tap.tick(true,true,5,1)==Decision.RESTORE,
            "native hold and restore cadence unchanged");
        ok(!jump.preview(1,4,9,false,8,12) &&
           !jump.preview(1,5,0,false,8,12),
            "denied window still records every hurt transition");
        ok(!jump.preview(1,6,10,true,8,12),
            "old jump cooldown survives WTap restore");
        ok(!jump.preview(1,7,9,true,8,12),
            "suppressed native damage cannot replay later");
        ok(!jump.preview(1,8,0,true,8,12),
            "hurt baseline returns to idle");
        ok(jump.preview(1,13,10,true,8,12),
            "fresh event at original cooldown end can jump");
        jump.cancelPrepared();
        ok(assertions>=15,"native WTap-to-Jump Reset continuity probed");
        System.out.println("NATIVE_WTAP_JUMP_CONTINUITY_PASS assertions="+assertions);
    }
}
