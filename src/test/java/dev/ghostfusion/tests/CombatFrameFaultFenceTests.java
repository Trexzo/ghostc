package dev.ghostfusion.tests;

import dev.ghostfusion.features.CombatFrameFaultFence;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.88 owner-thread shared-frame fault fence, real HostRuntime warnings and
 * independent native-pulse quarantine. No Minecraft game/packet is simulated.
 */
public final class CombatFrameFaultFenceTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static boolean denied(Runnable r) {
        try { r.run();return false; }
        catch(RuntimeException expected) { return true; }
    }
    private static void exact() throws Exception {
        HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-r388-"));
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        NativePulseSettlement pulse=new NativePulseSettlement();
        host.worldPresence(true);
        Object world=new Object(),player=new Object();
        long epoch=host.services().generation();
        ok(fence.observe(epoch,world,player),"initial native identity");
        ok(pulse.reconcileNative(epoch,world,player),"native pulse independently pinned");
        ok(host.combatDispatchWarning().isEmpty(),"healthy combat has no warning");
        fence.quarantine();
        host.combatDispatchStatus(fence.quarantined());
        ok(!fence.observe(epoch,world,player),"same-frame fault blocks admission");
        ok(host.combatDispatchWarning().contains("shared controller fault"),
            "user receives stable warning without diagnostics");
        ok(host.diagnostics().count(Counter.COMBAT_DISPATCH_FAULT)==0,
            "no diagnostic collection without opt-in");
        host.modules().setEnabled("hud.diagnostics",true);
        host.combatDispatchStatus(true);
        ok(host.diagnostics().count(Counter.COMBAT_DISPATCH_FAULT)==0,
            "no synthetic backfilled fault when user opts in");
        // Panic and a module off/on toggle cannot clear a shared dispatch fault.
        host.modules().setEnabled("combat.clicker",true);
        PanicController.disableAll(host);
        host.modules().setEnabled("combat.clicker",true);
        ok(!fence.observe(epoch,world,player),"toggle/Panic cannot rearm");
        // Prove ATTACK native output remains completely independent.
        pulse.begin(Lane.ATTACK,epoch,1);
        pulse.nativeReturned(Lane.ATTACK,epoch,1);
        pulse.abandon(Lane.ATTACK,epoch,1);
        host.nativePulseStatus(pulse.uncertainMask());
        ok(pulse.uncertainMask()==1 && fence.quarantined(),
            "shared and native quarantine are separate latches");
        ok(host.nativePulseWarning().contains("ATTACK paused"),
            "native uncertainty remains visible with shared-frame fault");

        host.worldPresence(false);
        epoch=host.services().generation();
        ok(!fence.observe(epoch,null,null),"null world cannot clear a fault");
        host.worldPresence(true);
        epoch=host.services().generation();
        ok(!fence.observe(epoch,world,player),"same native objects block reconnect rearm");
        ok(!pulse.admitNative(Lane.ATTACK,epoch,world,player),
            "same-world reconnect cannot rearm uncertain ATTACK");
        ok(!fence.observe(epoch,new Object(),player),
            "same-generation native replacement cannot clear fault");

        host.worldPresence(false);
        host.worldPresence(true);
        epoch=host.services().generation();
        world=new Object();
        ok(fence.observe(epoch,world,player),"new epoch and world identity recover dispatch");
        ok(pulse.reconcileNative(epoch,world,player),"native pulse recovers independently");
        host.combatDispatchStatus(fence.quarantined());
        host.nativePulseStatus(pulse.uncertainMask());
        ok(host.combatDispatchWarning().isEmpty() && host.nativePulseWarning().isEmpty(),
            "only identity-verified recovery clears both warnings");
        ok(host.diagnostics().count(Counter.COMBAT_DISPATCH_RECOVERED)==1,
            "opt-in recovery counts once");
        // New fault while opted in has exactly one transition count.
        fence.quarantine();
        for(int i=0;i<200;i++)host.combatDispatchStatus(fence.quarantined());
        ok(host.diagnostics().count(Counter.COMBAT_DISPATCH_FAULT)==1,
            "blocked frames do not flood telemetry");
        ok(denied(()->fence.observe(-1,world,player)),
            "negative epoch rejected");
        AtomicBoolean rejected=new AtomicBoolean();
        Thread t=new Thread(()->{
            try {fence.quarantine();}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        t.start();t.join();
        ok(rejected.get(),"foreign thread cannot alter dispatch admission");
        rejected.set(false);
        Thread report=new Thread(()->{
            try {host.combatDispatchStatus(false);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        report.start();report.join();
        ok(rejected.get(),"foreign thread cannot clear warning");
        host.close();

        // A feature callback RuntimeException is ALREADY isolated to one module,
        // rather than being incorrectly classified as a shared-frame failure.
        HostRuntime peerHost=new HostRuntime(Files.createTempDirectory("ghostc-r388-peer-"));
        peerHost.worldPresence(true);
        peerHost.modules().setEnabled("combat.clicker",true);
        peerHost.modules().setEnabled("combat.blockhit",true);
        CombatFrameFaultFence peerFence=new CombatFrameFaultFence();
        ok(peerFence.observe(peerHost.services().generation(),new Object(),new Object()),
            "peer fence initially clear");
        ok(!peerHost.runFeature("combat.clicker",()->{
            throw new IllegalStateException("PRIVATE FEATURE MESSAGE");
        }),"individual feature fault contained by existing guard");
        ok(!peerHost.featureEnabled("combat.clicker") &&
            peerHost.featureEnabled("combat.blockhit") &&
            !peerFence.quarantined(),"healthy peer remains enabled");
        ok(!peerHost.faultWarning().contains("PRIVATE FEATURE MESSAGE"),
            "private exception text remains excluded");
        peerHost.close();
    }
    private static void randomized() throws Exception {
        HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-r388-random-"));
        host.worldPresence(true);
        CombatFrameFaultFence fence=new CombatFrameFaultFence();
        Random r=new Random(3882026L);
        Object world=new Object(),player=new Object();
        long epoch=host.services().generation();
        boolean blocked=false;
        for(int i=0;i<30000;i++) {
            if(i>0 && i%523==0) {
                host.worldPresence(false);
                long leave=host.services().generation();
                ok(fence.observe(leave,null,null)==!blocked,
                    "disconnect cannot recover frame fault");
                host.worldPresence(true);
                epoch=host.services().generation();
                if((i/523)%2==0) {
                    world=new Object();
                    blocked=false;
                }
            }
            ok(fence.observe(epoch,world,player)==!blocked,
                "native identity recovery requires changed object plus epoch");
            if(!blocked && r.nextInt(23)==0) {
                fence.quarantine();
                blocked=true;
            }
            host.combatDispatchStatus(fence.quarantined());
            ok(fence.quarantined()==blocked,"30k quarantine state oracle");
            ok(host.combatDispatchWarning().isEmpty()==!blocked,
                "30k HUD warning matches frame admission");
            ok(fence.observe(epoch,world,player)==!blocked,
                "30k same-object repeat cannot rearm");
        }
        host.close();
        ok(checks>=90000,"deterministic shared exception recovery coverage");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("COMBAT_FRAME_FAULT_FENCE_PASS assertions="+checks);
    }
}
