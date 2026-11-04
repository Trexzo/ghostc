package dev.ghostfusion.forge189;

import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.TriggerNativeIdentityFence;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/**
 * Java8 original Minecraft 1.8.9 and Forge linkage for the same-tick
 * TriggerBot attack arbitration. All evidence is headless and local.
 */
public final class NativeTriggerSameTickAdmissionTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method nativeCombat=ForgeCombatController.class.getDeclaredMethod("tick");
        ok(nativeCombat.getReturnType()==Void.TYPE,
            "native combat END-tick entrypoint remains present");
        ok(AttackEntityEvent.class.getField("target")!=null,
            "original Forge attack target API still linked");
        KeyBinding attack=new KeyBinding("key.ghostc.r380.attack",238,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown() && !attack.isPressed(),
            "original Minecraft native attack starts with no queued input");
        UUID id=new UUID(380L,1L);
        Object world=new Object(),entity=new Object(),other=new Object();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
        for(int tick=0;tick<=2;tick++) {
            ok(!fence.observe(1,tick,true,id,19,entity,world),
                "genuine native identity stable through required dwell");
            ok(trigger.due(1,tick,true,id,2,6)==(tick==2),
                "headless native target requires complete acquisition");
        }
        ok(!fence.observe(1,2,true,id,19,entity,world),
            "duplicate identical target is passive, not a runtime fault");
        ok(!trigger.due(1,2,true,id,2,6),
            "duplicate native END tick never emits a second intent");
        ok(fence.observe(1,2,true,id,19,other,world),
            "same UUID/ID changed native object invalidates current candidate");
        trigger.invalidateCandidate();
        ok(!fence.permits(1,2,id,19,entity,world) &&
           !fence.permits(1,2,id,19,other,world),
            "neither old nor replacement native object may attack this frame");
        ok(!trigger.due(1,2,true,id,2,6),
            "same-tick replacement cannot borrow prior ready state");
        ok(!fence.observe(1,3,true,id,19,other,world),
            "new player begins fresh native acquisition next tick");
        ok(!trigger.due(1,3,true,id,2,6),
            "fresh target must reacquire after identity replacement");
        ok(checks>=15,"original Minecraft and Forge headless admission checked");
        System.out.println("NATIVE_TRIGGER_SAME_TICK_PASS assertions="+checks);
    }
}
