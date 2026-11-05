package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.ProjectileManualInputGate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.85: live projectile/manual-hotbar suspension discards Block Hit pending
 * USE/event intent, reobserves physical input on resume, and retains only
 * a previously committed native USE cooldown (not speculative/vanilla).
 * Headless pure-Java owner-thread evidence; no Minecraft/server output.
 */
public final class BlockHitTransientSuspensionTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static boolean preview(BlockHitPolicy p,long epoch,long tick,
                                   String mode,int gap,int hurt,boolean attack) {
        return p.preview(true,true,false,true,true,false,attack,
            gap,hurt,mode,tick,epoch);
    }
    private static void rejectedCommit(BlockHitPolicy p,long tick,long epoch) {
        boolean denied=false;
        try {p.committed(tick,epoch);}
        catch(IllegalStateException expected){denied=true;}
        ok(denied,"only an actual completed Minecraft native USE can commit");
    }

    private static void exact() throws Exception {
        BlockHitPolicy interval=new BlockHitPolicy();
        ok(!preview(interval,1,0,"interval",10,0,false),
            "first manual held-key baseline is not synthetic USE");
        ok(preview(interval,1,10,"interval",10,0,false),
            "initial optional interval USE stages at original boundary");
        interval.committed(10,1);
        interval.suspendTransient(); // start rod SELECT
        interval.suspendTransient(); // next snowball/rod RESTORE frame
        ok(!preview(interval,1,13,"interval",10,0,false),
            "first resumed END tick observes input but cannot emit native USE");
        for(int t=14;t<20;t++)
            ok(!preview(interval,1,t,"interval",10,0,false),
                "previous completed USE gap still governs after hotbar "+t);
        ok(preview(interval,1,20,"interval",10,0,false),
            "prior genuine native USE boundary is preserved, not restarted");
        interval.committed(20,1);
        ok(!preview(interval,1,21,"interval",10,0,false),
            "newly completed USE enters original shared interval");

        BlockHitPolicy vanillaOnly=new BlockHitPolicy();
        ok(!preview(vanillaOnly,2,0,"interval",10,0,false),
            "initial held-key baseline has no native USE receipt");
        vanillaOnly.suspendTransient();
        ok(!preview(vanillaOnly,2,5,"interval",10,0,false),
            "resume after vanilla-only observation must seed new baseline");
        for(int t=6;t<15;t++)
            ok(!preview(vanillaOnly,2,t,"interval",10,0,false),
                "vanilla baseline cannot masquerade as committed native USE "+t);
        ok(preview(vanillaOnly,2,15,"interval",10,0,false),
            "never-committed baseline restarts from the first resumed hold");
        vanillaOnly.cancelPrepared();

        BlockHitPolicy uncommitted=new BlockHitPolicy();
        ok(!preview(uncommitted,3,0,"interval",2,0,false),
            "initial interval starts with vanilla");
        ok(preview(uncommitted,3,2,"interval",2,0,false),
            "speculative native USE has been staged but NOT dispatched");
        uncommitted.suspendTransient();
        rejectedCommit(uncommitted,2,3);
        ok(!preview(uncommitted,3,3,"interval",2,0,false),
            "pending USE from before projectile is never replayed on resume");
        ok(!preview(uncommitted,3,4,"interval",2,0,false),
            "one-tick rearm cannot borrow pre-projectile speculation");
        ok(preview(uncommitted,3,5,"interval",2,0,false),
            "new interval may stage only after fresh held-input baseline");
        uncommitted.committed(5,3);

        BlockHitPolicy post=new BlockHitPolicy();
        ok(!preview(post,4,0,"post_attack",2,0,true),
            "confirmed attack at t0 arms future event");
        ok(preview(post,4,1,"post_attack",2,0,false),
            "post-attack USE stages before hotbar change");
        post.suspendTransient();
        rejectedCommit(post,1,4);
        ok(!preview(post,4,2,"post_attack",2,0,false),
            "first post-projectile frame does not replay old attack event");
        ok(!preview(post,4,3,"post_attack",2,0,false),
            "stale post-attack pending event has been discarded");
        ok(!preview(post,4,4,"post_attack",2,0,true),
            "only a new confirmed attack creates a new pending event");
        ok(preview(post,4,5,"post_attack",2,0,false),
            "new post-attack event may stage following genuine attack");
        post.committed(5,4);
        post.suspendTransient();
        ok(!preview(post,4,6,"post_attack",2,0,false),
            "completed USE with no new attack cannot replay");
        ok(!preview(post,4,7,"post_attack",2,0,false),
            "completed USE cooldown alone cannot fabricate post-attack");
        ok(!preview(post,4,8,"post_attack",2,0,true),
            "new confirmed post-attack event arms following suspension");
        ok(preview(post,4,9,"post_attack",2,0,false),
            "fresh event obeys original completed USE cadence");
        post.committed(9,4);

        BlockHitPolicy damage=new BlockHitPolicy();
        ok(!preview(damage,5,0,"on_damage",2,0,false),
            "damage baseline begins at zero");
        ok(!preview(damage,5,1,"on_damage",2,10,false),
            "real rising damage queues one future event");
        damage.suspendTransient();
        ok(!preview(damage,5,3,"on_damage",2,8,false),
            "resume at nonzero hurtTime is passive baseline");
        ok(!preview(damage,5,4,"on_damage",2,8,false),
            "same hurtTime after resume cannot fabricate new damage");
        ok(!preview(damage,5,5,"on_damage",2,10,false),
            "only a genuinely new rising hurtTime arms reactive USE");
        ok(preview(damage,5,6,"on_damage",2,9,false),
            "fresh on_damage event may emit on subsequent eligible tick");
        damage.committed(6,5);

        ProjectileManualInputGate human=new ProjectileManualInputGate();
        human.mark();
        human.mark();
        ok(human.take() && !human.take(),
            "one actual hotbar key/wheel veto owns one END frame");
        ok(ProjectileCombatHandoff.reserved(
                ProjectileOwnerArbiter.Owner.ROD,false,false) &&
           ProjectileCombatHandoff.reserved(
                ProjectileOwnerArbiter.Owner.SNOWBALL,false,false),
            "both projectile SELECT/USE/RESTORE paths are exclusive");
        ok(!ProjectileCombatHandoff.reserved(
                ProjectileOwnerArbiter.Owner.NONE,false,false),
            "first genuinely unowned frame may resume combat");
        BlockHitPolicy lifecycle=new BlockHitPolicy();
        ok(!preview(lifecycle,7,0,"interval",3,0,false),
            "fresh world enters vanilla baseline");
        ok(preview(lifecycle,7,3,"interval",3,0,false),
            "original world stages native USE");
        lifecycle.committed(3,7);
        lifecycle.suspendTransient();
        lifecycle.reset();
        ok(!preview(lifecycle,8,4,"interval",3,0,false),
            "hard world/Panic reset cannot preserve old native USE receipt");
        ok(!preview(lifecycle,8,5,"interval",3,0,false),
            "new session cannot replay old cooldown/pending use");
        ok(!preview(lifecycle,8,6,"interval",3,0,false),
            "new world remains on vanilla cadence");
        ok(preview(lifecycle,8,7,"interval",3,0,false),
            "new world earns independent interval after full teardown");
        lifecycle.cancelPrepared();

        AtomicBoolean foreignDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{lifecycle.suspendTransient();}
            catch(IllegalStateException expected){foreignDenied.set(true);}
        });foreign.start();foreign.join();
        ok(foreignDenied.get(),"foreign Netty thread cannot mutate suspension state");
    }

    private static void replay() {
        Random rng=new Random(3852026L);
        String[] modes={"interval","post_attack","on_damage"};
        int outputs=0,noCommit=0,events=0;
        for(int i=0;i<90000;i++) {
            final long epoch=i+20L;
            final int gap=2+rng.nextInt(19);
            final String mode=modes[rng.nextInt(3)];
            final boolean committed=rng.nextBoolean();
            final int pause=1+rng.nextInt(10);
            BlockHitPolicy p=new BlockHitPolicy();
            long initialPulse;
            if("interval".equals(mode)) {
                ok(!preview(p,epoch,0,mode,gap,0,false),
                    "90k interval vanilla held-key seed "+i);
                initialPulse=gap;
                ok(preview(p,epoch,initialPulse,mode,gap,0,false),
                    "90k initial interval native USE stage "+i);
            } else if("post_attack".equals(mode)) {
                ok(!preview(p,epoch,0,mode,gap,0,true),
                    "90k confirmed attack arms post event "+i);
                initialPulse=1;
                ok(preview(p,epoch,initialPulse,mode,gap,0,false),
                    "90k post-attack first native USE stage "+i);
            } else {
                ok(!preview(p,epoch,0,mode,gap,0,false),
                    "90k damage baseline is passive "+i);
                ok(!preview(p,epoch,1,mode,gap,10,false),
                    "90k fresh damage edge arms use "+i);
                initialPulse=2;
                ok(preview(p,epoch,initialPulse,mode,gap,9,false),
                    "90k damage event first native USE stage "+i);
            }
            if(committed) {
                p.committed(initialPulse,epoch);
                outputs++;
            } else noCommit++;

            for(int d=0;d<pause;d++) {
                // No combat module tick while rod/snowball or human slot
                // event owns this END frame. Multiple suspensions are safe.
                p.suspendTransient();
            }
            if(!committed)rejectedCommit(p,initialPulse,epoch);
            long resume=initialPulse+pause+1;
            int hurtOnResume="on_damage".equals(mode)?10:0;
            ok(!preview(p,epoch,resume,mode,gap,hurtOnResume,false),
                "90k first resume tick never emits USE "+i);
            if("interval".equals(mode)) {
                long earliest=committed
                    ? Math.max(resume+1,initialPulse+gap)
                    : resume+gap;
                for(long tick=resume+1;tick<=earliest;tick++) {
                    boolean due=preview(p,epoch,tick,mode,gap,0,false);
                    ok(due==(tick==earliest),
                        "90k interval earliest eligible native USE oracle "+i+"/"+tick);
                    if(due) {
                        p.committed(tick,epoch);
                        ok(!preview(p,epoch,tick,mode,gap,0,false),
                            "90k duplicate output tick cannot emit USE "+i);
                    }
                }
                ok(!preview(p,epoch,earliest+1,mode,gap,0,false),
                    "90k committed interval must reenter cooldown "+i);
            } else {
                // The event before suspension must be gone. For damage
                // a nonzero hurt baseline cannot be mistaken for a rise.
                ok(!preview(p,epoch,resume+1,mode,gap,
                    "on_damage".equals(mode)?9:0,false),
                    "90k no old queued event can replay "+i);
                ok(!preview(p,epoch,resume+2,mode,gap,
                    "on_damage".equals(mode)?8:0,false),
                    "90k old event remains absent "+i);
                long sourceTick=resume+3;
                boolean newAttack="post_attack".equals(mode);
                ok(!preview(p,epoch,sourceTick,mode,gap,
                    "on_damage".equals(mode)?10:0,newAttack),
                    "90k fresh event arms but cannot emit same tick "+i);
                long earliest=committed
                    ? Math.max(sourceTick+1,initialPulse+gap)
                    : sourceTick+1;
                boolean emitted=false;
                for(long tick=sourceTick+1;tick<=sourceTick+4;tick++) {
                    boolean due=preview(p,epoch,tick,mode,gap,
                        "on_damage".equals(mode)?9:0,false);
                    boolean expect=earliest<=sourceTick+3 && tick==earliest;
                    ok(due==expect,
                        "90k genuine event expiration / cooldown oracle "+i+"/"+tick);
                    if(due) {
                        emitted=true;
                        p.committed(tick,epoch);
                    }
                }
                if(emitted)events++;
                ok(!preview(p,epoch,sourceTick+5,mode,gap,
                    "on_damage".equals(mode)?8:0,false),
                    "90k no stale post-event output after original TTL "+i);
            }
        }
        ok(outputs>30000 && noCommit>30000 && events>10000,
            "all committed, never-committed and post-suspend event modes covered");
        ok(checks>360000,"90k seeded Block Hit transient native cooldown traces executed");
    }
    public static void main(String[] args) throws Exception {
        exact();replay();
        System.out.println("BLOCKHIT_TRANSIENT_SUSPENSION_PASS assertions="+checks);
    }
}
