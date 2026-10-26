package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.33 opt-in combat modes. Offline policy checks are NOT native-game evidence.
 * Original 0.7 baseline tests remain unchanged and are executed separately.
 */
public final class CombatModesTests {
    private static int checks;
    private static void ok(boolean value,String message) {
        checks++;
        if(!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable task,String message) {
        boolean denied=false;
        try {task.run();} catch(RuntimeException expected) {denied=true;}
        ok(denied,message);
    }
    private static boolean block(BlockHitPolicy p,String mode,int selfHurt,boolean extra,
                                 long tick,long epoch) {
        return p.due(true,true,false,true,true,true,
            extra,4,selfHurt,mode,tick,epoch);
    }
    private static void blockModes() {
        BlockHitPolicy p=new BlockHitPolicy();
        ok(!block(p,"interval",0,false,0,1),"default interval arms");
        ok(!block(p,"interval",0,false,3,1),"default interval early");
        ok(block(p,"interval",0,false,4,1),"default interval pulse unchanged");
        ok(!block(p,"post_attack",0,false,5,1),"mode switch clears stale cadence");
        ok(!block(p,"post_attack",0,true,6,1),"attack queues a future use, not same-tick");
        ok(block(p,"post_attack",0,false,7,1),"post-attack pulse occurs next clean tick");
        ok(!block(p,"post_attack",0,false,7,1),"post attack cannot double pulse");
        ok(!block(p,"post_attack",0,true,8,1),"next attack arms another opportunity");
        ok(!block(p,"post_attack",0,false,9,1),"cooldown prevents early use");
        ok(!block(p,"post_attack",0,false,10,1),"cooldown still enforced");
        ok(block(p,"post_attack",0,false,11,1),"queued pulse retries within bounded window");
        ok(!block(p,"post_attack",0,false,12,1),"one pulse per queued attack");
        ok(!block(p,"on_damage",0,false,13,1),"mode change safely arms");
        ok(!block(p,"on_damage",10,false,14,1),"rising self-hurt arms, never same tick");
        ok(block(p,"on_damage",9,false,15,1),"damage event triggers delayed use");
        ok(!block(p,"on_damage",8,false,16,1),"falling hurt-time does not retrigger");
        ok(!block(p,"on_damage",10,false,17,1),"new hurt event may rearm");
        ok(!block(p,"on_damage",9,false,18,1),"cooldown holds next reactive pulse");
        ok(block(p,"on_damage",8,false,19,1),"new damage pulse waits for cooldown");
        ok(!block(p,"on_damage",7,false,20,1),"no unprompted repeat from damage history");
        ok(!block(p,"on_damage",0,false,0,2),"new world cannot inherit damage event");
        ok(!block(p,"on_damage",10,false,1,2),"new generation rising damage event");
        ok(block(p,"on_damage",9,false,2,2),"new generation independently emits later");

        p.reset();
        ok(!block(p,"post_attack",0,true,0,1),"first observed attack never same tick");
        ok(block(p,"post_attack",0,false,1,1),"first attack schedules next tick only");
        ok(!p.due(true,true,true,true,true,true,
            false,4,0,"post_attack",2,1),"user-held use cancels pending trigger");
        ok(!block(p,"post_attack",0,false,3,1),"resumed hold rearms instead of replay");
        ok(!block(p,"post_attack",0,false,4,1),"no phantom after vanilla use");

        p.reset();
        ok(!block(p,"post_attack",0,false,0,1),"arm before expiry scenario");
        ok(!block(p,"post_attack",0,true,1,1),"arm post attack");
        ok(block(p,"post_attack",0,false,2,1),"first trigger");
        // Change the configured interval after the first successful pulse:
        // the pending event must expire, not fire after a long cooldown.
        ok(!p.due(true,true,false,true,true,true,
            true,10,0,"post_attack",3,1),"another attack starts a long cooldown");
        ok(!p.due(true,true,false,true,true,true,
            false,10,0,"post_attack",4,1),"cooldown t4");
        ok(!p.due(true,true,false,true,true,true,
            false,10,0,"post_attack",5,1),"cooldown t5");
        ok(!p.due(true,true,false,true,true,true,
            false,10,0,"post_attack",6,1),"cooldown t6");
        ok(!p.due(true,true,false,true,true,true,
            false,10,0,"post_attack",7,1),"event older than three ticks expires");
        ok(!p.due(true,true,false,true,true,true,
            false,10,0,"post_attack",12,1),"expired pending remains suppressed");

        p.reset();
        ok(!block(p,"post_attack",0,false,0,1),"starting post-attack");
        ok(!block(p,"post_attack",0,true,1,1),"attack queues");
        ok(!p.due(true,false,false,true,true,true,
            false,4,0,"post_attack",2,1),"release cancels pending");
        ok(!block(p,"post_attack",0,false,3,1),"new press arms clean");
        ok(!block(p,"post_attack",0,false,4,1),"released pending never replays");
        rejects(()->block(p,"unknown",0,false,5,1),"invalid mode fail closed");
        rejects(()->block(p,"on_damage",-1,false,5,1),"negative hurt rejected");
        rejects(()->block(p,"post_attack",21,false,5,1),"out-of-bound hurt rejected");
        rejects(()->block(p,"post_attack",0,false,-1,1),"negative tick rejected");
        rejects(()->block(p,"post_attack",0,false,5,-1),"negative epoch rejected");
        p.reset();
        ok(!block(p,"post_attack",0,false,5,1),"initialize tick sequence");
        rejects(()->block(p,"post_attack",0,false,4,1),"tick regression rejected");
    }
    private static void hitModes() {
        HitSelectPolicy p=new HitSelectPolicy();
        ok(p.accept(true,true,2,2,3,0,"normal",1,0,1),
            "normal retains legacy 2-hurt threshold");
        ok(!p.accept(true,true,2,2,3,0,"ready",1,1,1),
            "ready mode requires target not in hurt animation");
        ok(p.accept(true,true,0,2,3,0,"ready",1,2,1),
            "zero-hurt ready target admitted");
        ok(!p.accept(true,true,0,2,3,0,"ready",1,3,1),
            "ready mode preserves minimum accepted-gap");
        ok(!p.accept(true,true,0,2,3,2,"trade",3,4,1),
            "trade requires sufficient local hurt-time");
        ok(p.accept(true,true,2,2,3,3,"trade",3,5,1),
            "trade permits target in configured hurt window");
        ok(!p.accept(true,true,2,2,3,3,"trade",3,6,1),
            "trade still obeys gap");
        ok(!p.accept(true,true,4,2,3,10,"trade",3,8,1),
            "trade never skips target hurt-time cap");
        ok(p.accept(true,true,0,2,3,10,"trade",3,9,1),
            "trade can resume when both damage windows admit");
        ok(!p.accept(false,true,0,2,3,10,"trade",3,12,1),
            "disabled filter still rejects");
        ok(!p.accept(true,false,0,2,3,10,"trade",3,13,1),
            "missing target veto retains priority");
        rejects(()->p.accept(true,true,0,2,3,0,"ghost",3,14,1),
            "unrecognized filter mode rejected");
        rejects(()->p.accept(true,true,0,2,3,-1,"trade",3,14,1),
            "bad self hurt-time rejected");
        rejects(()->p.accept(true,true,0,2,3,1,"trade",0,14,1),
            "bad trade threshold rejected");
        rejects(()->p.accept(true,true,0,2,3,1,"trade",11,14,1),
            "oversized trade threshold rejected");
        p.reset();
        ok(p.accept(true,true,0,0,1,0,"ready",1,0,1),
            "reset clears cooldown and new mode");
        ok(p.accept(true,true,0,0,1,5,"trade",2,0,2),
            "world generation resets hit-select selection");
    }
    private static void traceReplay() {
        for(int seed=0;seed<50;seed++) {
            Random rng=new Random(9092026L+seed);
            BlockHitPolicy post=new BlockHitPolicy();
            BlockHitPolicy defensive=new BlockHitPolicy();
            HitSelectPolicy ready=new HitSelectPolicy();
            HitSelectPolicy trade=new HitSelectPolicy();
            long latestAttack=-1,latestDamage=-1;
            int prevHurt=0;
            long lastPost=-1,lastDefensive=-1;
            // All controls remain otherwise eligible; randomly injected attack
            // pulses and rising damage edges are the sole event sources.
            for(long tick=0;tick<400;tick++) {
                boolean extra=(rng.nextInt(6)==0);
                int selfHurt=(rng.nextInt(7)==0)?10:Math.max(0,prevHurt-1);
                boolean gotPost=block(post,"post_attack",selfHurt,extra,tick,1);
                boolean gotDefensive=block(defensive,"on_damage",selfHurt,extra,tick,1);
                if(extra) latestAttack=tick;
                if(selfHurt>prevHurt && tick>0) latestDamage=tick;
                if(gotPost) {
                    ok(!extra && latestAttack>=0 &&
                        tick>latestAttack && tick-latestAttack<=3,
                        "post-attack event provenance "+seed+"/"+tick);
                    ok(lastPost<0 || tick-lastPost>=4,
                        "post-attack cooldown "+seed+"/"+tick);
                    lastPost=tick;
                }
                if(gotDefensive) {
                    ok(!extra && latestDamage>=0 &&
                        tick>latestDamage && tick-latestDamage<=3,
                        "local-damage event provenance "+seed+"/"+tick);
                    ok(lastDefensive<0 || tick-lastDefensive>=4,
                        "on-damage cooldown "+seed+"/"+tick);
                    lastDefensive=tick;
                }
                int targetHurt=rng.nextInt(11);
                boolean target=rng.nextInt(5)!=0;
                boolean gotReady=ready.accept(true,target,targetHurt,3,2,
                    selfHurt,"ready",2,tick,1);
                boolean gotTrade=trade.accept(true,target,targetHurt,3,2,
                    selfHurt,"trade",5,tick,1);
                if(gotReady)ok(target && targetHurt==0,
                    "ready never attacks damage-invulnerable target "+seed+"/"+tick);
                if(gotTrade)ok(target && targetHurt<=3 && selfHurt>=5,
                    "trade requires both relevant hurt windows "+seed+"/"+tick);
                prevHurt=selfHurt;
            }
        }
    }
    private static void profileRoundtrip() throws Exception {
        Path root=Files.createTempDirectory("ghostc-r333-combat-modes-");
        HostRuntime h=new HostRuntime(root);
        ok("interval".equals(h.blockMode()) && "normal".equals(h.hitMode()) &&
           h.hitTradeMinSelfHurt()==1,"original scheduling defaults preserved");
        h.worldPresence(true);
        h.uiActions().cycle("combat.blockhit","mode");
        h.uiActions().cycle("combat.hitselect","mode");
        h.uiActions().fraction("combat.hitselect","trade_min_self_hurt",0.5);
        ok("post_attack".equals(h.blockMode()) && "ready".equals(h.hitMode()) &&
           h.hitTradeMinSelfHurt()>=5,
           "all new settings driven by real GUI actions");
        h.uiActions().cycle("combat.blockhit","mode");
        h.uiActions().cycle("combat.hitselect","mode");
        ok("on_damage".equals(h.blockMode()) && "trade".equals(h.hitMode()),
           "mode cycles reach damage and trade policies");
        final int threshold=h.hitTradeMinSelfHurt();
        h.close();
        HostRuntime loaded=new HostRuntime(root);
        ok("on_damage".equals(loaded.blockMode()) &&
           "trade".equals(loaded.hitMode()) &&
           loaded.hitTradeMinSelfHurt()==threshold,
           "both mode families persist through typed, sealed profile roundtrip");
        loaded.uiActions().cycle("combat.blockhit","mode");
        loaded.uiActions().cycle("combat.hitselect","mode");
        ok("interval".equals(loaded.blockMode()) && "normal".equals(loaded.hitMode()),
           "original behavior remains one UI selection away");
        loaded.close();
    }
    private static void ownerFencing() throws Exception {
        final BlockHitPolicy block=new BlockHitPolicy();
        final HitSelectPolicy hit=new HitSelectPolicy();
        AtomicBoolean b=new AtomicBoolean(false),h=new AtomicBoolean(false);
        Thread outsider=new Thread(()->{
            try {block(block,"post_attack",0,false,0,1);}
            catch(IllegalStateException expected){b.set(true);}
            try {hit.accept(true,true,0,2,2,0,"normal",1,0,1);}
            catch(IllegalStateException expected){h.set(true);}
        });
        outsider.start();outsider.join();
        ok(b.get() && h.get(),"both advanced modes remain owner-thread only");
    }
    public static void main(String[] args) throws Exception {
        blockModes();
        hitModes();
        traceReplay();
        profileRoundtrip();
        ownerFencing();
        ok(checks>100,"multiple independent combat mode contract checks");
        System.out.println("COMBAT_MODES_PASS assertions="+checks);
    }
}
