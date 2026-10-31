package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import java.lang.reflect.Field;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MouseHelper;

/**
 * Java 8 native boundary check: genuine Minecraft 1.8.9 MouseHelper fields
 * and KeyBinding state; no actual game launch or physical mouse is asserted.
 */
public final class NativeAimHandoffTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Field deltaX=MouseHelper.class.getField("deltaX");
        Field deltaY=MouseHelper.class.getField("deltaY");
        ok(deltaX.getType()==Integer.TYPE && deltaY.getType()==Integer.TYPE,
            "original Minecraft 1.8.9 native mouse deltas are integer fields");
        KeyBinding attack=new KeyBinding("key.ghostc.r368.attack",225,"key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r368.use",226,"key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown()&&!use.isKeyDown(),
            "actual native attack/use keys are initially released");
        ok(AimTriggerHandoff.yieldCamera(true,false,false,false,true,true),
            "native unheld trigger crosshair yields camera");
        AimManualInputPolicy manual=new AimManualInputPolicy();
        ok(manual.suppressed(9,0,true,3,0,3,3),
            "real mouse field semantics begin pure pause on trigger yield tick");
        ok(manual.suppressed(9,1,true,0,0,3,3),
            "native crosshair loss cannot erase sampled physical movement");
        ok(manual.suppressed(9,2,true,0,0,3,3),
            "native trigger handoff cannot shorten pause");
        ok(!manual.suppressed(9,3,true,0,0,3,3),
            "native pause expiration returns camera without added delay");
        ok(!AimTriggerHandoff.yieldCamera(true,true,false,false,true,true),
            "real attack down does not transfer Aim Assist camera");
        KeyBinding.setKeyBindState(225,true);
        ok(attack.isKeyDown(),"native Minecraft physical key binding observable");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown(),"native release observable");
        ok(checks>=10,"native API and continuity smoke completed");
        System.out.println("NATIVE_AIM_HANDOFF_PASS assertions="+checks);
    }
}
