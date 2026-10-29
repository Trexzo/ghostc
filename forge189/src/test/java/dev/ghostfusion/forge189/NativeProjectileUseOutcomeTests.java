package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileNativeUseResult;
import dev.ghostfusion.features.ProjectileNativeUseResult.Outcome;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * JDK8 original Minecraft 1.8.9 native-method admission, not a launched
 * game, packet capture, server acknowledgement or projectile hit.
 */
public final class NativeProjectileUseOutcomeTests {
    private static int checks;
    private static void ok(boolean v,String reason) {
        checks++;
        if(!v)throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Method use=PlayerControllerMP.class.getMethod(
            "sendUseItem",EntityPlayer.class,World.class,ItemStack.class);
        ok(Modifier.isPublic(use.getModifiers()) &&
            use.getReturnType()==Boolean.TYPE,
            "real native use returns boolean local inventory change, not packet ACK");
        Method spectator=PlayerControllerMP.class.getMethod("isSpectator");
        ok(Modifier.isPublic(spectator.getModifiers()) &&
            spectator.getReturnType()==Boolean.TYPE,
            "real vanilla spectator pre-use guard accessible");
        Method rod=ItemFishingRod.class.getMethod(
            "onItemRightClick",ItemStack.class,World.class,EntityPlayer.class);
        Method snow=ItemSnowball.class.getMethod(
            "onItemRightClick",ItemStack.class,World.class,EntityPlayer.class);
        ok(rod.getReturnType()==ItemStack.class &&
           snow.getReturnType()==ItemStack.class,
           "both original-game items return local stack, not throw success");
        ok(ProjectileNativeUseResult.classify(false,true,false)==Outcome.SUBMITTED_UNCHANGED,
           "rod local unchanged still submitted via original native method");
        ok(ProjectileNativeUseResult.classify(false,true,true)==Outcome.SUBMITTED_LOCAL_CHANGE,
           "depleted snowball item use has local mutation signal");
        ok(ProjectileNativeUseResult.classify(true,false,false)==Outcome.SPECTATOR_DENIED,
           "spectator has no approved native projectile submission");
        ok(checks>=6,"original Minecraft 1.8.9 native use API probe ran");
        System.out.println("NATIVE_PROJECTILE_USE_OUTCOME_PASS assertions="+checks);
    }
}
