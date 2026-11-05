package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.CombatLifecycleFence;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.86: actual registry lifecycle revisions and session action fences.
 * Headless owner-thread policy evidence, not native gameplay or server ACK.
 */
public final class CombatLifecycleRevisionTests {
    private static long checks;
    private static final String[] IDS={
        "combat.aimassist","combat.clicker","combat.triggerbot",
        "combat.hitselect","combat.blockhit"
    };
    private static final int[] BITS={
        CombatLifecycleFence.AIM,CombatLifecycleFence.CLICK,
        CombatLifecycleFence.TRIGGER,CombatLifecycleFence.HIT,
        CombatLifecycleFence.BLOCK
    };
    private static void ok(boolean condition,String why) {
        checks++;
        if(!condition)throw new AssertionError(why);
    }
    private static long[] revisions(HostRuntime host) {
        long[] r=new long[5];
        for(int i=0;i<5;i++)
            r[i]=host.modules().get(IDS[i]).lifecycleRevision();
        return r;
    }
    private static int observe(CombatLifecycleFence f,long epoch,long[] r) {
        return f.observe(epoch,r[0],r[1],r[2],r[3],r[4]);
    }
    private static boolean denied(Runnable r) {
        try {r.run();return false;}
        catch(IllegalStateException ex){return true;}
    }
    private static void actualHost() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r386-lifecycle");
        HostRuntime h=new HostRuntime(dir);
        CombatLifecycleFence f=new CombatLifecycleFence();
        h.worldPresence(true);
        long gen=h.services().generation();
        ok(observe(f,gen,revisions(h))==CombatLifecycleFence.ALL,
            "first live session invalidates every stale combat policy");
        ok(observe(f,gen,revisions(h))==0,
            "unchanged module states never spuriously reset cooldowns");
        for(int i=0;i<5;i++) {
            long before=h.modules().get(IDS[i]).lifecycleRevision();
            h.uiActions().toggle(IDS[i]);
            long enabled=h.modules().get(IDS[i]).lifecycleRevision();
            ok(enabled==before+1 && h.featureEnabled(IDS[i]),
                "real GUI enable advances one lifecycle receipt "+IDS[i]);
            ok(observe(f,gen,revisions(h))==BITS[i],
                "exact changed module is independently invalidated "+IDS[i]);
        }
        // A pending Block Hit event must never survive a fast OFF->ON
        // cycle between END ticks, even if both observations see ENABLED.
        BlockHitPolicy block=new BlockHitPolicy();
        ok(!block.preview(true,true,false,true,true,false,true,
            2,0,"post_attack",0,gen),"genuine post-attack event arms");
        h.uiActions().toggle("combat.blockhit");
        h.uiActions().toggle("combat.blockhit");
        ok(h.featureEnabled("combat.blockhit"),
            "fast Block Hit OFF->ON ends with same enabled state");
        ok(observe(f,gen,revisions(h))==CombatLifecycleFence.BLOCK,
            "inter-tick Block Hit toggles are visible via revision");
        block.reset();
        ok(!block.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",1,gen),
            "reset prevents old post-attack USE on first new frame");
        ok(!block.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",2,gen),
            "stale event cannot reappear after a second frame");

        // TriggerBot READY and Hit Select prepared attack may not cross
        // toggles; no native pulse/cooldown is invented by the reset.
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID uuid=new UUID(386L,1);
        ok(!trigger.due(gen,0,true,uuid,1,5),"trigger dwell begins");
        ok(trigger.due(gen,1,true,uuid,1,5),"trigger obtains ready token");
        HitSelectPolicy hit=new HitSelectPolicy();
        ok(hit.preview(true,true,0,2,5,0,"normal",1,1,gen),
            "Hit Select holds a prepared native ATTACK preview");
        h.uiActions().toggle("combat.triggerbot");
        h.uiActions().toggle("combat.triggerbot");
        h.uiActions().toggle("combat.hitselect");
        h.uiActions().toggle("combat.hitselect");
        int flags=observe(f,gen,revisions(h));
        ok(flags==(CombatLifecycleFence.TRIGGER|CombatLifecycleFence.HIT),
            "two fast peer toggles invalidate exact independent policies");
        trigger.reset();hit.reset();
        ok(denied(()->trigger.dispatched(gen,1,uuid)),
            "stale TriggerBot READY can never commit after OFF->ON");
        ok(denied(()->hit.committed(1,gen)),
            "stale Hit Select preview can never claim native ATTACK receipt");
        ok(!trigger.due(gen,2,true,uuid,1,5),
            "re-enabled TriggerBot requires fresh native target acquisition");
        ok(hit.preview(true,true,0,2,5,0,"normal",1,2,gen),
            "re-enabled Hit Select has only new-session-local preview");

        // Actual FeatureFaultGuard quarantines one module via registry.
        long beforeFault=h.modules().get("combat.triggerbot").lifecycleRevision();
        ok(!h.runFeature("combat.triggerbot",()->{
            throw new IllegalArgumentException("private");
        }),"feature-fault isolation disables TriggerBot");
        ok(h.modules().get("combat.triggerbot").lifecycleRevision()>beforeFault,
            "fault quarantine increments lifecycle receipt");
        ok(observe(f,gen,revisions(h))==CombatLifecycleFence.TRIGGER,
            "other enabled combat modules unaffected by isolated fault");
        h.uiActions().toggle("combat.triggerbot"); // DISABLED -> ENABLED
        ok(h.featureEnabled("combat.triggerbot"),"explicit fault recovery allowed");
        ok(observe(f,gen,revisions(h))==CombatLifecycleFence.TRIGGER,
            "fault recovery cannot inherit prior TriggerBot target dwell");

        // These are real session-coordinator queues, not mock counters.
        SessionServices session=h.services();
        session.tickAdvance();
        long tick=session.tick();
        session.actions().request(IDS[1],ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,10,tick,2,gen);
        session.actions().request(IDS[4],ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,10,tick,2,gen);
        session.rotations().request(IDS[0],RotationCoordinator.Scope.CAMERA,
            20f,10f,10,tick,2,gen);
        ok(session.actions().pending()>=2 && session.rotations().pending()>0,
            "real pre-Panic attack/USE/camera requests are queued");
        int disabled=PanicController.disableAll(h);
        ok(disabled>=5,"real Panic disables all enabled combat modules");
        ok(session.actions().pending()==0 && session.rotations().pending()==0,
            "Panic clears coordinated pending native attack/USE/camera intent");
        ok(observe(f,gen,revisions(h))==CombatLifecycleFence.ALL,
            "Panic lifecycle is independently visible to all modules");
        ok(observe(f,gen,revisions(h))==0,
            "repeated stable Panic observation never grants a new action");

        h.worldPresence(false);
        long leave=session.generation();
        ok(leave>gen && !session.connected(),
            "world leave advances and disconnects session generation");
        ok(session.actions().pending()==0 && session.rotations().pending()==0,
            "world leave retains no old action or rotation requests");
        ok(observe(f,leave,revisions(h))==CombatLifecycleFence.ALL,
            "leave generation revokes old world combat policies");
        h.worldPresence(true);
        long reenter=session.generation();
        ok(reenter>leave && session.connected(),
            "reconnect creates another independent session generation");
        ok(observe(f,reenter,revisions(h))==CombatLifecycleFence.ALL,
            "return to world starts with all policies invalidated");
        ok(observe(f,reenter,revisions(h))==0,
            "new world can later retain fresh, unchanged state");
        h.close();
    }
    private static void deterministic() throws Exception {
        CombatLifecycleFence f=new CombatLifecycleFence();
        Random rng=new Random(3862026L);
        long[] rev={0,0,0,0,0},old=rev.clone();
        long epoch=1,lastEpoch=-1;
        long actualChanges=0,sameStateCycles=0,epochs=0;
        for(int i=0;i<100000;i++) {
            if(i%997==0){epoch++;epochs++;}
            int mode=rng.nextInt(8);
            if(mode<=4) {
                rev[mode]++;
                actualChanges++;
            } else if(mode==5) {
                int j=rng.nextInt(5);
                rev[j]+=2;
                actualChanges++;
                sameStateCycles++;
            } else if(mode==6) {
                for(int j=0;j<5;j++)rev[j]++;
                actualChanges+=5;
            }
            int expected=0;
            if(lastEpoch!=epoch)expected=CombatLifecycleFence.ALL;
            else for(int j=0;j<5;j++)if(rev[j]!=old[j])expected|=BITS[j];
            int result=observe(f,epoch,rev);
            ok(result==expected,"100k lifecycle change-set oracle "+i);
            ok(observe(f,epoch,rev)==0,
                "100k repeated native END frame never revalidates old transition "+i);
            lastEpoch=epoch;
            System.arraycopy(rev,0,old,0,5);
        }
        ok(epochs>90&&actualChanges>90000&&sameStateCycles>10000,
            "rapid toggles, Panic-like fanout and session epochs covered");

        final long stableEpoch=epoch;
        final long[] stableRevisions=rev.clone();
        AtomicBoolean rejected=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{f.observe(stableEpoch+1,stableRevisions[0],stableRevisions[1],
                stableRevisions[2],stableRevisions[3],stableRevisions[4]);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(rejected.get(),"network worker cannot inspect or mutate lifecycle ledger");
        f.reset();
        ok(observe(f,epoch+1,rev)==CombatLifecycleFence.ALL,
            "explicit full reset restarts independent native admission");
        ok(denied(()->f.observe(stableEpoch,stableRevisions[0],
            stableRevisions[1],stableRevisions[2],stableRevisions[3],
            stableRevisions[4])),
            "backward generation is rejected before policy mutation");
    }
    public static void main(String[] args) throws Exception {
        actualHost();deterministic();
        System.out.println("COMBAT_LIFECYCLE_REVISION_PASS assertions="+checks);
    }
}
