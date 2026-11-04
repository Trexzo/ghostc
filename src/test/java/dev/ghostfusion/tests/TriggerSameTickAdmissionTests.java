package dev.ghostfusion.tests;

import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.TriggerNativeIdentityFence;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.ActionCoordinator.Phase;
import dev.ghostfusion.services.ActionCoordinator.Type;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.80 same-tick TriggerBot / native target event arbitration. An unchanged
 * duplicate cannot produce another ATTACK intent, and a changed target or
 * manual hold revokes the first pending candidate. Only genuinely committed
 * native output owns cooldown. All probes are offline and owner-thread only.
 */
public final class TriggerSameTickAdmissionTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    private static boolean denied(Runnable action) {
        try {action.run();return false;}
        catch(IllegalStateException expected){return true;}
    }

    private static void exact() throws Exception {
        UUID a=new UUID(380L,1L),b=new UUID(380L,2L);
        Object world=new Object(),entity=new Object(),replacement=new Object();
        TriggerBotPolicy p=new TriggerBotPolicy();
        TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
        ok(!fence.observe(1,0,true,a,27,entity,world),
            "new candidate has no preceding replacement");
        ok(!p.due(1,0,true,a,2,8),"dwell must seed on first tick");
        ok(!fence.observe(1,1,true,a,27,entity,world) &&
           !p.due(1,1,true,a,2,8),"second native tick only acquires");
        ok(!fence.observe(1,2,true,a,27,entity,world),
            "exact native identity retained at ready tick");
        ok(p.due(1,2,true,a,2,8),"real stable target becomes ready");
        ok(!fence.observe(1,2,true,a,27,entity,world) &&
           fence.permits(1,2,a,27,entity,world),
            "identical native duplicate is read-only");
        ok(!p.due(1,2,true,a,2,8),
            "same tick cannot issue second TriggerBot attack request");
        p.dispatched(1,2,a);
        ok(denied(()->p.dispatched(1,2,a)),
            "same native dispatch cannot commit twice");
        ok(!p.due(1,2,true,a,2,8),
            "same-tick reentry after dispatch cannot grant more output");
        ok(!fence.observe(1,3,true,a,27,entity,world),
            "next native tick still uses exact target");
        ok(!p.due(1,3,true,a,2,8),
            "genuine native cooldown survives repeated observations");

        TriggerBotPolicy changed=new TriggerBotPolicy();
        TriggerNativeIdentityFence changedFence=new TriggerNativeIdentityFence();
        for(int t=0;t<=2;t++) {
            changedFence.observe(2,t,true,a,27,entity,world);
            ok(changed.due(2,t,true,a,2,8)==(t==2),
                "independent changed-target baseline");
        }
        ok(changedFence.observe(2,2,true,a,27,replacement,world),
            "same UUID and entity ID, replaced native object vetoes");
        changed.invalidateCandidate();
        ok(!changedFence.permits(2,2,a,27,entity,world) &&
           !changedFence.permits(2,2,a,27,replacement,world),
            "mutated native player cannot borrow original frame");
        ok(!changed.due(2,2,true,a,2,8),
            "same-tick replacement does not rearm another candidate");
        ok(denied(()->changed.dispatched(2,2,a)),
            "in-flight action from old entity cannot commit");
        ok(!changedFence.observe(2,2,true,a,27,entity,world),
            "another same-tick sample cannot reinstate a cleared target");
        ok(!changedFence.permits(2,2,a,27,entity,world),
            "stale object remains denied after a same-tick rollback");
        ok(!changedFence.observe(2,3,true,a,27,replacement,world) &&
           !changed.due(2,3,true,a,2,8),
            "replacement must begin fresh dwell next tick");

        TriggerBotPolicy manual=new TriggerBotPolicy();
        TriggerNativeIdentityFence manualFence=new TriggerNativeIdentityFence();
        for(int t=0;t<=2;t++) {
            manualFence.observe(3,t,true,a,27,entity,world);
            manual.due(3,t,true,a,2,8);
        }
        ok(manualFence.observe(3,2,false,null,-1,null,null),
            "manual attack/target loss revokes same-tick native candidate");
        ok(!manual.due(3,2,false,null,2,8),
            "manual input denies duplicate-tick attack");
        ok(denied(()->manual.dispatched(3,2,a)),
            "manual click revokes previous ready state");
        ok(!manualFence.permits(3,2,a,27,entity,world),
            "manual input removes native identity permission");
        ok(!manualFence.observe(3,3,true,b,27,entity,world) &&
           !manual.due(3,3,true,b,2,8),
            "new target after manual override requires full fresh dwell");

        PhysicalInputGate physical=new PhysicalInputGate();
        ok(!physical.allowed(3,true,true,false,false,-100,-99),
            "physical attack must first observe real release");
        ok(physical.allowed(3,true,true,false,true,-100,-99),
            "physical player click remains admitted to vanilla");
        ok(!manual.due(3,3,false,null,2,8),
            "vanilla manual hold never authorizes optional TriggerBot");
        AtomicBoolean wrong=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try {fence.observe(1,4,false,null,-1,null,null);}
            catch(IllegalStateException expected){wrong.set(true);}
        });
        foreign.start();foreign.join();
        ok(wrong.get(),"native object identity observes only on owner thread");
    }

    private static void randomized() {
        Random rng=new Random(3802026L);
        UUID a=new UUID(380L,11L),b=new UUID(380L,12L);
        for(int i=0;i<80000;i++) {
            long epoch=i+100L;
            int dwell=1+rng.nextInt(5);
            int cooldown=1+rng.nextInt(40);
            Object world=new Object(),entity=new Object(),replacement=new Object();
            TriggerBotPolicy trigger=new TriggerBotPolicy();
            TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
            HitSelectPolicy hit=new HitSelectPolicy();
            ActionCoordinator actions=new ActionCoordinator();
            for(int t=0;t<=dwell;t++) {
                ok(!fence.observe(epoch,t,true,a,20,entity,world),
                    "80k stable native target acquire "+i);
                ok(trigger.due(epoch,t,true,a,dwell,cooldown)==(t==dwell),
                    "80k independent dwell boundary "+i);
                if(t<dwell) {
                    ok(!fence.observe(epoch,t,true,a,20,entity,world),
                        "80k identical native duplicate is passive "+i);
                    ok(!trigger.due(epoch,t,true,a,dwell,cooldown),
                        "80k acquisition cannot advance twice per tick "+i);
                }
            }
            final int mode=rng.nextInt(5);
            // 0 identical, 1 replaced entity object, 2 changed UUID,
            // 3 manual input/target loss, 4 policy-only manual input veto.
            boolean same=mode==0;
            if(mode<=3) {
                boolean nativeActive=mode!=3;
                UUID id=mode==2?b:a;
                Object obj=mode==1?replacement:entity;
                boolean changed=fence.observe(epoch,dwell,nativeActive,
                    nativeActive?id:null,nativeActive?20:-1,
                    nativeActive?obj:null,nativeActive?world:null);
                ok(changed==(!same),
                    "80k duplicate target/movement mutation detection "+i);
                if(changed)trigger.invalidateCandidate();
                ok(fence.permits(epoch,dwell,a,20,entity,world)==same,
                    "80k same-tick native permit cannot switch target "+i);
            }
            boolean due=trigger.due(epoch,dwell,mode!=3&&mode!=4,
                mode==2?b:a,dwell,cooldown);
            ok(!due,"80k same-tick reentry never queues another attack "+i);
            // A prepared intent can only survive an IDENTICAL duplicate.
            if(same) {
                ok(trigger.state()==TriggerBotPolicy.State.READY,
                    "80k matching duplicate preserves original prepared token "+i);
                actions.request("combat.triggerbot",Type.ATTACK,Phase.OUTPUT,
                    0,10,dwell,1,epoch);
                ActionCoordinator.Decision selected=actions.consume(
                    Type.ATTACK,Phase.OUTPUT,dwell,epoch);
                ok(selected!=null &&
                   "combat.triggerbot".equals(selected.owner),
                    "80k single shared attack owner still intact "+i);
                ok(actions.consume(Type.ATTACK,Phase.OUTPUT,dwell,epoch)==null,
                    "80k no second selected attack after coordinator consume "+i);
                ok(hit.preview(true,true,0,2,10,0,"normal",1,dwell,epoch),
                    "80k Hit Select preview for one native winner "+i);
                hit.committed(dwell,epoch);
                trigger.dispatched(epoch,dwell,a);
                ok(!trigger.due(epoch,dwell,true,a,dwell,cooldown),
                    "80k completed native dispatch cannot be reused "+i);
                ok(!hit.preview(true,true,0,2,10,0,"normal",1,dwell+1,epoch),
                    "80k shared Hit Select cooldown held "+i);
                if(cooldown>1) {
                    ok(!trigger.due(epoch,dwell+1,true,a,dwell,cooldown),
                        "80k TriggerBot committed cooldown held "+i);
                }
            } else {
                ok(trigger.state()==TriggerBotPolicy.State.IDLE,
                    "80k changed native input/target revokes ready token "+i);
                ok(denied(()->trigger.dispatched(epoch,dwell,a)),
                    "80k rejected changed-target dispatch cannot commit "+i);
                ok(!trigger.due(epoch,dwell+1,true,b,dwell,cooldown),
                    "80k changed or manual-vetoed target reacquires next tick "+i);
            }
        }
        ok(checks>300000,"80k randomized native attack admission traces executed");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("TRIGGER_SAME_TICK_ADMISSION_PASS assertions="+checks);
    }
}
