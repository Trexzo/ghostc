package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.ProjectileManualInputGate;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;

/**
 * Headless Java8 real Minecraft 1.8.9 KeyBinding/projectile API linkage.
 * This does not launch Minecraft, interact with a server or prove PvP hits.
 */
public final class NativeBlockHitTransientSuspensionTests {
    private static int checks;
    private static void ok(boolean b,String reason) {
        checks++;
        if(!b)throw new AssertionError(reason);
    }
    private static boolean interval(BlockHitPolicy p,long tick,int gap) {
        return p.preview(true,true,false,true,true,false,false,
            gap,0,"interval",tick,1);
    }
    public static void main(String[] args) throws Exception {
        Method projectile=ForgeCombatController.class.getDeclaredMethod(
            "suspendForProjectile");
        Method manual=ForgeCombatController.class.getDeclaredMethod(
            "suspendForManualHotbar");
        ok(projectile.getReturnType()==Void.TYPE &&
           manual.getReturnType()==Void.TYPE,
            "original Forge combat owner has both live suppression paths");
        ok(ItemFishingRod.class!=null && ItemSnowball.class!=null,
            "native Minecraft rod and snowball types remain linkable");
        KeyBinding use=new KeyBinding("key.ghostc.r385.use",245,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        BlockHitPolicy p=new BlockHitPolicy();
        ok(!interval(p,0,5),"first real held-key frame belongs to vanilla");
        ok(interval(p,5,5),"first native Block Hit USE is staged");
        KeyBinding.onTick(245);
        p.committed(5,1);
        ok(use.isPressed() && !use.isPressed(),
            "genuine Minecraft 1.8.9 KeyBinding pulse was committed");
        ok(ProjectileCombatHandoff.reserved(
            ProjectileOwnerArbiter.Owner.ROD,false,false),
            "rod hotbar transaction owns native input");
        p.suspendTransient();
        ok(!interval(p,7,5),"projectile resume first tick cannot write USE");
        ok(!interval(p,8,5),"committed USE cooldown remains in force");
        ok(!interval(p,9,5),"native cooldown still not expired");
        ok(interval(p,10,5),"original native commit permits USE at exact deadline");
        p.cancelPrepared();
        ok(!use.isPressed(),"speculative USE never emitted Minecraft input");

        ProjectileManualInputGate human=new ProjectileManualInputGate();
        human.mark();
        ok(human.take() && !human.take(),
            "manual slot key/wheel event is a one-frame veto");
        p.suspendTransient();
        ok(!interval(p,11,5),"manual slot resume first tick does not replay USE");
        ok(interval(p,12,5),
            "prior completed cooldown remains valid after manual hotbar");
        p.cancelPrepared();

        BlockHitPolicy speculative=new BlockHitPolicy();
        ok(!interval(speculative,0,2),"never-committed interval seed");
        ok(interval(speculative,2,2),"speculative native USE staged");
        speculative.suspendTransient();
        ok(!interval(speculative,3,2),
            "speculative stage is never replayed through hotbar");
        ok(!interval(speculative,4,2),
            "suspended vanilla baseline is not treated as completed USE");
        ok(interval(speculative,5,2),
            "new native event requires original interval after rearming");
        speculative.cancelPrepared();
        ok(checks>=18,"Java8 Block Hit suspension and native input test executed");
        System.out.println("NATIVE_BLOCKHIT_TRANSIENT_SUSPENSION_PASS assertions="+checks);
    }
}
