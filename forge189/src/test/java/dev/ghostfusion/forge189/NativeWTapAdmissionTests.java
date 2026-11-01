package dev.ghostfusion.forge189;

import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.features.WTapPolicy.Decision;
import java.lang.reflect.Method;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Original Forge 1.8.9 event cancellation and Minecraft sprint method probe.
 * The test never instantiates a Minecraft game or spoofs a hit/server ACK.
 */
public final class NativeWTapAdmissionTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method hook=ForgeEventAdapter.class.getMethod("onAttackEntity",
            AttackEntityEvent.class);
        SubscribeEvent settings=hook.getAnnotation(SubscribeEvent.class);
        ok(settings!=null,"native Forge attack event listener registered");
        ok(settings.priority()==EventPriority.LOWEST,
            "WTap observes attack at lowest native event priority");
        ok(!settings.receiveCanceled(),
            "canceled attack events are filtered at native event bus boundary");
        ok(AttackEntityEvent.class.isAnnotationPresent(Cancelable.class),
            "genuine Forge AttackEntityEvent carries cancelable declaration");
        ok(AttackEntityEvent.class.getField("target")!=null,
            "original Forge target reference retained");
        ok(EntityPlayerSP.class.getMethod("setSprinting",Boolean.TYPE)
            .getReturnType()==Void.TYPE,
            "original Minecraft 1.8.9 native sprint setter retained");
        WTapPolicy policy=new WTapPolicy();
        ok(policy.attack(true,true,true,2,0,1),"fresh accepted attack stages reset");
        ok(policy.tick(true,true,0,1)==Decision.NONE,
            "native attack callback does not change sprint immediately");
        ok(policy.tick(true,true,1,1)==Decision.RESET,
            "next tick performs sole sprint-reset decision");
        ok(policy.tick(true,true,1,1)==Decision.NONE,
            "reentrant same-tick tick never repeats sprint write");
        ok(policy.tick(true,true,2,1)==Decision.HOLD,
            "original second-tick native sprint hold preserved");
        ok(policy.tick(true,true,2,1)==Decision.NONE,
            "duplicate native hold never repeats");
        ok(policy.tick(true,true,3,1)==Decision.RESTORE,
            "original restore cadence preserved");
        ok(checks>=13,"genuine Forge 1.8.9 WTap gate verified");
        System.out.println("NATIVE_WTAP_ADMISSION_PASS assertions="+checks);
    }
}
