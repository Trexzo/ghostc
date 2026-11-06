package dev.ghostfusion.forge189;

import dev.ghostfusion.features.CombatFaultCleanup;
import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.lang.reflect.Method;
import net.minecraft.client.settings.KeyBinding;

/** Original Minecraft 1.8.9 Java8 linkage; no game/world is launched. */
public final class NativeCombatFaultPeerTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        Method cleanup=CombatFaultCleanup.class.getMethod("contain",
            SessionServices.class,Runnable.class);
        ok(cleanup.getReturnType()==Void.TYPE,"fallback cleanup links in native Forge build");
        Method tick=ForgeCombatController.class.getDeclaredMethod("suspendForProjectile");
        ok(tick.getReturnType()==Void.TYPE,"original projectile suspend path still linked");

        final int manualKey=238;
        KeyBinding manual=new KeyBinding("key.ghostc.r389.manual",manualKey,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        KeyBinding.onTick(manualKey);
        SessionServices s=new SessionServices();
        s.world(true);
        s.tickAdvance();
        long epoch=s.generation(),step=s.tick();
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,20,step,1,epoch);
        s.actions().request("inventory.autoarmor",ActionCoordinator.Type.HOTBAR_SELECT,
            ActionCoordinator.Phase.OUTPUT,1,10,step,1,epoch);
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Object world=new Object(),player=new Object();
        ok(fence.observe(epoch,world,player),"native world identity pinned");
        fence.quarantine();
        CombatFaultCleanup.contain(s,()->{
            throw new IllegalStateException("private reset failure");
        });
        ok(!fence.observe(epoch,world,player),"blocked native frame is not readmitted");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,step,epoch)==null,
            "unsettled synthetic attack request revoked");
        ActionCoordinator.Decision peer=s.actions().consume(
            ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.OUTPUT,step,epoch);
        ok(peer!=null&&"inventory.autoarmor".equals(peer.owner),
            "independent inventory intent survives");
        ok(manual.isPressed()&&!manual.isPressed(),
            "unrelated original Minecraft manual KeyBinding remains untouched");
        ok(!fence.observe(epoch+1,world,player),
            "same-world generation cannot recover after exception");
        ok(fence.observe(epoch+2,new Object(),player),
            "new native identity plus generation recovers");
        ok(checks>=9,"Java8 native peer-continuity boundary exercised");
        System.out.println("NATIVE_COMBAT_FAULT_PEER_PASS assertions="+checks);
    }
}
