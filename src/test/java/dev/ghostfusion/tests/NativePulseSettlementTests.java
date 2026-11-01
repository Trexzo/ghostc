package dev.ghostfusion.tests;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.BlockHitPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.72 exact uncertain native output / recovery without fabricated ACK. */
public final class NativePulseSettlementTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String why) {
        boolean rejected=false;
        try{r.run();}catch(RuntimeException expected){rejected=true;}
        ok(rejected,why);
    }
    private static void exact() throws Exception {
        NativePulseSettlement a=new NativePulseSettlement();
        ok(a.admit(Lane.ATTACK,1),"initial attack may be admitted");
        ok(a.admit(Lane.USE,1),"independent use lane");
        a.begin(Lane.ATTACK,1,0);
        ok(!a.admit(Lane.ATTACK,1),"in-flight key cannot be duplicated");
        rejects(()->a.begin(Lane.ATTACK,1,0),"same tick cannot begin twice");
        a.abandon(Lane.ATTACK,1,0);
        ok(a.phase(Lane.ATTACK,1)==Phase.UNCERTAIN,
            "KeyBinding exception creates uncertain attack outcome");
        ok(!a.admit(Lane.ATTACK,1),"unknown click cannot be retried");
        a.abandon(Lane.ATTACK,1,0);
        ok(a.phase(Lane.ATTACK,1)==Phase.UNCERTAIN,
            "repeated finally does not clear quarantine");
        ok(a.admit(Lane.USE,1),"unrelated use lane remains independent");
        a.begin(Lane.USE,1,0);
        a.nativeReturned(Lane.USE,1,0);
        a.abandon(Lane.USE,1,0);
        ok(!a.admit(Lane.USE,1),
            "post-native but pre-BlockHit commit exception quarantines use");
        ok(a.admit(Lane.ATTACK,2),"new game generation resets attack fence");
        ok(a.admit(Lane.USE,2),"new game generation resets use fence");

        HitSelectPolicy hit=new HitSelectPolicy();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID target=new UUID(372,7);
        ok(hit.preview(true,true,0,2,3,0,"normal",1,0,2),
            "Hit Select prepared independently");
        ok(!trigger.due(2,0,true,target,1,3),"native trigger target starts dwell");
        ok(trigger.due(2,1,true,target,1,3),"target gains native readiness");
        a.begin(Lane.ATTACK,2,1);
        a.nativeReturned(Lane.ATTACK,2,1);
        hit.cancelPrepared(); // emulate stale Hit Select after native key
        rejects(()->hit.committed(0,2),
            "Hit Select failure after native return is injected");
        a.abandon(Lane.ATTACK,2,1);
        ok(!a.admit(Lane.ATTACK,2),
            "partial Hit Select + TriggerBot commit cannot replay attack");
        ok(trigger.state()==TriggerBotPolicy.State.READY,
            "pre-existing TriggerBot state remains observable, not fabricated");
        ok(a.admit(Lane.ATTACK,3),"new world generation reopens attack lane");

        a.begin(Lane.ATTACK,3,0);
        a.nativeReturned(Lane.ATTACK,3,0);
        a.committed(Lane.ATTACK,3,0);
        // A telemetry or diagnostics RuntimeException after completed()
        // must never downgrade the already fully settled local native pulse.
        rejects(()->{throw new IllegalStateException("diagnostics injection");},
            "postsettlement diagnostics fault injection");
        a.abandon(Lane.ATTACK,3,0);
        ok(a.admit(Lane.ATTACK,3),
            "postcommit diagnostics exception does not fake uncertain pulse");
        rejects(()->a.begin(Lane.ATTACK,3,0),
            "same-tick postdiagnostic replay rejected");
        a.begin(Lane.ATTACK,3,1);
        a.nativeReturned(Lane.ATTACK,3,1);
        a.committed(Lane.ATTACK,3,1);
        a.abandon(Lane.ATTACK,3,1);

        BlockHitPolicy block=new BlockHitPolicy();
        ok(!block.preview(true,true,false,true,true,true,false,
            2,0,"interval",0,3),"vanilla first hold");
        ok(block.preview(true,true,false,true,true,true,false,
            2,0,"interval",2,3),"Block Hit native staged");
        a.begin(Lane.USE,3,2);
        a.nativeReturned(Lane.USE,3,2);
        block.cancelPrepared(); // injected failure before policy commit
        rejects(()->block.committed(2,3),
            "native use occurred but Block Hit commit was unavailable");
        a.abandon(Lane.USE,3,2);
        ok(!a.admit(Lane.USE,3),
            "Block Hit cannot retry uncertain native USE");
        ok(a.admit(Lane.ATTACK,3),
            "failed use does not artificially block successful attack lane");
        ok(a.admit(Lane.USE,4),
            "only fresh generation may recover unknown native use");

        AtomicBoolean foreignDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{a.admit(Lane.ATTACK,4);}
            catch(IllegalStateException expected){foreignDenied.set(true);}
        });foreign.start();foreign.join();
        ok(foreignDenied.get(),"foreign network thread cannot mutate native settlement");
        rejects(()->a.admit(null,4),"null native lane refused");
        rejects(()->a.begin(Lane.USE,-1,0),"negative generation refused");
    }
    private static void randomized() {
        NativePulseSettlement p=new NativePulseSettlement();
        Random r=new Random(3722026L);
        long epoch=6,tick=0;
        boolean[] uncertain={false,false};
        long[] lastTick={-1,-1};
        for(int i=0;i<90000;i++) {
            if(i%1073==0) {
                epoch++;tick=0;
                uncertain[0]=uncertain[1]=false;
                lastTick[0]=lastTick[1]=-1;
            }
            Lane lane=r.nextBoolean()?Lane.ATTACK:Lane.USE;
            int n=lane.ordinal();
            boolean expectedAdmit=!uncertain[n];
            ok(p.admit(lane,epoch)==expectedAdmit,
                "90k independent unresolved native pulse oracle "+i);
            if(expectedAdmit) {
                p.begin(lane,epoch,tick);
                int failure=r.nextInt(11);
                if(failure==0) {
                    p.abandon(lane,epoch,tick);uncertain[n]=true;
                } else {
                    p.nativeReturned(lane,epoch,tick);
                    if(failure==1) {
                        p.abandon(lane,epoch,tick);uncertain[n]=true;
                    } else {
                        p.committed(lane,epoch,tick);
                        p.abandon(lane,epoch,tick);
                        // Optional telemetry after settlement is allowed to
                        // fail without classifying a native pulse as unknown.
                    }
                }
                lastTick[n]=tick;
            }
            ok(p.admit(lane,epoch)==!uncertain[n],
                "90k no unknown native output replay "+i);
            tick++;
        }
        ok(checks>=180000,"large deterministic staged/partial/settled traces");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("NATIVE_PULSE_SETTLEMENT_PASS assertions="+checks);
    }
}
