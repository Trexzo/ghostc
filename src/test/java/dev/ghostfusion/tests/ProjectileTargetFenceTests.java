package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.ProjectileTargetFence;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.59 target identity and next-tick projectile USE regression. */
public final class ProjectileTargetFenceTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    private static void rejects(Runnable run,String why) {
        boolean denied=false;
        try {run.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static void boundaries() throws Exception {
        ProjectileTargetFence fence=new ProjectileTargetFence();
        UUID a=new UUID(11,22), b=new UUID(33,44);
        ok(!fence.allowUse(1,1,a,7,true),
            "unselected player cannot trigger a projectile");
        fence.selected(1,5,a,7);
        rejects(()->fence.selected(1,5,b,8),
            "cannot overwrite unconsumed initial target");
        ok(!fence.allowUse(1,6,b,8,true),
            "crosshair switch to another valid target is rejected");
        ok(!fence.allowUse(1,6,a,7,true),
            "rejected target switch cannot be retried on same tick");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(!fence.allowUse(1,6,a,8,true),
            "same UUID but recreated native entity ID refused");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(!fence.allowUse(1,6,a,7,false),
            "same target out of range, invisible or dead refused");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(!fence.allowUse(2,6,a,7,true),
            "cross-world generation cannot borrow a pending selection");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(!fence.allowUse(1,7,a,7,true),
            "late two-tick USE is refused instead of replayed");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(!fence.allowUse(1,5,a,7,true),
            "same END tick cannot SELECT and USE");
        fence.reset();
        fence.selected(1,5,a,7);
        ok(fence.allowUse(1,6,a,7,true),
            "same player plus native ID, next tick and fresh validation permits USE");
        ok(!fence.allowUse(1,6,a,7,true),
            "one-shot native right click permission consumed");
        rejects(()->fence.selected(1,1,null,7),
            "null target UUID cannot be admitted");
        rejects(()->fence.selected(1,1,a,-1),
            "unassigned native entity cannot be admitted");
        AtomicBoolean denied=new AtomicBoolean();
        Thread other=new Thread(()->{
            try {fence.allowUse(1,6,a,7,true);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        other.start();other.join();
        ok(denied.get(),"foreign Netty thread cannot evaluate target fence");
    }
    private static void rodAndSnowball() {
        UUID original=new UUID(100,200);
        AutoRodPolicy rod=new AutoRodPolicy();
        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        ProjectileTargetFence rodTarget=new ProjectileTargetFence();
        ProjectileTargetFence snowTarget=new ProjectileTargetFence();
        ok(rod.decide(1,0,false,true,2,6,30)==AutoRodPolicy.Action.NONE,
            "rod release baseline");
        ok(rod.decide(1,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
            "rod selected on physical press");
        rod.committed(AutoRodPolicy.Action.SELECT,1,1);
        rodTarget.selected(1,1,original,42);
        ok(rod.decide(1,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
            "rod policy prepares USE but does not dispatch it");
        ok(!rodTarget.allowUse(1,2,new UUID(101,201),43,true),
            "new valid crosshair player cannot inherit rod permission");
        rod.cancelPrepared();
        ok(rod.decide(1,3,true,false,6,6,30)==AutoRodPolicy.Action.RESTORE,
            "rod target switch causes safe restore, not a throw");
        ok(!rod.castObserved(),"no synthetic rod cast count after canceled USE");
        rod.committed(AutoRodPolicy.Action.RESTORE,1,3);
        ok(!rod.ownsHotbar(),"rod releases original hotbar owner");

        ok(snow.decide(2,0,false,true,2,5,20)==AutoSnowballPolicy.Action.NONE,
            "snowball release baseline");
        ok(snow.decide(2,1,true,true,2,5,20)==AutoSnowballPolicy.Action.SELECT,
            "snowball selected on physical press");
        snow.committed(AutoSnowballPolicy.Action.SELECT,2,1);
        snowTarget.selected(2,1,original,42);
        ok(snow.decide(2,2,true,false,5,5,20)==AutoSnowballPolicy.Action.RESTORE,
            "snowball policy skips USE if original target left native range");
        ok(!snowTarget.allowUse(2,2,original,42,false),
            "fresh native range failure denies the original opponent");
        ok(!snow.castObserved(),"out-of-range snowball never locally used");
        snow.committed(AutoSnowballPolicy.Action.RESTORE,2,2);
        ok(!snow.ownsHotbar(),"snowball original selected slot released");
    }
    private static void seeded() {
        Random rng=new Random(359189L);
        ProjectileTargetFence gate=new ProjectileTargetFence();
        long accepted=0,denied=0;
        for(int t=0;t<80000;t++) {
            gate.reset();
            long epoch=1+t/1739;
            long tick=t+1;
            UUID target=new UUID(rng.nextLong(),rng.nextLong());
            int id=1+rng.nextInt(4096);
            gate.selected(epoch,tick,target,id);
            int variant=rng.nextInt(8);
            long useEpoch=variant==1?epoch+1:epoch;
            long useTick=(variant==2?tick:variant==3?tick+2:tick+1);
            UUID other=variant==4?
                new UUID(target.getMostSignificantBits()^1L,
                         target.getLeastSignificantBits()):target;
            int actualId=variant==5?id+1:id;
            boolean nativeValid=variant!=6&&variant!=7;
            boolean expected=variant==0;
            boolean granted=gate.allowUse(useEpoch,useTick,other,actualId,nativeValid);
            ok(granted==expected,
                "80k independently constructed target/timing oracle "+t);
            ok(!gate.allowUse(epoch,tick+1,target,id,true),
                "80k consumed target cannot throw twice "+t);
            if(granted)accepted++;else denied++;
        }
        ok(accepted>9000 && denied>65000,
            "seeded target changes, lateness, world change and range loss exercised");
        ok(checks>160000,"all strict target/tick gates checked in randomized replay");
    }
    public static void main(String[] args) throws Exception {
        boundaries();rodAndSnowball();seeded();
        System.out.println("PROJECTILE_TARGET_FENCE_PASS assertions="+checks);
    }
}
