package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.features.CombatWorldBoundaryRecovery;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;

/**
 * Original Minecraft 1.8.9 Java8 linkage. No native game launch, real world
 * object, OpenGL context or server acknowledgment is involved.
 */
public final class NativeCombatWorldBoundaryRecoveryTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method reset=ForgeEventAdapter.class.getDeclaredMethod(
            "resetCombatAtWorldBoundary");
        ok(reset.getReturnType()==Void.TYPE,
            "real Forge adapter exposes isolated lifecycle combat reset");
        Method input=KeyBinding.class.getMethod("onTick",Integer.TYPE);
        ok(input.getReturnType()==Void.TYPE,
            "original Minecraft 1.8.9 native key callback linked");

        final int manualKey=241;
        KeyBinding manual=new KeyBinding("key.ghostc.r390.manual",manualKey,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        KeyBinding.onTick(manualKey);

        SessionServices s=new SessionServices();
        s.world(true);
        s.tickAdvance();
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Object original=new Object(),faultingWorld=new Object(),player=new Object();
        ok(fence.observe(s.generation(),original,player),"original native context");
        s.world(false); // exact boundary progression on SWITCH
        s.world(true);
        final int[] resets={0};
        long tick=s.tick(),epoch=s.generation();
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,10,tick,1,epoch);
        s.actions().request("inventory.autoarmor",ActionCoordinator.Type.HOTBAR_SELECT,
            ActionCoordinator.Phase.OUTPUT,2,10,tick,1,epoch);
        boolean completed=CombatWorldBoundaryRecovery.reset(s,fence,faultingWorld,player,
            ()->{resets[0]++;throw new IllegalStateException("private");});
        ok(!completed&&resets[0]==2,"failed native boundary reset contained");
        ok(fence.quarantined() &&
           !fence.observe(s.generation(),faultingWorld,player),
           "faulting native identity pinned and combat blocked");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,tick,epoch)==null,
            "pending synthetic combat input canceled");
        ActionCoordinator.Decision peer=s.actions().consume(
            ActionCoordinator.Type.HOTBAR_SELECT,
            ActionCoordinator.Phase.OUTPUT,tick,epoch);
        ok(peer!=null&&"inventory.autoarmor".equals(peer.owner),
            "independent inventory intent preserved");
        ok(manual.isPressed()&&!manual.isPressed(),
            "physical native Minecraft manual input unchanged");
        s.world(false);
        s.world(true);
        ok(!fence.observe(s.generation(),faultingWorld,player),
            "same native world cannot recover through reconnect");
        s.world(false);
        s.world(true);
        ok(fence.observe(s.generation(),new Object(),player),
            "only changed native identity plus new epoch recovers");
        ok(!manual.isPressed(),"reset never injected a second native key");
        ok(checks>=12,"Java8 native lifecycle reset probe executed");
        System.out.println("NATIVE_COMBAT_WORLD_BOUNDARY_RECOVERY_PASS assertions="+checks);
    }
}
