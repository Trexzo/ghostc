package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.71: Block Hit final USE denial cannot consume pulse cadence or a
 * post-attack/on-damage event. Hit Select and TriggerBot keep their existing
 * native commit authority, independent from a canceled Block Hit pulse.
 */
public final class BlockHitNativeCommitTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static boolean preview(BlockHitPolicy b,long t,int gap,String mode,
                                   int hurt,boolean attack) {
        return b.preview(true,true,false,true,true,true,attack,
            gap,hurt,mode,t,1);
    }
    private static void rejects(Runnable f,String why) {
        boolean denied=false;
        try{f.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static void exact() throws Exception {
        BlockHitPolicy interval=new BlockHitPolicy();
        ok(!preview(interval,0,3,"interval",0,false),
            "initial native hold belongs to vanilla Minecraft");
        ok(preview(interval,3,3,"interval",0,false),
            "periodic Block Hit stages first optional pulse");
        interval.cancelPrepared(); // final native use binding / focus veto
        rejects(()->interval.committed(3,1),
            "denied USE cannot commit a stale Block Hit pulse");
        ok(preview(interval,4,3,"interval",0,false),
            "denied pulse did not advance cadence and can retry");
        interval.committed(4,1);
        rejects(()->interval.committed(4,1),
            "same staged pulse cannot double commit");
        ok(!preview(interval,5,3,"interval",0,false),
            "successful pulse consumes exactly one interval");
        ok(preview(interval,7,3,"interval",0,false),
            "next pulse requires committed interval deadline");
        interval.cancelPrepared();
        ok(preview(interval,8,3,"interval",0,false),
            "another denied native output retains original pulse deadline");
        interval.cancelPrepared();

        BlockHitPolicy post=new BlockHitPolicy();
        ok(!preview(post,0,2,"post_attack",0,true),
            "genuine extra attack arms next-tick post-attack event");
        ok(preview(post,1,2,"post_attack",0,false),
            "post-attack pending pulse reaches native USE boundary");
        post.cancelPrepared();
        ok(preview(post,2,2,"post_attack",0,false),
            "denied USE retains original post-attack event within window");
        post.committed(2,1);
        ok(!preview(post,3,2,"post_attack",0,false),
            "successful post-attack event is consumed exactly once");

        BlockHitPolicy damage=new BlockHitPolicy();
        ok(!preview(damage,0,2,"on_damage",0,false),
            "damage mode observes first native sample");
        ok(!preview(damage,1,2,"on_damage",2,false),
            "rising hurt time arms delayed damage event");
        ok(preview(damage,2,2,"on_damage",2,false),
            "damage event is staged for native USE");
        damage.cancelPrepared();
        ok(preview(damage,3,2,"on_damage",1,false),
            "denied damage event retries without new rising edge");
        damage.committed(3,1);
        ok(!preview(damage,4,2,"on_damage",0,false),
            "same damaged state cannot produce duplicate event");
        ok(!preview(damage,5,2,"on_damage",2,false),
            "new genuine rise can arm a later damage event");
        ok(preview(damage,6,2,"on_damage",2,false),
            "independent new hurt-time edge is accepted");
        damage.cancelPrepared();

        BlockHitPolicy expire=new BlockHitPolicy();
        ok(!preview(expire,0,2,"post_attack",0,true),"pending starts");
        for(int t=1;t<=3;t++) {
            ok(preview(expire,t,2,"post_attack",0,false),
                "pending post-attack native USE remains stageable "+t);
            expire.cancelPrepared();
        }
        ok(!preview(expire,4,2,"post_attack",0,false),
            "pending event expires, without infinite retry");
        expire.cancelPrepared();
        ok(!preview(expire,5,2,"post_attack",0,false),
            "expired event cannot reappear");

        HitSelectPolicy hit=new HitSelectPolicy();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID enemy=new UUID(0x371,1);
        ok(hit.preview(true,true,0,2,3,0,"normal",1,0,2),
            "Hit Select previews an attack winner");
        hit.cancelPrepared();
        ok(hit.preview(true,true,0,2,3,0,"normal",1,1,2),
            "denied attack did not consume shared cooldown");
        hit.committed(1,2);
        ok(!hit.preview(true,true,0,2,3,0,"normal",1,2,2),
            "successful attack consumes Hit Select cooldown");
        ok(!trigger.due(2,0,true,enemy,2,4),"TriggerBot dwell starts");
        ok(!trigger.due(2,1,true,enemy,2,4),"TriggerBot dwell continues");
        ok(trigger.due(2,2,true,enemy,2,4),"TriggerBot is ready");
        trigger.invalidateCandidate();
        ok(!trigger.due(2,3,true,enemy,2,4),
            "native-denied TriggerBot reacquires rather than replaying");
        ok(!trigger.due(2,4,true,enemy,2,4),"new dwell still pending");
        ok(trigger.due(2,5,true,enemy,2,4),"new dwell earns readiness");

        interval.reset();
        rejects(()->interval.committed(8,1),
            "panic/reset revokes staged native output");
        BlockHitPolicy foreign=new BlockHitPolicy();
        AtomicBoolean wrongThread=new AtomicBoolean();
        Thread other=new Thread(()->{
            try{foreign.preview(true,true,false,true,true,true,false,3,
                0,"interval",0,1);}
            catch(IllegalStateException expected){wrongThread.set(true);}
        });
        other.start();other.join();
        ok(wrongThread.get(),"foreign worker cannot mutate pending Block Hit");
    }
    private static void randomized() {
        BlockHitPolicy policy=new BlockHitPolicy();
        Random rng=new Random(3712026L);
        long epoch=11,lastPulse=-1,tick=0;
        int interval=4;
        for(int i=0;i<90000;i++) {
            if(i%1103==0) {
                policy.reset();lastPulse=-1;epoch++;tick=0;
            }
            boolean held=rng.nextInt(9)!=0;
            boolean use=rng.nextInt(11)==0;
            boolean sword=rng.nextInt(8)!=0;
            boolean target=rng.nextInt(7)!=0;
            boolean extra=rng.nextInt(13)==0;
            boolean nativeGranted=rng.nextInt(4)!=0;
            boolean eligible=held&&!use&&sword&&target;
            boolean expected;
            if(!eligible) {lastPulse=-1;expected=false;}
            else if(lastPulse<0) {lastPulse=tick;expected=false;}
            else expected=tick-lastPulse>=interval&&!extra;
            boolean staged=policy.preview(true,held,use,sword,target,true,
                extra,interval,0,"interval",tick,epoch);
            ok(staged==expected,"90k independently timed Block Hit native preview "+i);
            if(staged&&nativeGranted) {
                policy.committed(tick,epoch);
                lastPulse=tick;
            } else {
                policy.cancelPrepared();
            }
            if(staged&&!nativeGranted)
                ok(lastPulse<tick,
                    "native rejected Block Hit cannot advance pulse clock "+i);
            tick++;
        }
        ok(checks>90000,"large deterministic denied/committed native USE trace");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("BLOCKHIT_NATIVE_COMMIT_PASS assertions="+checks);
    }
}
