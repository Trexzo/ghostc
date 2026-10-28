package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.ActionCoordinator.Decision;
import dev.ghostfusion.services.ActionCoordinator.Phase;
import dev.ghostfusion.services.ActionCoordinator.Type;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.49: only the actually dispatched native pulse owns shared cooldown. */
public final class AttackCommitTests {
    private static int checks;
    private static void ok(boolean value,String label) {
        checks++;if(!value)throw new AssertionError(label);
    }
    private static void rejects(Runnable task,String label) {
        boolean denied=false;
        try{task.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,label);
    }
    private static boolean preview(HitSelectPolicy gate,long tick,long epoch,int gap) {
        return gate.preview(true,true,0,2,gap,0,"normal",1,tick,epoch);
    }
    private static void staging() {
        HitSelectPolicy gate=new HitSelectPolicy();
        ok(preview(gate,0,1,4),"first candidate preview");
        gate.cancelPrepared();
        rejects(()->gate.committed(0,1),"canceled preview cannot commit");
        ok(preview(gate,1,1,4),"rejected native click did not consume a gap");
        gate.committed(1,1);
        rejects(()->gate.committed(1,1),"cannot commit same native pulse twice");
        ok(!preview(gate,2,1,4),"cooldown blocks next synthetic pulse");
        ok(!preview(gate,4,1,4),"cooldown remains below exact boundary");
        ok(preview(gate,5,1,4),"exact cooldown boundary");
        rejects(()->gate.committed(4,1),"wrong tick cannot commit");
        rejects(()->gate.committed(5,2),"wrong world epoch cannot commit");
        gate.cancelPrepared();
        ok(preview(gate,6,1,4),"preview canceled without consuming");
        gate.committed(6,1);
        ok(gate.accept(true,true,0,2,4,0,"ready",1,7,1),
            "legacy atomic accept works after changing mode");
        ok(!gate.accept(true,true,0,2,4,0,"ready",1,8,1),
            "legacy atomic accept still consumes its cooldown");
        ok(preview(gate,9,2,4),"new epoch resets shared cooldown");
        gate.reset();
        rejects(()->gate.committed(9,2),"Panic/reset invalidates pending commit");
        ok(preview(gate,10,3,4),"new session reacquires");
        rejects(()->gate.preview(true,true,0,2,0,0,"normal",1,11,3),
            "invalid timing fails closed");
        AtomicBoolean denied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try {gate.committed(10,3);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(denied.get(),"foreign thread cannot commit a native pulse");
    }
    private static void nativeTandemReplay() {
        ActionCoordinator actions=new ActionCoordinator();
        HitSelectPolicy hit=new HitSelectPolicy();
        BlockHitPolicy block=new BlockHitPolicy();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID target=UUID.fromString("123e4567-e89b-12d3-a456-426614174003");
        Random rng=new Random(3492026L);
        long lastCommitted=-1;
        long lastRealClicker=-1;
        int rejected=0,clicker=0,triggered=0,blockPulses=0;
        for(int t=0;t<50000;t++) {
            boolean physicalDown=rng.nextBoolean();
            boolean clickCandidate=physicalDown && rng.nextBoolean();
            boolean triggerCandidate=!physicalDown && rng.nextBoolean();
            if(clickCandidate)
                actions.request("combat.clicker",Type.ATTACK,Phase.OUTPUT,0,20,t,1,1);
            if(triggerCandidate)
                actions.request("combat.triggerbot",Type.ATTACK,Phase.OUTPUT,0,10,t,1,1);
            Decision d=actions.consume(Type.ATTACK,Phase.OUTPUT,t,1);
            ok(actions.consume(Type.ATTACK,Phase.OUTPUT,t,1)==null,
                "no two owners can consume one attack tick "+t);
            boolean nativeDispatched=false;
            boolean realClicker=false;
            if(d!=null) {
                boolean admitted=preview(hit,t,1,3);
                boolean nativeEligible=rng.nextInt(3)!=0;
                if(admitted && nativeEligible) {
                    ok(lastCommitted<0||t-lastCommitted>=3,
                        "two committed native pulses respect shared cooldown "+t);
                    hit.committed(t,1);
                    nativeDispatched=true;
                    realClicker="combat.clicker".equals(d.owner);
                    lastCommitted=t;
                    if(realClicker) {clicker++;lastRealClicker=t;}
                    else triggered++;
                }else{
                    hit.cancelPrepared();
                    rejected++;
                }
            }
            boolean blockPulse=block.due(true,physicalDown,false,true,true,true,
                realClicker,4,0,"post_attack",t,1);
            if(blockPulse) {
                ok(!nativeDispatched && physicalDown &&
                    lastRealClicker>=0 && t>lastRealClicker &&
                    t-lastRealClicker<=3,
                    "post-attack block requires earlier actual clicker pulse "+t);
                blockPulses++;
            }
            // No legacy pending ActionCoordinator requests from either source.
            actions.cancelOwner("combat.clicker");
            actions.cancelOwner("combat.triggerbot");
        }
        ok(rejected>1000 && clicker>1000 && triggered>1000,
            "native acceptance, rejections and both attack sources exercised");
        ok(blockPulses>=0,"native Block Hit replay completed");
        // A TriggerBot accepted intent cannot commit cooldown without a pulse.
        ok(!trigger.due(1,0,true,target,2,4),"trigger acquisition starts clean");
        ok(trigger.due(1,2,true,target,2,4),"trigger candidate ready");
        ok(trigger.due(1,3,true,target,2,4),
            "undispatched candidate remains ready instead of entering cooldown");
        trigger.dispatched(1,3,target);
        ok(!trigger.due(1,4,true,target,2,4),"native dispatch commits trigger cooldown");
    }
    public static void main(String[] args) {
        staging();
        nativeTandemReplay();
        ok(checks>50000,"substantial real-outcome arbitration replay");
        System.out.println("ATTACK_COMMIT_PASS assertions="+checks);
    }
}
