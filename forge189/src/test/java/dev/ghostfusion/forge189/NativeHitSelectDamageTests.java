package dev.ghostfusion.forge189;

import dev.ghostfusion.features.HitSelectPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import dev.ghostfusion.services.SessionServices;

/**
 * Headless Java8 original Minecraft 1.8.9 hurtTime/input API linkage and
 * exact final Hit Select prepared-token confirmation. No game launched.
 */
public final class NativeHitSelectDamageTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method callback=ForgeCombatController.class.getDeclaredMethod(
            "confirmHitSelectNative",EntityLivingBase.class,
            EntityPlayerSP.class,Boolean.TYPE,SessionServices.class);
        ok(Modifier.isPrivate(callback.getModifiers()) &&
           callback.getReturnType()==Boolean.TYPE,
            "Forge native attack owner has a final Hit Select validation boundary");
        ok(EntityLivingBase.class.getField("hurtTime").getType()==Integer.TYPE,
            "original Minecraft 1.8.9 target hurtTime is live integer");
        ok(KeyBinding.class.getMethod("onTick",Integer.TYPE).getReturnType()==Void.TYPE,
            "original Minecraft 1.8.9 native attack pulse API unchanged");
        HitSelectPolicy hit=new HitSelectPolicy();
        ok(hit.preview(true,true,0,2,6,0,"ready",1,1,1),
            "real Forge-ready target damage stage is initially eligible");
        ok(!hit.confirmPrepared(true,true,1,2,6,0,"ready",1,1,1),
            "late native hurtTime rise blocks ready attack");
        boolean denied=false;
        try{hit.committed(1,1);}
        catch(IllegalStateException expected){denied=true;}
        ok(denied,"rejected native preview cannot commit cooldown");
        ok(hit.preview(true,true,1,2,6,5,"trade",4,2,1),
            "trade mode can stage current self damage");
        ok(!hit.confirmPrepared(true,true,1,2,6,2,"trade",4,2,1),
            "late native self hurtTime drop cancels trade");
        ok(hit.preview(true,true,1,2,6,5,"trade",4,3,1),
            "failed trade does not consume shared cooldown");
        ok(hit.confirmPrepared(true,true,1,2,6,5,"trade",4,3,1),
            "same native settings and live damage pass");
        hit.committed(3,1);
        ok(!hit.preview(true,true,1,2,6,5,"trade",4,4,1),
            "only genuine completed attack consumes shared interval");
        ok(checks>=11,"native Hit Select damage boundary probed");
        System.out.println("NATIVE_HITSELECT_DAMAGE_PASS assertions="+checks);
    }
}
