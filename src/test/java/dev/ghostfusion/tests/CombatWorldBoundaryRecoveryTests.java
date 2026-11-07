package dev.ghostfusion.tests;

import dev.ghostfusion.features.CombatWorldBoundaryRecovery;
import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.90 real session and owner-thread lifecycle reset fault isolation.
 * No native Minecraft world or server is launched by this test.
 */
public final class CombatWorldBoundaryRecoveryTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void enqueue(SessionServices s) {
        long e=s.generation(),t=s.tick();
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,10,t,2,e);
        s.actions().request("combat.blockhit",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,10,t,2,e);
        s.actions().request("inventory.autoarmor",ActionCoordinator.Type.HOTBAR_SELECT,
            ActionCoordinator.Phase.OUTPUT,2,10,t,2,e);
        s.rotations().request("combat.aimassist",RotationCoordinator.Scope.CAMERA,
            10f,2f,5,t,2,e);
        s.rotations().request("movement.bridgeassist",RotationCoordinator.Scope.OUTBOUND,
            5f,2f,5,t,2,e);
    }
    private static void peersRemain(SessionServices s) {
        long e=s.generation(),t=s.tick();
        ok(s.actions().pending()==1,"only peer action survives combat reset failure");
        ok(s.rotations().pending()==1,"only peer rotation survives combat reset failure");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,t,e)==null,
            "no synthetic ATTACK after lifecycle reset failure");
        ok(s.actions().consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,t,e)==null,
            "no synthetic USE after lifecycle reset failure");
        ActionCoordinator.Decision peer=s.actions().consume(
            ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.OUTPUT,t,e);
        ok(peer!=null&&"inventory.autoarmor".equals(peer.owner),
            "inventory peer retains its own intent");
        ok("movement.bridgeassist".equals(s.rotations().decide(
            RotationCoordinator.Scope.OUTBOUND,t,e,0f,0f).owner),
            "bridge movement rotation survives shared combat reset failure");
        ok(s.rotations().decide(RotationCoordinator.Scope.CAMERA,t,e,0f,0f).owner==null,
            "incomplete Aim Assist camera intent revoked");
        s.actions().cancelOwner("inventory.autoarmor");
        s.rotations().cancel("movement.bridgeassist");
    }
    private static void exact() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r390-"));
        h.worldPresence(true);
        h.modules().setEnabled("inventory.autoarmor",true);
        SessionServices s=h.services();
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        NativePulseSettlement pulse=new NativePulseSettlement();
        Object oldWorld=new Object(),world=new Object(),player=new Object();
        ok(fence.observe(s.generation(),oldWorld,player),"initial native world pinned");
        ok(pulse.reconcileNative(s.generation(),oldWorld,player),
            "native output has independent settlement");
        pulse.begin(Lane.ATTACK,s.generation(),0);
        pulse.nativeReturned(Lane.ATTACK,s.generation(),0);
        pulse.abandon(Lane.ATTACK,s.generation(),0);
        ok(pulse.uncertainMask()==1,"native ATTACK uncertainty starts");
        // SWITCH invokes worldPresence(false) before its first reset.
        h.worldPresence(false);
        final int[] calls={0},peerResets={0};
        boolean completed=CombatWorldBoundaryRecovery.reset(s,fence,
            world,player,()->{calls[0]++;throw new IllegalStateException("PRIVATE RESET");});
        peerResets[0]++; // independent bridge/velocity/inventory reset callbacks
        ok(!completed&&calls[0]==2&&peerResets[0]==1,
            "leave transition survives both failing combat resets");
        ok(fence.quarantined(),"failed reset quarantines failing native world");
        h.worldPresence(true);
        ok(!fence.observe(s.generation(),world,player),
            "ENTER of same already-faulting world does not instantly rearm");
        ok(CombatWorldBoundaryRecovery.reset(s,fence,world,player,()->{}),
            "subsequent successful reset does not itself throw");
        ok(fence.quarantined(),"success cannot erase prior shared fault");
        ok(pulse.reconcileNative(s.generation(),world,player),
            "native pulse has its own identity recovery");
        ok(pulse.uncertainMask()==0,
            "fresh new-world identity may independently recover native pulse");
        h.combatDispatchStatus(fence.quarantined());
        ok(h.combatDispatchWarning().contains("shared controller fault"),
            "shared fault warning remains visible without diagnostics");
        ok(h.nativePulseWarning().isEmpty(),
            "native warning not fabricated by shared fault");

        s.tickAdvance();
        enqueue(s);
        calls[0]=0;
        completed=CombatWorldBoundaryRecovery.reset(s,fence,world,player,()->{
            calls[0]++;throw new IllegalArgumentException("PRIVATE SECOND RESET");
        });
        ok(!completed&&calls[0]==2,"failed ENTER reset retried for cleanup");
        peersRemain(s);
        ok(h.runFeature("inventory.autoarmor",()->peerResets[0]++),
            "healthy inventory callback can continue after failed combat boundary");
        ok(peerResets[0]==2,"noncombat peer remained functional");

        h.worldPresence(false);
        ok(!CombatWorldBoundaryRecovery.reset(s,fence,null,null,()->{
            throw new IllegalStateException("PRIVATE NULL WORLD");
        }),"null-world leave reset failure contained");
        h.worldPresence(true);
        ok(!fence.observe(s.generation(),world,player),
            "same native objects and new generation cannot rearm after failed leave");
        h.worldPresence(false);
        h.worldPresence(true);
        Object recoveredWorld=new Object();
        ok(fence.observe(s.generation(),recoveredWorld,player),
            "only another newer generation plus changed world recovers frame");
        h.combatDispatchStatus(fence.quarantined());
        ok(h.combatDispatchWarning().isEmpty(),"recovery warning clears");
        ok(CombatWorldBoundaryRecovery.reset(s,fence,recoveredWorld,player,()->{}),
            "normal world enter reset succeeds");
        h.close();

        HostRuntime foreign=new HostRuntime(Files.createTempDirectory("ghostc-r390-owner-"));
        foreign.worldPresence(true);
        final SessionServices foreignSession=foreign.services();
        final CombatFrameFaultFence foreignFence=new CombatFrameFaultFence();
        AtomicBoolean rejected=new AtomicBoolean(),ran=new AtomicBoolean();
        Thread t=new Thread(()->{
            try {CombatWorldBoundaryRecovery.reset(foreignSession,foreignFence,
                new Object(),new Object(),()->ran.set(true));}
            catch(IllegalStateException expected) {rejected.set(true);}
        });
        t.start();t.join();
        ok(rejected.get()&&!ran.get(),"foreign thread cannot invoke native reset");
        foreign.close();

        // Serious JVM Errors are not regular frame faults and must propagate.
        HostRuntime fatal=new HostRuntime(Files.createTempDirectory("ghostc-r390-error-"));
        fatal.worldPresence(true);
        CombatFrameFaultFence fatalFence=new CombatFrameFaultFence();
        Object fatalWorld=new Object(),fatalPlayer=new Object();
        ok(fatalFence.observe(fatal.services().generation(),fatalWorld,fatalPlayer),
            "independent fatal test native identity");
        boolean propagated=false;
        try {CombatWorldBoundaryRecovery.reset(fatal.services(),fatalFence,
            fatalWorld,fatalPlayer,()->{throw new AssertionError("fatal");});}
        catch(AssertionError expected) {propagated=true;}
        ok(propagated&&!fatalFence.quarantined(),
            "serious Error is propagated, not swallowed as RuntimeException");
        fatal.close();
    }
    private static void randomized() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r390-random-"));
        h.worldPresence(true);
        SessionServices s=h.services();
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Object world=new Object(),player=new Object();
        Random r=new Random(3902026L);
        boolean blocked=false;
        int faults=0;
        for(int i=0;i<16000;i++) {
            if(i>0&&i%411==0) {
                h.worldPresence(false);
                h.worldPresence(true);
                if((i/411)%2==0) { world=new Object();blocked=false; }
            }
            s.tickAdvance();
            ok(fence.observe(s.generation(),world,player)==!blocked,
                "16k generation/identity admission oracle");
            if(!blocked && r.nextInt(11)==0) {
                final int[] invoked={0};
                boolean reset=CombatWorldBoundaryRecovery.reset(s,fence,world,player,()->{
                    invoked[0]++;throw new IllegalStateException("PRIVATE");
                });
                blocked=true;faults++;
                ok(!reset&&invoked[0]==2&&fence.quarantined(),
                    "16k thrown resets quarantined and contained");
            } else if(r.nextInt(7)==0) {
                ok(CombatWorldBoundaryRecovery.reset(s,fence,world,player,()->{}),
                    "16k healthy lifecycle reset succeeds");
            }
            ok(fence.quarantined()==blocked,
                "16k no silent same-world rearm");
        }
        ok(faults>10&&checks>=32000,
            "large deterministic lifecycle transition fault matrix");
        h.close();
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("COMBAT_WORLD_BOUNDARY_RECOVERY_PASS assertions="+checks);
    }
}
