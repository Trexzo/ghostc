package dev.ghostfusion.tests;

import dev.ghostfusion.features.CombatFrameAcceptance;
import dev.ghostfusion.features.CombatFrameAcceptance.Result;
import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.FixedClickScheduler;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime evidence is NOT a server ACK: forge-key pulses and physical attacks
 * are independent. Covers exact same-END-tick writes, owner conflicts, manual
 * overrides, post-projectile rearm, GUI/world session reset.
 */
public final class CombatFrameAcceptanceTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;
        if(!v)throw new AssertionError(why);
    }
    private static void reject(Runnable r,String why) {
        boolean bad=false;
        try{r.run();}catch(RuntimeException expected){bad=true;}
        ok(bad,why);
    }
    private static Result f(CombatFrameAcceptance a,long e,long t,
                            boolean plan,boolean rod,boolean snow,
                            boolean manual,boolean conflict,boolean ran,
                            boolean write,boolean attack) {
        return a.observe(e,t,plan,rod,snow,manual,conflict,ran,write,attack);
    }
    private static void basics() throws Exception {
        CombatFrameAcceptance a=new CombatFrameAcceptance();
        ok(f(a,1,0,false,false,false,false,false,false,false,false)==Result.INACTIVE,
           "no enabled combat cannot claim native combat activity");
        ok(f(a,1,1,true,false,false,false,false,false,false,false)==Result.COMBAT_SKIPPED,
           "enabled but unexecuted frame cannot claim native action");
        ok(f(a,1,2,true,false,false,false,false,true,true,true)==Result.COMBAT_RAN,
           "genuine local key pulse is recorded as combat running, not a hit");
        ok(f(a,1,3,true,true,false,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "rod SELECT/USE/RESTORE is exclusively owned");
        ok(f(a,1,4,true,true,false,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "projectile ownership persists across client END ticks");
        ok(f(a,1,5,true,false,false,false,false,true,false,false)==Result.POST_PROJECTILE_REARM,
           "combat comes back after rod restores, with no buffered native attack");
        ok(f(a,1,6,true,false,false,false,false,true,true,true)==Result.COMBAT_RAN,
           "later fresh actual physical rearm may dispatch a local key pulse");
        ok(f(a,1,7,true,false,true,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "snowball owns the native hotbar");
        ok(f(a,1,8,true,false,false,false,false,true,true,true)==Result.UNEXPECTED_NATIVE_WRITE,
           "synthetic attack on first rearm frame is flagged");
        ok(f(a,1,9,true,false,false,false,false,true,false,false)==Result.COMBAT_RAN,
           "first-frame violation never survives as an authorization");
        ok(f(a,1,10,true,false,false,true,false,false,false,false)==Result.MANUAL_OVERRIDE,
           "human manual selection suppresses optional combat");
        ok(f(a,1,11,true,true,true,false,true,false,false,false)==Result.OWNER_CONFLICT,
           "two projectile owners are a conflict, not a successful cast");
        ok(f(a,1,12,true,false,false,false,true,false,false,false)==Result.OWNER_CONFLICT,
           "owner conflict is fail-closed even without selected item");
        ok(f(a,1,13,true,true,false,false,false,true,false,false)==Result.UNEXPECTED_NATIVE_WRITE,
           "combat controller illegally running while rod owns slot is caught");
        ok(f(a,1,14,true,false,true,false,false,false,true,false)==Result.UNEXPECTED_NATIVE_WRITE,
           "native Aim/Block write while snowball owns slot is caught");
        ok(f(a,1,15,true,false,false,true,false,false,true,false)==Result.UNEXPECTED_NATIVE_WRITE,
           "manual override must not be accompanied by a native camera/use write");
        ok(f(a,1,16,true,false,false,false,true,false,true,true)==Result.UNEXPECTED_NATIVE_WRITE,
           "owner conflict must not coincide with a synthetic attack");
        ok(f(a,1,17,false,false,false,false,false,false,true,false)==Result.UNEXPECTED_NATIVE_WRITE,
           "unowned native write without a combat controller is flagged");
        ok(f(a,2,0,true,false,false,false,false,true,false,false)==Result.COMBAT_RAN,
           "new world generation resets old projectile handoff");
        reject(()->f(a,2,0,true,false,false,false,false,true,false,false),
               "same client END tick cannot be double counted");
        reject(()->f(a,2,-1,true,false,false,false,false,true,false,false),
               "negative clock cannot enter acceptance ledger");
        reject(()->f(a,2,1,true,false,false,false,false,true,false,true),
               "synthetic write cannot exist without corresponding native write");
        a.reset();
        ok(f(a,2,1,true,false,false,false,false,true,false,false)==Result.COMBAT_RAN,
           "opt-in diagnostics toggling starts a fresh frame");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{a.reset();}catch(IllegalStateException expected){blocked.set(true);}
        });foreign.start();foreign.join();
        ok(blocked.get(),"foreign thread cannot manipulate runtime evidence");
    }
    private static void realPolicyHandoff() {
        CombatFrameAcceptance a=new CombatFrameAcceptance();
        AutoRodPolicy rod=new AutoRodPolicy();
        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        PhysicalInputGate input=new PhysicalInputGate();
        FixedClickScheduler clicks=new FixedClickScheduler();
        long gen=13;
        rod.decide(gen,0,false,true,2,6,30);
        snow.decide(gen,0,false,true,2,5,20);
        input.allowed(gen,true,true,false,false,-100,-99);
        ok(rod.decide(gen,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
           "native rod select on physical attack edge");
        rod.committed(AutoRodPolicy.Action.SELECT,gen,1);
        ok(f(a,gen,1,true,true,false,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "select frame forbids Clicker/Trigger/BlockHit/Aim write");
        ok(rod.decide(gen,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
           "rod USE on exact next END tick");
        rod.committed(AutoRodPolicy.Action.USE,gen,2);
        ok(f(a,gen,2,true,true,false,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "real native use cannot additionally permit optional combat");
        ok(rod.decide(gen,3,true,true,6,6,30)==AutoRodPolicy.Action.RESTORE,
           "rod restores owned native slot");
        rod.committed(AutoRodPolicy.Action.RESTORE,gen,3);
        ok(f(a,gen,3,true,true,false,false,false,false,false,false)==Result.PROJECTILE_EXCLUSIVE,
           "RESTORE tick is still exclusive even after slot released");
        input.reset();clicks.reset();
        ok(!input.allowed(gen,true,true,false,true,-100,-99),
           "held attack cannot be inherited after combat.reset");
        ok(f(a,gen,4,true,false,false,false,false,true,false,false)==Result.POST_PROJECTILE_REARM,
           "first combat tick after restore has no synthetic click");
        input.allowed(gen,true,true,false,false,-100,-99);
        ok(input.allowed(gen,true,true,false,true,-100,-99),
           "real release followed by actual press re-arms combat");
        ok(!clicks.due(1_000_000L,true,true,10.0),
           "click scheduler first rearmed frame never catch-up bursts");
        ok(f(a,gen,5,true,false,false,false,false,true,false,false)==Result.COMBAT_RAN,
           "new press may begin scheduling, not immediate queued attack");
        ok(snow.decide(gen,6,false,true,2,5,20)==AutoSnowballPolicy.Action.SELECT ||
           !snow.ownsHotbar(),
           "different projectile policy remains independently gated after rod");
    }
    private static void randomized() {
        CombatFrameAcceptance a=new CombatFrameAcceptance();
        Random rng=new Random(365189L);
        long violations=0,exclusive=0,manualFrames=0,conflicts=0,rearmed=0;
        long epoch=5,tick=0;
        boolean priorProjectile=false;
        for(int i=0;i<80000;i++) {
            if(i%1503==0){epoch++;tick=0;priorProjectile=false;a.reset();}
            boolean rod=rng.nextInt(13)==0;
            boolean snow=rng.nextInt(14)==0;
            boolean manual=rng.nextInt(17)==0;
            boolean conflict=rng.nextInt(79)==0;
            boolean plan=rng.nextBoolean();
            boolean ran=plan&&!rod&&!snow&&!manual&&!conflict&&rng.nextInt(10)!=0;
            boolean write=rng.nextInt(11)==0;
            boolean attack=write&&rng.nextBoolean();
            boolean blocked=rod||snow||manual||conflict;
            boolean bad=(blocked&&(ran||write))||(!ran&&write)||
                (priorProjectile&&attack);
            Result expected=bad?Result.UNEXPECTED_NATIVE_WRITE:
                conflict?Result.OWNER_CONFLICT:
                manual?Result.MANUAL_OVERRIDE:
                (rod||snow)?Result.PROJECTILE_EXCLUSIVE:
                ran&&priorProjectile?Result.POST_PROJECTILE_REARM:
                ran?Result.COMBAT_RAN:plan?Result.COMBAT_SKIPPED:Result.INACTIVE;
            Result got=f(a,epoch,tick++,plan,rod,snow,manual,conflict,ran,write,attack);
            ok(got==expected,"80k native frame grant/write/owner oracle "+i);
            priorProjectile=rod||snow;
            if(got==Result.UNEXPECTED_NATIVE_WRITE)violations++;
            if(got==Result.PROJECTILE_EXCLUSIVE)exclusive++;
            if(got==Result.MANUAL_OVERRIDE)manualFrames++;
            if(got==Result.OWNER_CONFLICT)conflicts++;
            if(got==Result.POST_PROJECTILE_REARM)rearmed++;
        }
        ok(violations>6000&&exclusive>5000&&manualFrames>1000&&conflicts>100,
           "80k includes suppressed and illegally written frames");
        ok(rearmed>300,"80k includes handoff after projectile release");
        ok(checks>80000,"80k cases exercised");
    }
    public static void main(String[] args) throws Exception {
        basics();realPolicyHandoff();randomized();
        System.out.println("COMBAT_FRAME_ACCEPTANCE_PASS assertions="+checks);
    }
}
