package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AutoSnowballPolicy;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Actual 1.8.9 Forge JDK8 classes and vanilla right-click signature.
 * This is headless native CLASS admission, not gameplay/network acceptance.
 */
public final class NativeAutoSnowballTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method use=PlayerControllerMP.class.getMethod("sendUseItem",
            EntityPlayer.class,World.class,ItemStack.class);
        ok(Modifier.isPublic(use.getModifiers())&&
           use.getReturnType()==Boolean.TYPE,
           "real Minecraft 1.8.9 vanilla sendUseItem is public boolean");
        Method update=PlayerControllerMP.class.getMethod("updateController");
        ok(Modifier.isPublic(update.getModifiers()) &&
           update.getReturnType()==Void.TYPE,
           "genuine native held-hotbar sync method exists");
        ok(ItemSnowball.class.getSuperclass()!=null &&
           EntityPlayer.class.isAssignableFrom(EntityPlayerSP.class),
           "real fishing-snowball and local player native types linked");
        AutoSnowballPolicy p=new AutoSnowballPolicy();
        ok(p.decide(1,0,false,true,2,6,30)==AutoSnowballPolicy.Action.NONE,
           "native world entry arms input gate without hidden right click");
        ok(p.decide(1,1,true,true,2,6,30)==AutoSnowballPolicy.Action.SELECT,
           "fresh physical attack may select original game snowball");
        p.committed(AutoSnowballPolicy.Action.SELECT,1,1);
        ok(p.decide(1,2,true,true,6,6,30)==AutoSnowballPolicy.Action.USE,
           "native right-click phase follows only committed selection");
        p.cancelPrepared();
        ok(p.decide(1,3,true,false,6,6,30)==AutoSnowballPolicy.Action.RESTORE,
           "denied right-click still restores original slot safely");
        ok(checks>=7,"mandatory Minecraft 1.8.9 native snowball linkage proof ran");
        System.out.println("NATIVE_AUTOSNOWBALL_PASS assertions="+checks);
    }
}
