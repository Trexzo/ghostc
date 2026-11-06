package dev.ghostfusion.tests;

import dev.ghostfusion.features.CombatFaultCleanup;
import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.89 real HostRuntime and coordinator acceptance: combat-only fallback
 * cancellation, independent peer callbacks, repeated blocked frames, and
 * exact native-identity recovery. No Minecraft/OpenGL/server assertions.
 */
public final class CombatFaultPeerContinuityTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void enqueue(SessionServices s) {
        final long t=s.tick(),e=s.generation();
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,20,t,2,e);
        s.actions().request("combat.triggerbot",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,10,t,2,e);
        s.actions().request("combat.blockhit",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,10,t,2,e);
        s.actions().request("inventory.autoarmor",ActionCoordinator.Type.HOTBAR_SELECT,
            ActionCoordinator.Phase.OUTPUT,1,10,t,2,e);
        s.rotations().request("combat.aimassist",RotationCoordinator.Scope.CAMERA,
            40f,5f,10,t,2,e);
        s.rotations().request("movement.bridgeassist",RotationCoordinator.Scope.OUTBOUND,
            10f,2f,10,t,2,e);
    }
    private static void unrelatedOnly(SessionServices s) {
        final long t=s.tick(),e=s.generation();
        ok(s.actions().pending()==1,"only noncombat inventory request survives");
        ok(s.rotations().pending()==1,"only noncombat rotation survives");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,t,e)==null,
            "no combat ATTACK replay after failed cleanup");
        ok(s.actions().consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,t,e)==null,
            "no combat USE replay after failed cleanup");
        ok(s.rotations().decide(RotationCoordinator.Scope.CAMERA,t,e,0f,0f).owner==null,
            "no stale Aim camera request");
        ActionCoordinator.Decision armor=s.actions().consume(
            ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.OUTPUT,t,e);
        ok(armor!=null&&"inventory.autoarmor".equals(armor.owner),
            "inventory hotbar request remains independent");
        ok("movement.bridgeassist".equals(s.rotations().decide(
            RotationCoordinator.Scope.OUTBOUND,t,e,0f,0f).owner),
            "unrelated movement rotation request survives");
        s.actions().cancelOwner("inventory.autoarmor");
        s.rotations().cancel("movement.bridgeassist");
    }
    private static void exact() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r389-"));
        h.worldPresence(true);
        h.modules().setEnabled("inventory.autoarmor",true);
        SessionServices s=h.services();
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Object world=new Object(),player=new Object();
        ok(fence.observe(s.generation(),world,player),"initial native identity");
        s.tickAdvance();
        enqueue(s);
        ok(s.actions().pending()==4 && s.rotations().pending()==2,
            "combat and peer requests actually coexist before fault");
        AtomicBoolean resetEntered=new AtomicBoolean();
        fence.quarantine();
        CombatFaultCleanup.contain(s,()->{
            resetEntered.set(true);
            throw new IllegalStateException("PRIVATE RESET FAILURE");
        });
        ok(resetEntered.get(),"faulty reset was attempted");
        unrelatedOnly(s);
        final int[] peer={0};
        ok(h.runFeature("inventory.autoarmor",()->peer[0]++),
            "real host inventory callback still runs after shared combat fault");
        ok(peer[0]==1 && h.featureEnabled("inventory.autoarmor"),
            "healthy inventory peer remains enabled");
        for(int i=0;i<120;i++) {
            s.tickAdvance();
            ok(!fence.observe(s.generation(),world,player),
                "blocked same-world frames cannot reenter synthetic combat");
            ok(h.runFeature("inventory.autoarmor",()->peer[0]++),
                "quarantine does not block healthy inventory work");
        }
        ok(peer[0]==121,"all unrelated callbacks completed while combat blocked");
        h.worldPresence(false);
        ok(!fence.observe(s.generation(),null,null),"null world never clears fault");
        h.worldPresence(true);
        ok(!fence.observe(s.generation(),world,player),
            "same-world reconnect cannot clear shared fault");
        h.worldPresence(false);
        h.worldPresence(true);
        world=new Object();
        ok(fence.observe(s.generation(),world,player),
            "changed native identity and newer generation recover combat");
        h.close();

        // Cleanup has a strict owner-thread boundary, even if reset is provided
        // by an untrusted callback. It cannot run another thread's reset.
        HostRuntime foreign=new HostRuntime(Files.createTempDirectory("ghostc-r389-owner-"));
        foreign.worldPresence(true);
        AtomicBoolean denied=new AtomicBoolean(),ran=new AtomicBoolean();
        Thread t=new Thread(()->{
            try {CombatFaultCleanup.contain(foreign.services(),()->ran.set(true));}
            catch(IllegalStateException expected){denied.set(true);}
        });
        t.start();t.join();
        ok(denied.get()&&!ran.get(),"foreign thread rejected before reset callback");
        foreign.close();
    }
    private static void randomized() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r389-matrix-"));
        h.worldPresence(true);
        h.modules().setEnabled("inventory.autoarmor",true);
        SessionServices s=h.services();
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Random r=new Random(3892026L);
        Object world=new Object(),player=new Object();
        long callbacks=0,skipped=0;
        for(int i=0;i<12000;i++) {
            if(i>0 && i%397==0) {
                h.worldPresence(false);
                ok(!fence.quarantined() || !fence.observe(s.generation(),null,null),
                    "absent world never clears shared uncertainty");
                h.worldPresence(true);
                if((i/397)%2==0)world=new Object();
            }
            s.tickAdvance();
            boolean admitted=fence.observe(s.generation(),world,player);
            if(admitted) {
                enqueue(s);
                if(r.nextInt(5)==0) {
                    fence.quarantine();
                    CombatFaultCleanup.contain(s,()->{
                        throw new IllegalStateException("PRIVATE FRAME RESET");
                    });
                    unrelatedOnly(s);
                } else {
                    // Successful synthetic frame: clear only this test's
                    // requests; it is not a claim of native game execution.
                    s.actions().cancelOwner("combat.clicker");
                    s.actions().cancelOwner("combat.triggerbot");
                    s.actions().cancelOwner("combat.blockhit");
                    s.actions().cancelOwner("inventory.autoarmor");
                    s.rotations().cancel("combat.aimassist");
                    s.rotations().cancel("movement.bridgeassist");
                }
            } else {
                skipped++;
                ok(fence.quarantined(),"blocked frame does not call combat/suspend");
            }
            final int[] next={0};
            ok(h.runFeature("inventory.autoarmor",()->next[0]++),
                "independent inventory callback remains enabled");
            callbacks+=next[0];
        }
        ok(callbacks==12000 && skipped>1000,
            "12k independent peer callbacks across quarantines");
        h.close();
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("COMBAT_FAULT_PEER_CONTINUITY_PASS assertions="+checks);
    }
}
