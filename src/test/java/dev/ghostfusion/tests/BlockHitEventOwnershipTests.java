package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.84: event-mode Block Hit samples one END tick exactly once. One
 * pending event owns its original bounded lifetime; newer overlapping
 * damage/ATTACK events cannot prolong it or create duplicate native USE.
 *
 * All tests use pure Java policy. No Minecraft input or server ACK.
 */
public final class BlockHitEventOwnershipTests {
    private static long checks;
    private static void ok(boolean b,String reason) {
        checks++;
        if(!b)throw new AssertionError(reason);
    }
    private static boolean sample(BlockHitPolicy policy,long tick,long epoch,
                                  String mode,int hurt,boolean attack,int gap,
                                  boolean held) {
        return policy.preview(true,held,false,true,true,false,attack,
            gap,hurt,mode,tick,epoch);
    }
    private static void rejected(BlockHitPolicy p,long tick,long epoch) {
        p.cancelPrepared();
        boolean failed=false;
        try {p.committed(tick,epoch);}
        catch(IllegalStateException expected) {failed=true;}
        ok(failed,"rejected native USE cannot commit a speculative Block Hit event");
    }
    private static void exact() throws Exception {
        BlockHitPolicy post=new BlockHitPolicy();
        ok(!sample(post,0,1,"post_attack",0,true,2,true),
            "first confirmed Clicker ATTACK arms one future post-attack event");
        ok(!sample(post,1,1,"post_attack",0,true,2,true),
            "overlapping real ATTACK suppresses native USE without refreshing event");
        ok(!sample(post,2,1,"post_attack",0,true,2,true),
            "another overlapping ATTACK cannot extend original event");
        ok(!sample(post,3,1,"post_attack",0,true,2,true),
            "original third-tick event is still suppressed by ATTACK");
        ok(!sample(post,4,1,"post_attack",0,false,2,true),
            "original event has expired even though overlapping attacks occurred");
        ok(!sample(post,5,1,"post_attack",0,false,2,true),
            "expired post-attack event cannot replay without a new actual ATTACK");
        ok(!sample(post,6,1,"post_attack",0,true,2,true),
            "new genuine attack after expiry arms a new event");
        ok(sample(post,7,1,"post_attack",0,false,2,true),
            "new event independently stages a native USE on a later tick");
        rejected(post,7,1);
        ok(!sample(post,7,1,"post_attack",0,false,2,true),
            "duplicate canceled END tick cannot restage the same USE");
        ok(sample(post,8,1,"post_attack",0,false,2,true),
            "native rejection preserves pending event for next-tick retry");
        post.committed(8,1);
        ok(!sample(post,8,1,"post_attack",0,false,2,true),
            "duplicate after native commit cannot produce a second USE");
        ok(!sample(post,9,1,"post_attack",0,false,2,true),
            "committed event has no unprompted follow-up pulse");

        BlockHitPolicy pending=new BlockHitPolicy();
        ok(!sample(pending,0,2,"post_attack",0,true,2,true),
            "fresh native event first arms its original window");
        ok(sample(pending,1,2,"post_attack",0,false,2,true),
            "pending event is stageable before cancellation");
        ok(!sample(pending,1,2,"post_attack",0,false,2,true),
            "duplicate while staged cancels speculative output");
        rejected(pending,1,2);
        ok(sample(pending,2,2,"post_attack",0,false,2,true),
            "one canceled duplicate does not consume the original event");
        pending.cancelPrepared();
        ok(sample(pending,3,2,"post_attack",0,false,2,true),
            "native refusal retains original event through third tick");
        pending.cancelPrepared();
        ok(!sample(pending,4,2,"post_attack",0,false,2,true),
            "native retries never extend the three-tick deadline");

        BlockHitPolicy damage=new BlockHitPolicy();
        ok(!sample(damage,0,3,"on_damage",0,false,2,true),
            "first local hurt-time sample is a passive baseline");
        ok(!sample(damage,1,3,"on_damage",9,false,2,true),
            "first genuine rising local hurt edge arms event");
        ok(!sample(damage,2,3,"on_damage",8,true,2,true),
            "native ATTACK suppresses an otherwise due damage USE");
        ok(sample(damage,3,3,"on_damage",10,false,2,true),
            "new rising hurt edge cannot replace older unconsumed damage event");
        damage.cancelPrepared();
        ok(sample(damage,4,3,"on_damage",9,false,2,true),
            "original damage event still stageable at its third-tick deadline");
        damage.cancelPrepared();
        ok(!sample(damage,5,3,"on_damage",8,false,2,true),
            "new damage observed during old event cannot extend its lifetime");
        ok(!sample(damage,6,3,"on_damage",10,false,2,true),
            "genuine rise after old expiry creates a new pending event");
        ok(sample(damage,7,3,"on_damage",9,false,2,true),
            "new independent local damage edge permits a later pulse");
        damage.committed(7,3);
        ok(!sample(damage,8,3,"on_damage",8,false,2,true),
            "damage event consumed only once by actual native USE");

        BlockHitPolicy interval=new BlockHitPolicy();
        ok(!sample(interval,0,4,"interval",0,false,3,true),
            "interval mode seeds original vanilla attack hold");
        ok(sample(interval,3,4,"interval",0,false,3,true),
            "original interval pulse becomes eligible");
        ok(!sample(interval,3,4,"interval",0,false,3,true),
            "duplicate interval preview cancels staged native output");
        rejected(interval,3,4);
        ok(sample(interval,4,4,"interval",0,false,3,true),
            "canceled interval pulse can retry following client tick");
        interval.committed(4,4);
        ok(!sample(interval,4,4,"interval",0,false,3,true),
            "interval mode cannot emit two USE actions on one tick");
        ok(!sample(interval,5,4,"interval",0,false,3,true),
            "committed interval preserves the full minimum period");

        BlockHitPolicy lifecycle=new BlockHitPolicy();
        ok(!sample(lifecycle,0,5,"post_attack",0,true,2,true),
            "post event before owner disengages");
        ok(!sample(lifecycle,1,5,"post_attack",0,false,2,false),
            "physical release discards the old pending event");
        ok(!sample(lifecycle,2,5,"post_attack",0,false,2,true),
            "fresh press cannot replay stale event");
        ok(!sample(lifecycle,3,5,"post_attack",0,false,2,true),
            "rearmed hold remains passive without new attack");
        ok(!sample(lifecycle,4,5,"on_damage",0,false,2,true),
            "mode change starts a clean damage baseline");
        ok(!sample(lifecycle,5,5,"on_damage",10,false,2,true),
            "new mode requires its own genuine hurt event");
        ok(!sample(lifecycle,0,6,"on_damage",10,false,2,true),
            "new generation never imports prior pending damage event");
        ok(!sample(lifecycle,1,6,"on_damage",10,false,2,true),
            "new world stable hurtTime does not produce a synthetic edge");

        AtomicBoolean threadDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{lifecycle.preview(true,true,false,true,true,false,false,
                    2,0,"post_attack",2,6);}
            catch(IllegalStateException expected){threadDenied.set(true);}
        });
        foreign.start();foreign.join();
        ok(threadDenied.get(),"foreign callback cannot mutate owner-thread event queue");
    }
    private static void randomized() {
        Random rng=new Random(3842026L);
        BlockHitPolicy p=new BlockHitPolicy();
        long epoch=-1;
        boolean armed=false;
        int prevHurt=0;
        long pending=-1,lastCommitted=-1;
        boolean[] kinds={false,true};
        long duplicates=0,expired=0,overlaps=0,denied=0;
        for(int i=0;i<90000;i++) {
            long e=1+i/101;
            long tick=i%101;
            if(e!=epoch) {
                epoch=e;
                p.reset();
                armed=false;
                prevHurt=0;
                pending=lastCommitted=-1;
            }
            boolean post=kinds[(int)e%2];
            String mode=post?"post_attack":"on_damage";
            int gap=2+(int)(e%9);
            boolean attack=rng.nextInt(5)==0;
            int hurt=rng.nextInt(5)==0?10:rng.nextInt(10);
            boolean held=rng.nextInt(13)!=0;
            boolean initialArmed=armed;
            boolean expected=false;
            if(!held) {
                armed=false;
                pending=-1;
                lastCommitted=-1;
                prevHurt=hurt;
            } else if(!armed) {
                armed=true;
                prevHurt=hurt;
                if(post&&attack)pending=tick;
            } else {
                boolean rising=hurt>prevHurt&&hurt>0;
                if(pending>=0&&tick-pending>3) {
                    pending=-1;
                    expired++;
                }
                if(pending<0 && ((post&&attack)||(!post&&rising)))
                    pending=tick;
                else if(pending>=0 && ((post&&attack)||(!post&&rising)))
                    overlaps++;
                prevHurt=hurt;
                if(pending>=0 && tick>pending && !attack &&
                   (lastCommitted<0 || tick-lastCommitted>=gap))
                    expected=true;
            }
            boolean staged=sample(p,tick,e,mode,hurt,attack,gap,held);
            ok(staged==expected,
                "90k independent event-first-owner and interval oracle "+i);
            if(staged) {
                if(rng.nextInt(4)==0) {
                    denied++;
                    p.cancelPrepared();
                } else {
                    p.committed(tick,e);
                    lastCommitted=tick;
                    pending=-1;
                }
            } else p.cancelPrepared();
            // A duplicate callback with a different hurt sample and action
            // receipt must neither create a new event nor restage a USE.
            int changedHurt=rng.nextInt(21);
            boolean changedAttack=rng.nextBoolean();
            boolean duplicate=sample(p,tick,e,mode,changedHurt,
                changedAttack,gap,held);
            ok(!duplicate,"90k same-tick duplicate is always passive "+i);
            p.cancelPrepared();
            duplicates++;
            // Mode and lifecycle baseline must not be confused with a
            // deferred event even when the first sample had nonzero hurt.
            if(!initialArmed && held)
                ok(!staged,"first observed hold never emits native USE "+i);
        }
        ok(duplicates==90000 && overlaps>1000 && expired>100 && denied>300,
            "all independent overlap, expiry, native-veto and duplicate classes covered");
        ok(checks>180000,"90k seeded Block Hit event owner-tick frames");
    }
    public static void main(String[] args) throws Exception {
        exact();
        randomized();
        System.out.println("BLOCKHIT_EVENT_OWNERSHIP_PASS assertions="+checks);
    }
}
