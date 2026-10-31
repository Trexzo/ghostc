package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import net.minecraft.client.settings.KeyBinding;

/** Original Minecraft 1.8.9 KeyBinding pulse with actual staged Java8 policy. */
public final class NativeBlockHitCommitTests {
    private static int checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        final int useCode=231;
        KeyBinding use=new KeyBinding("key.ghostc.r371.use",useCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        BlockHitPolicy policy=new BlockHitPolicy();
        ok(!policy.preview(true,true,false,true,true,true,false,
                2,0,"interval",0,3),"vanilla initial hold");
        ok(policy.preview(true,true,false,true,true,true,false,
                2,0,"interval",2,3),"native USE can be staged");
        policy.cancelPrepared();
        ok(!use.isPressed(),"veto emits no real Minecraft USE pulse");
        ok(policy.preview(true,true,false,true,true,true,false,
                2,0,"interval",3,3),"cancelled native USE retains original due time");
        KeyBinding.onTick(useCode);
        policy.committed(3,3);
        ok(use.isPressed()&&!use.isPressed(),
            "real native KeyBinding pulse after staging and commit");
        ok(!policy.preview(true,true,false,true,true,true,false,
                2,0,"interval",4,3),
            "native committed USE owns interval cooldown");
        policy.cancelPrepared();
        ok(checks>=5,"native Java8 Block Hit lifecycle checked");
        System.out.println("NATIVE_BLOCKHIT_COMMIT_PASS assertions="+checks);
    }
}
