package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;

/**
 * Original Minecraft 1.8.9 Java8 input linkage and native settlement
 * coordination. Headless only; no game process, packet or real PvP hit.
 */
public final class NativeBlockHitAttackUncertaintyTests {
    private static int checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method interlock=NativePulseSettlement.class.getMethod(
            "attackConflictsWithBlockHit",Long.TYPE,Long.TYPE);
        ok(interlock.getReturnType()==Boolean.TYPE,
            "Block Hit consults owner-thread ATTACK settlement proof");
        ok(ForgeCombatController.class.getDeclaredMethod("tick")
            .getReturnType()==Void.TYPE,
            "original native Minecraft combat END owner entrypoint retained");

        int attackCode=242,useCode=243;
        KeyBinding attack=new KeyBinding("key.ghostc.r383.attack",attackCode,
            "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r383.use",useCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        NativePulseSettlement settlement=new NativePulseSettlement();
        BlockHitPolicy block=new BlockHitPolicy();
        Object w=new Object(),p=new Object();
        ok(settlement.reconcileNative(1,w,p),"real native identity pinned");
        ok(!block.preview(true,true,false,true,true,false,false,
            2,0,"interval",0,1),"initial hold is vanilla only");
        ok(block.preview(true,true,false,true,true,false,false,
            2,0,"interval",2,1),"native Block Hit USE preview staged");
        settlement.begin(Lane.ATTACK,1,2);
        KeyBinding.onTick(attackCode);
        settlement.nativeReturned(Lane.ATTACK,1,2);
        settlement.abandon(Lane.ATTACK,1,2);
        ok(settlement.phase(Lane.ATTACK,1)==Phase.UNCERTAIN,
            "original Minecraft key pulse may precede failed combat receipt");
        if(settlement.attackConflictsWithBlockHit(1,2))
            block.cancelPrepared();
        else KeyBinding.onTick(useCode);
        ok(!use.isPressed(),
            "Block Hit never adds native USE after ambiguous ATTACK");
        ok(attack.isPressed()&&!attack.isPressed(),
            "actual headless Minecraft ATTACK key pulse was observed");
        ok(block.preview(true,true,false,true,true,false,false,
            2,0,"interval",3,1),
            "suppressed USE did not consume Block Hit's interval");
        if(settlement.attackConflictsWithBlockHit(1,3))
            block.cancelPrepared();
        else KeyBinding.onTick(useCode);
        ok(!use.isPressed(),
            "later native USE remains disabled until native ATTACK recovery");
        ok(settlement.reconcileNative(2,w,p),
            "numeric new epoch alone leaves uncertain native player");
        ok(settlement.attackConflictsWithBlockHit(2,0),
            "same native identity cannot rearm dependent Block Hit");
        ok(settlement.reconcileNative(3,new Object(),p),
            "new generation plus world identity clears native quarantine");
        ok(!settlement.attackConflictsWithBlockHit(3,0),
            "verified new native world allows future Block Hit scheduling");
        ok(checks>=13,"native Java8 ATTACK/USE interlock checked");
        System.out.println("NATIVE_BLOCKHIT_ATTACK_UNCERTAINTY_PASS assertions="+checks);
    }
}
