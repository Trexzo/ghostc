package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.ProjectileManualInputGate;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.ProjectileOwnerArbiter.Owner;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Input and ownership oracle. No fake Minecraft world or gameplay claims. */
public final class ProjectileManualInputTests {
    private static long assertions;
    private static void ok(boolean truth,String why) {
        assertions++;
        if(!truth)throw new AssertionError(why);
    }
    private static void ownerAndCancellation() throws Exception {
        ProjectileManualInputGate inputs=new ProjectileManualInputGate();
        AutoRodPolicy rod=new AutoRodPolicy();
        AutoSnowballPolicy snow=new AutoSnowballPolicy();

        ok(!inputs.take()&&!inputs.pending(),
           "no invented manual hotbar event before input callback");
        // Rod takes a real edge and selects original hotbar slot 2 -> 6.
        ok(rod.decide(1,0,false,true,2,6,30)==AutoRodPolicy.Action.NONE,
           "rod seeds a release on first native tick");
        ok(rod.decide(1,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
           "physical edge proposes native rod selection");
        rod.committed(AutoRodPolicy.Action.SELECT,1,1);
        ok(rod.ownsHotbar(),"rod now owns an in-flight transaction");
        // A tap can move 6 -> 3 -> 6 *between* END ticks.
        // currentItem==6 alone would incorrectly restore/throw a rod.
        inputs.mark();
        inputs.mark();
        ok(inputs.pending() && inputs.signals()==2,
           "both rapid number-key/wheel events latched");
        ok(inputs.take()&&!inputs.take()&&inputs.consumedTicks()==1,
           "multiple events revoke exactly the next native output tick");
        ok(rod.relinquish(6,true,true)==-1&&!rod.ownsHotbar(),
           "manual tap prevents old slot restoration despite unchanged currentItem");

        // The same must hold for Snowball after SELECT, before USE.
        ok(snow.decide(2,0,false,true,1,5,20)==AutoSnowballPolicy.Action.NONE,
           "snowball establishes physical press baseline");
        ok(snow.decide(2,1,true,true,1,5,20)==AutoSnowballPolicy.Action.SELECT,
           "snowball owns one native select intent");
        snow.committed(AutoSnowballPolicy.Action.SELECT,2,1);
        ok(snow.ownsHotbar(),"native snowball selection committed");
        inputs.mark(); // a single wheel event need not change final selected slot
        ok(inputs.take(),"manual wheel event reached shared END tick");
        ok(snow.relinquish(5,true,true)==-1 && !snow.ownsHotbar(),
           "manual wheel aborts snowball without silent prior-slot overwrite");

        // Safe automatic rollback only in the *absence* of human input.
        rod.reset();
        rod.decide(3,0,false,true,2,6,30);
        rod.decide(3,1,true,true,2,6,30);
        rod.committed(AutoRodPolicy.Action.SELECT,3,1);
        ok(!inputs.take() && rod.relinquish(6,true,false)==2,
           "same world rod may safely restore when no manual event intervened");
        snow.reset();
        snow.decide(4,0,false,true,1,5,20);
        snow.decide(4,1,true,true,1,5,20);
        snow.committed(AutoSnowballPolicy.Action.SELECT,4,1);
        ok(snow.relinquish(5,false,false)==-1,
           "stale-world snowball always forbids a restore");

        inputs.mark();
        inputs.reset();
        ok(!inputs.take(),"world/GUI transition drops stale-world input events");

        AtomicBoolean failed=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try { inputs.mark(); }
            catch(IllegalStateException expected) {failed.set(true);}
        });
        foreign.start();foreign.join();
        ok(failed.get(),"foreign Netty/input thread cannot write owner latch");
    }
    private static void arbiter() {
        ok(ProjectileOwnerArbiter.observed(false,false)==Owner.NONE,
           "neither projectile currently owns hotbar");
        ok(ProjectileOwnerArbiter.observed(true,false)==Owner.ROD,
           "rod owner retained over competing new snowball press");
        ok(ProjectileOwnerArbiter.observed(false,true)==Owner.SNOWBALL,
           "snowball owner retained across future rod opportunity");
        ok(ProjectileOwnerArbiter.observed(true,true)==Owner.CONFLICT,
           "two simultaneously retained native hotbar owners fail closed");
        for(Owner o:Owner.values()) {
            ok(!ProjectileOwnerArbiter.mayStartRod(o,true),
               "human manual input preempts rod "+o);
            ok(!ProjectileOwnerArbiter.mayStartSnowball(o,true,false),
               "human manual input preempts snowball "+o);
        }
        ok(ProjectileOwnerArbiter.mayStartRod(Owner.NONE,false) &&
           ProjectileOwnerArbiter.mayStartRod(Owner.ROD,false) &&
           !ProjectileOwnerArbiter.mayStartRod(Owner.SNOWBALL,false) &&
           !ProjectileOwnerArbiter.mayStartRod(Owner.CONFLICT,false),
           "new rod opportunity never steals existing snowball ownership");
        ok(ProjectileOwnerArbiter.mayStartSnowball(Owner.NONE,false,false) &&
           ProjectileOwnerArbiter.mayStartSnowball(Owner.SNOWBALL,false,false) &&
           !ProjectileOwnerArbiter.mayStartSnowball(Owner.ROD,false,false) &&
           !ProjectileOwnerArbiter.mayStartSnowball(Owner.CONFLICT,false,false),
           "snowball never steals a rod transaction or both-owner fault");
        ok(!ProjectileOwnerArbiter.mayStartSnowball(Owner.NONE,false,true),
           "new simultaneous attack edge gives rod first refusal");
    }
    private static void deterministicReplay() {
        ProjectileManualInputGate gate=new ProjectileManualInputGate();
        Random rng=new Random(358189L);
        long physicalSignals=0,manualTicks=0;
        for(int tick=0;tick<80000;tick++) {
            boolean expected=false;
            int n=rng.nextInt(8)==0?1+rng.nextInt(4):0;
            for(int i=0;i<n;i++) {
                gate.mark();physicalSignals++;expected=true;
            }
            if(rng.nextInt(4049)==0) {
                gate.reset();expected=false;
            }
            ok(gate.pending()==expected,
               "80k pre-END native event latch equality "+tick);
            boolean observed=gate.take();
            if(observed)manualTicks++;
            ok(observed==expected && !gate.pending(),
               "80k event latch consumed once without cross-tick replay "+tick);
            // Exercise all no-owner, rod, snowball and conflict observations.
            boolean rod=rng.nextBoolean(),snow=rng.nextBoolean();
            Owner prior=ProjectileOwnerArbiter.observed(rod,snow);
            boolean rodStart=ProjectileOwnerArbiter.mayStartRod(prior,observed);
            boolean snowStart=ProjectileOwnerArbiter.mayStartSnowball(
                prior,observed,rodStart && rng.nextBoolean());
            ok(!(prior==Owner.CONFLICT && (rodStart||snowStart)),
               "80k collision always refuses optional native writes "+tick);
            ok(!observed||(!rodStart&&!snowStart),
               "80k human override cancels both projectile candidates "+tick);
            ok(!rod || !snow || prior==Owner.CONFLICT,
               "80k impossible owner never normalizes silently "+tick);
        }
        ok(gate.signals()==physicalSignals &&
           gate.consumedTicks()==manualTicks &&
           physicalSignals>manualTicks && manualTicks>1000,
           "80k seeded input events coalesce but never vanish");
        ok(assertions>400000,"comprehensive owner and rapid-event replay");
    }
    public static void main(String[] args) throws Exception {
        ownerAndCancellation();
        arbiter();
        deterministicReplay();
        System.out.println("PROJECTILE_MANUAL_INPUT_PASS assertions="+assertions);
    }
}
