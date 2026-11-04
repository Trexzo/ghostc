package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;

/**
 * Original Minecraft 1.8.9 Java8 KeyBinding linkage and headless
 * two-phase Block Hit event ownership. No game or server is launched.
 */
public final class NativeBlockHitEventOwnershipTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method tick=ForgeCombatController.class.getDeclaredMethod("tick");
        ok(tick.getReturnType()==Void.TYPE,
            "native combat still has one Forge owner END tick");
        Method pulse=KeyBinding.class.getMethod("onTick",Integer.TYPE);
        ok(pulse.getReturnType()==Void.TYPE,
            "original Minecraft 1.8.9 USE input method remains available");
        int useCode=244;
        KeyBinding use=new KeyBinding("key.ghostc.r384.use",useCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        BlockHitPolicy post=new BlockHitPolicy();
        ok(!post.preview(true,true,false,true,true,false,true,
            2,0,"post_attack",0,1),"first native attack arms a future event");
        ok(!post.preview(true,true,false,true,true,false,true,
            2,0,"post_attack",1,1),"overlapping attack cannot extend original event");
        ok(post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",2,1),"first pending event may stage USE");
        post.cancelPrepared();
        ok(!use.isPressed(),"rejected native USE sends no key pulse");
        ok(!post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",2,1),"same END tick cannot restage native USE");
        ok(post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",3,1),"original event remains available for bounded retry");
        KeyBinding.onTick(useCode);
        post.committed(3,1);
        ok(use.isPressed()&&!use.isPressed(),
            "one original Minecraft native USE pulse consumed after commit");
        ok(!post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",3,1),"committed native USE cannot duplicate in same tick");
        ok(!post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",4,1),"committed event cannot replay next tick");

        BlockHitPolicy damage=new BlockHitPolicy();
        ok(!damage.preview(true,true,false,true,true,false,false,
            2,0,"on_damage",0,3),"damage mode seeds initial hurtTime baseline");
        ok(!damage.preview(true,true,false,true,true,false,false,
            2,9,"on_damage",1,3),"first native damage rise creates pending event");
        ok(damage.preview(true,true,false,true,true,false,false,
            2,8,"on_damage",2,3),"pending hurt event can stage native USE");
        damage.cancelPrepared();
        ok(!damage.preview(true,true,false,true,true,false,false,
            2,10,"on_damage",2,3),
            "same-tick new hurt rise cannot restage canceled USE");
        ok(damage.preview(true,true,false,true,true,false,false,
            2,9,"on_damage",3,3),
            "native rejected damage event preserves original deadline");
        damage.cancelPrepared();
        ok(damage.preview(true,true,false,true,true,false,false,
            2,8,"on_damage",4,3),
            "original damage event remains within third-tick retry limit");
        damage.cancelPrepared();
        ok(!damage.preview(true,true,false,true,true,false,false,
            2,7,"on_damage",5,3),"expired damage event cannot replay");
        ok(checks>=16,"Java8 native Block Hit event lifecycle checked");
        System.out.println("NATIVE_BLOCKHIT_EVENT_OWNERSHIP_PASS assertions="+checks);
    }
}
