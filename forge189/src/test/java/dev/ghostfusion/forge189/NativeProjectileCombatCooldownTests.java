package dev.ghostfusion.forge189;

import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;

/**
 * Original Minecraft 1.8.9 Java8 linkage, plus headless TriggerBot/Hit
 * Select completed-cooldown continuity. No Minecraft instance or server.
 */
public final class NativeProjectileCombatCooldownTests {
    private static int count;
    private static void ok(boolean value,String why) {
        count++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method suspend=ForgeCombatController.class.getDeclaredMethod(
            "suspendForProjectile");
        Method nonMouse=ForgeCombatController.class.getDeclaredMethod(
            "resetNonMouse",Boolean.TYPE);
        ok(suspend.getReturnType()==Void.TYPE &&
           nonMouse.getReturnType()==Void.TYPE,
            "native combat distinguishes projectile suspend from full teardown");
        ok(ItemFishingRod.class!=null && ItemSnowball.class!=null,
            "original native projectile item types remain linked");
        KeyBinding attack=new KeyBinding("key.ghostc.r379.attack",237,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown(),"native attack key initially unpressed");
        ok(!attack.isPressed(),"native attack key has no queued synthetic pulse");
        ok(ProjectileCombatHandoff.reserved(
            ProjectileOwnerArbiter.Owner.ROD,false,false),
            "genuine rod transaction denies native combat");
        ok(ProjectileCombatHandoff.reserved(
            ProjectileOwnerArbiter.Owner.SNOWBALL,false,false),
            "genuine snowball transaction denies native combat");
        UUID target=new UUID(379L,3L);
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        HitSelectPolicy hit=new HitSelectPolicy();
        ok(!trigger.due(1,0,true,target,1,8),"first candidate acquires");
        ok(trigger.due(1,1,true,target,1,8),
            "actual local target dwell grants preview");
        ok(hit.preview(true,true,0,2,10,0,"normal",1,1,1),
            "shared Hit Select preview granted");
        hit.committed(1,1);
        trigger.dispatched(1,1,target);
        trigger.invalidateCandidate();
        hit.cancelPrepared();
        ok(!trigger.due(1,3,true,target,1,8),
            "post-projectile native candidate starts new dwell");
        ok(!trigger.due(1,4,true,target,1,8),
            "TriggerBot cannot bypass prior native dispatch cooldown");
        ok(!hit.preview(true,true,0,2,10,0,"normal",1,4,1),
            "shared Hit Select cannot bypass prior native dispatch interval");
        ok(count>=12,"Java8 projectile/attack continuity proof executed");
        System.out.println("NATIVE_PROJECTILE_COMBAT_COOLDOWN_PASS assertions="+count);
    }
}
