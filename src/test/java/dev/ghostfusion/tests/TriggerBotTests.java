package dev.ghostfusion.tests;

import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.ActionCoordinator.Type;
import dev.ghostfusion.services.ActionCoordinator.Phase;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.48: deterministic trigger acquisition, native-dispatch receipts and
 * single-owner attack arbitration. No launched Minecraft is simulated. */
public final class TriggerBotTests {
    private static int checks;
    private static void ok(boolean value,String label) {
        checks++; if(!value)throw new AssertionError(label);
    }
    private static void rejects(Runnable action,String label) {
        boolean refused=false;
        try {action.run();}catch(RuntimeException expected){refused=true;}
        ok(refused,label);
    }
    public static void main(String[] args) throws Exception {
        UUID a=UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        UUID b=UUID.fromString("123e4567-e89b-12d3-a456-426614174002");
        TriggerBotPolicy p=new TriggerBotPolicy();
        ok(!p.due(1,10,true,a,2,4),"first crosshair acquisition cannot fire");
        ok(p.state()==TriggerBotPolicy.State.ACQUIRING,"acquisition state");
        ok(!p.due(1,11,true,a,2,4),"short dwell cannot fire");
        ok(p.due(1,12,true,a,2,4),"stable UUID reaches deadline");
        p.dispatched(1,12,a);
        ok(p.state()==TriggerBotPolicy.State.COOLDOWN,"dispatch owns cooldown");
        ok(!p.due(1,13,true,a,2,4),"native dispatch cooldown");
        ok(!p.due(1,15,true,a,2,4),"cooldown exact boundary minus one");
        ok(p.due(1,16,true,a,2,4),"cooldown exact boundary");
        rejects(()->p.dispatched(1,16,b),"different UUID cannot consume dispatch");
        p.dispatched(1,16,a);
        rejects(()->p.dispatched(1,16,a),"no double native dispatch same tick");
        ok(!p.due(1,17,false,a,2,4),"physical hold disarms trigger acquisition");
        ok(p.state()==TriggerBotPolicy.State.IDLE,"hold releases synthetic trigger");
        ok(!p.due(1,18,true,a,2,4),"release must reacquire");
        ok(!p.due(1,19,true,b,2,4),"switching UUID discards old dwell");
        ok(p.due(1,21,true,b,2,4),"new UUID requires full dwell");
        ok(!p.due(2,22,true,b,2,4),"world epoch resets target");
        ok(!p.due(2,23,true,b,3,4),"settings edit rearms");
        ok(!p.due(2,24,true,b,3,4),"new target dwell after edit");
        ok(p.due(2,26,true,b,3,4),"new settings deadline");
        ok(!p.due(2,27,false,null,3,4),"missing target clears acquisition");
        ok(!p.due(2,28,true,b,3,4),"cannot borrow dwell across target loss");
        rejects(()->p.due(2,27,true,b,3,4),"reject backward game time");
        rejects(()->p.due(2,29,true,b,0,4),"reject zero acquire");
        rejects(()->p.due(2,29,true,b,3,41),"reject invalid cooldown");
        rejects(()->p.dispatched(2,28,b),"cannot dispatch unready");
        AtomicBoolean denied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try {p.due(2,30,true,b,3,4);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();worker.join();
        ok(denied.get(),"foreign Netty/thread cannot mutate trigger policy");

        // Simulate both producers proposing on one tick: a single owned
        // coordinator decision wins, not two emitted key presses.
        ActionCoordinator actions=new ActionCoordinator();
        Random random=new Random(3482026L);
        long epoch=9;
        int clickerWins=0, triggerWins=0;
        for(int t=0;t<50000;t++) {
            boolean physicalHeld=random.nextBoolean();
            boolean ready=random.nextBoolean();
            if(physicalHeld)
                actions.request("combat.clicker",Type.ATTACK,Phase.OUTPUT,
                    0,20,t,1,epoch);
            if(ready)
                actions.request("combat.triggerbot",Type.ATTACK,Phase.OUTPUT,
                    0,10,t,1,epoch);
            ActionCoordinator.Decision decision=actions.consume(
                Type.ATTACK,Phase.OUTPUT,t,epoch);
            if(decision!=null) {
                if(physicalHeld) {
                    ok("combat.clicker".equals(decision.owner),
                        "held mouse wins over trigger in trace "+t);
                    clickerWins++;
                } else {
                    ok("combat.triggerbot".equals(decision.owner),
                        "trigger can own when physical clicker absent "+t);
                    triggerWins++;
                }
                ok(actions.consume(Type.ATTACK,Phase.OUTPUT,t,epoch)==null,
                    "one synthesized attack per frame "+t);
            }
            actions.cancelOwner("combat.clicker");
            actions.cancelOwner("combat.triggerbot");
        }
        ok(clickerWins>10000&&triggerWins>10000,"both sources exercise actual ownership");
        p.reset();
        ok(p.state()==TriggerBotPolicy.State.IDLE,"panic reset disarms");
        System.out.println("TRIGGERBOT_PASS assertions="+checks);
    }
}
