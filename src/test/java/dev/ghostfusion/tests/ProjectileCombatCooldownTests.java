package dev.ghostfusion.tests;

import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.79: projectile hotbar ownership discards pending native combat intents
 * and target dwell but never erases already committed TriggerBot or shared
 * Hit Select cooldowns. Pure owner-thread policy, no Minecraft or server.
 */
public final class ProjectileCombatCooldownTests {
    private static long count;
    private static void ok(boolean pass,String detail) {
        count++;
        if(!pass)throw new AssertionError(detail);
    }
    private static boolean preview(HitSelectPolicy hit,long epoch,long tick,
                                   int gap) {
        return hit.preview(true,true,0,2,gap,0,"normal",1,tick,epoch);
    }
    private static void exact() throws Exception {
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        HitSelectPolicy hit=new HitSelectPolicy();
        UUID a=new UUID(379L,1L),b=new UUID(379L,2L);
        ok(!trigger.due(1,0,true,a,2,8),"normal initial dwell");
        ok(!trigger.due(1,1,true,a,2,8),"normal second dwell");
        ok(trigger.due(1,2,true,a,2,8),"first genuine target acquired");
        ok(preview(hit,1,2,10),"original shared Hit Select output is ready");
        trigger.dispatched(1,2,a);
        hit.committed(2,1);
        // Projectile transaction starts at tick 3, target is no longer
        // allowed to inherit any earlier acquisition or pending preview.
        ok(preview(hit,1,3,10)==false,"native Hit Select cooldown precludes old pending intent");
        trigger.invalidateCandidate();
        hit.cancelPrepared();
        boolean rejected=false;
        try {hit.committed(3,1);}
        catch(IllegalStateException expected){rejected=true;}
        ok(rejected,"no pending hit can commit during projectile ownership");
        for(long t=4;t<=5;t++) {
            ok(ProjectileCombatHandoff.reserved(
                ProjectileOwnerArbiter.Owner.ROD,false,false),
                "AutoRod hotbar owner remains exclusive for original frame");
            trigger.invalidateCandidate();
            hit.cancelPrepared();
        }
        ok(!trigger.due(1,6,true,b,2,8),"post-rod target requires new dwell");
        ok(!trigger.due(1,7,true,b,2,8),"second post-rod dwell tick");
        ok(!trigger.due(1,8,true,b,2,8),
            "already committed trigger cooldown survives projectile and target switch");
        ok(!trigger.due(1,9,true,b,2,8),"cannot preempt original trigger cooldown");
        ok(trigger.due(1,10,true,b,2,8),"original trigger cooldown boundary");
        ok(!preview(hit,1,10,10),"original shared gap remains authoritative");
        ok(trigger.due(1,11,true,b,2,8),"native dwell stays ready without new dispatch");
        ok(!preview(hit,1,11,10),"shared Hit Select still within gap");
        ok(trigger.due(1,12,true,b,2,8),"trigger may propose at shared gap boundary");
        ok(preview(hit,1,12,10),"shared gap boundary restores permission");
        hit.committed(12,1);
        trigger.dispatched(1,12,b);
        ok(!trigger.due(1,13,true,b,2,8),"second native dispatch reenters cooldown");
        ok(!preview(hit,1,13,10),"shared gap also reenters cooldown");
        // Only an explicit world/focus/Panic reset forgets completed receipts.
        trigger.reset();hit.reset();
        ok(!trigger.due(2,0,true,a,2,8),
            "new world still requires fresh acquisition, never instant dispatch");
        ok(!trigger.due(2,1,true,a,2,8),"new world second dwell");
        ok(trigger.due(2,2,true,a,2,8),"new world has independent cooldown");
        ok(preview(hit,2,2,10),"new world resets prior shared hit interval");
        hit.cancelPrepared();
        trigger.invalidateCandidate();
        ok(!trigger.due(2,3,true,a,2,8),
            "projectile cannot retain native candidate even if undispatched");
        AtomicBoolean foreignDenied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try{trigger.invalidateCandidate();}
            catch(IllegalStateException expected){foreignDenied.set(true);}
        });
        worker.start();worker.join();
        ok(foreignDenied.get(),"projectile handoff is strictly owner-thread");
    }
    private static void replay() {
        Random rng=new Random(3792026L);
        final UUID a=new UUID(379L,10L),b=new UUID(379L,20L);
        for(int i=0;i<40000;i++) {
            final long epoch=i+20L;
            final int acquire=1+rng.nextInt(6);
            final int cooldown=1+rng.nextInt(40);
            final int gap=1+rng.nextInt(20);
            final int paused=1+rng.nextInt(5);
            TriggerBotPolicy trigger=new TriggerBotPolicy();
            HitSelectPolicy hit=new HitSelectPolicy();
            ok(!trigger.due(epoch,0,true,a,acquire,cooldown),
                "40k baseline starts unready "+i);
            for(int t=1;t<=acquire;t++) {
                boolean due=trigger.due(epoch,t,true,a,acquire,cooldown);
                ok(due==(t==acquire),"40k native pre-projectile acquisition "+i);
            }
            ok(preview(hit,epoch,acquire,gap),"40k initial shared hit available "+i);
            hit.committed(acquire,epoch);
            trigger.dispatched(epoch,acquire,a);
            for(int held=1;held<=paused;held++) {
                boolean rod=rng.nextBoolean();
                ok(ProjectileCombatHandoff.reserved(
                    rod?ProjectileOwnerArbiter.Owner.ROD:
                        ProjectileOwnerArbiter.Owner.SNOWBALL,false,false),
                    "40k exclusive projectile ownership "+i);
                trigger.invalidateCandidate();
                hit.cancelPrepared();
            }
            final int firstFree=acquire+paused+1;
            final int firstAllowed=Math.max(
                Math.max(firstFree+acquire,acquire+cooldown),
                acquire+gap);
            int actualFirst=-1;
            for(int t=firstFree;t<=firstAllowed;t++) {
                boolean ready=trigger.due(epoch,t,true,b,acquire,cooldown);
                boolean hitReady=ready&&preview(hit,epoch,t,gap);
                boolean expected=t==firstAllowed;
                ok(hitReady==expected,
                    "40k independent post-projectile dwell+two-cooldown oracle "+i+"/"+t);
                if(hitReady) {
                    actualFirst=t;
                    hit.committed(t,epoch);
                    trigger.dispatched(epoch,t,b);
                }
            }
            ok(actualFirst==firstAllowed,
                "40k no cooldown laundering through projectile hotbar "+i);
            ok(trigger.due(epoch,firstAllowed+1,true,b,acquire,cooldown)==
                (cooldown==1),
                "40k post-dispatch cooldown honors exact configured boundary "+i);
        }
        ok(count>300000,"seeded projectile cooldown trace executed");
    }
    public static void main(String[] args) throws Exception {
        exact();replay();
        System.out.println("PROJECTILE_COMBAT_COOLDOWN_PASS assertions="+count);
    }
}
