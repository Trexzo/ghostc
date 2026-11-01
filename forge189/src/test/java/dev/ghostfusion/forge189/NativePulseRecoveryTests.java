package dev.ghostfusion.forge189;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.services.SessionServices;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Java8 real Minecraft 1.8.9 KeyBinding and world-registry API probe. */
public final class NativePulseRecoveryTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method m=World.class.getMethod("getEntityByID",Integer.TYPE);
        ok(Entity.class.isAssignableFrom(m.getReturnType()),
            "original 1.8.9 native World.getEntityByID linked");
        final int attackCode=234;
        KeyBinding key=new KeyBinding("key.ghostc.r373.attack",attackCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        SessionServices session=new SessionServices();
        session.world(true);
        NativePulseSettlement gate=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        long first=session.generation();
        ok(gate.admitNative(Lane.ATTACK,first,world,player),
            "new session native identity verified");
        gate.begin(Lane.ATTACK,first,session.tick());
        KeyBinding.onTick(attackCode);
        gate.nativeReturned(Lane.ATTACK,first,session.tick());
        gate.abandon(Lane.ATTACK,first,session.tick());
        ok(key.isPressed()&&!key.isPressed(),"one genuine native key event queued");
        session.world(false);session.world(true);
        ok(!gate.admitNative(Lane.ATTACK,session.generation(),world,player),
            "numeric new generation alone cannot replay old native pulse");
        ok(!key.isPressed(),"no duplicate key queued by rejected admission");
        session.world(false);session.world(true);
        ok(gate.admitNative(Lane.ATTACK,session.generation(),new Object(),player),
            "new native world and newer generation rearm");
        ok(checks>=6,"native Minecraft recovery boundary executed");
        System.out.println("NATIVE_PULSE_RECOVERY_PROBE_PASS assertions="+checks);
    }
}
