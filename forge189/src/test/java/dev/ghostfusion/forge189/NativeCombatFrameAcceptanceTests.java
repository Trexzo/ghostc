package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CombatFrameAcceptance;
import dev.ghostfusion.features.CombatFrameAcceptance.Result;
import dev.ghostfusion.features.PhysicalInputGate;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import java.lang.reflect.Field;

/**
 * Headless test with actual Minecraft 1.8.9 KeyBinding and Forge callback
 * classes. NO game launch, server, physical mouse event, damage or packet.
 */
public final class NativeCombatFrameAcceptanceTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field target=AttackEntityEvent.class.getField("target");
        Field attacker=AttackEntityEvent.class.getField("entityPlayer");
        ok(Entity.class.isAssignableFrom(target.getType()),
           "actual Forge attack callback exposes native target Entity");
        ok(EntityPlayer.class.isAssignableFrom(attacker.getType()),
           "actual Forge callback exposes native attacking player");
        KeyBinding attack=new KeyBinding("key.ghostc.r365.attack",217,
                                        "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r365.use",218,
                                        "key.categories.misc");
        KeyBinding.unPressAllKeys();
        CombatFrameAcceptance frame=new CombatFrameAcceptance();
        PhysicalInputGate physical=new PhysicalInputGate();
        long epoch=1,t=0;
        ok(!physical.allowed(epoch,true,true,false,false,217,218),
           "initial native released-state sample");
        // A forged auto pulse during temporary rod ownership must be detected
        // independently of which module queued it.
        KeyBinding.onTick(217);
        boolean pulse=attack.isPressed();
        ok(pulse&& !attack.isPressed() && !use.isPressed(),
           "genuine Minecraft native pulse is read once by key binding");
        ok(frame.observe(epoch,t++,true,true,false,false,false,false,
                         pulse,pulse)==Result.UNEXPECTED_NATIVE_WRITE,
           "rod-exclusive native KeyBinding output is an evidence violation");
        KeyBinding.unPressAllKeys();
        ok(frame.observe(epoch,t++,true,true,false,false,false,false,
                         false,false)==Result.PROJECTILE_EXCLUSIVE,
           "normal native rod held tick produces no extra attack");
        ok(frame.observe(epoch,t++,true,false,false,false,false,true,
                         false,false)==Result.POST_PROJECTILE_REARM,
           "first game tick after rod release cannot replay the old attack");
        ok(!physical.allowed(epoch,true,true,false,true,217,218),
           "fresh held key alone is not a newly authorized synthetic click");
        ok(!physical.allowed(epoch,true,true,false,false,217,218),
           "actual native release restores click eligibility");
        ok(physical.allowed(epoch,true,true,false,true,217,218),
           "fresh physical press may rearm optional combat");
        KeyBinding.onTick(217);
        ok(attack.isPressed(),"real local native onTick press can be observed");
        ok(frame.observe(epoch,t++,true,false,false,false,false,true,
                         true,true)==Result.COMBAT_RAN,
           "normal combat pulse is independently accounted as a local action");
        KeyBinding.unPressAllKeys();
        ok(frame.observe(epoch,t++,true,false,true,false,false,false,
                         false,false)==Result.PROJECTILE_EXCLUSIVE,
           "snowball USE frame cannot inherit native Clicker pulse");
        KeyBinding.onTick(218);
        boolean usePulse=use.isPressed();
        ok(usePulse,"real Minecraft native use-key pulse was emitted");
        ok(frame.observe(epoch,t++,true,false,false,true,false,false,
                         usePulse,false)==Result.UNEXPECTED_NATIVE_WRITE,
           "manual hotbar override and native synthetic USE are incompatible");
        KeyBinding.unPressAllKeys();
        frame.reset();physical.reset();
        ok(frame.observe(epoch+1,0,true,false,false,false,false,true,
                         false,false)==Result.COMBAT_RAN,
           "new session erases old projectile ownership");
        ok(checks>=14,"JDK8 native combat handoff/linkage cases executed");
        System.out.println("NATIVE_COMBAT_FRAME_ACCEPTANCE_PASS assertions="+checks);
    }
}
