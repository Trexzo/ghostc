package dev.ghostfusion.tests;

import dev.ghostfusion.features.ProjectileObservationWindow;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import dev.ghostfusion.features.ProjectileObservationWindow.Outcome;
import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only post-use observation evidence, never a server success claim. */
public final class ProjectileObservationTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    private static void reject(Runnable r,String why) {
        boolean rejected=false;
        try {r.run();}catch(RuntimeException expected){rejected=true;}
        ok(rejected,why);
    }
    private static void exact() throws Exception {
        ProjectileObservationWindow rod=new ProjectileObservationWindow();
        ProjectileObservationWindow snow=new ProjectileObservationWindow();
        ok(rod.observe(1,1,true,Signal.ROD_HOOK_VISIBLE)==Outcome.NONE,
           "unarmed client hook never claims evidence");
        rod.arm(1,10);
        ok(rod.observe(1,10,true,Signal.ROD_HOOK_VISIBLE)==Outcome.PENDING,
           "same tick as native sendUseItem never confirms observation");
        ok(rod.observe(1,11,true,Signal.NONE)==Outcome.PENDING,
           "no early hook observation still pending");
        ok(rod.observe(1,12,true,Signal.ROD_HOOK_VISIBLE)==Outcome.LOCAL_HOOK,
           "delayed current-player hook is a local-only observation");
        ok(!rod.pending() &&
           rod.observe(1,13,true,Signal.ROD_HOOK_VISIBLE)==Outcome.NONE,
           "same hook cannot be counted multiple times");
        snow.arm(2,20);
        ok(snow.observe(2,21,true,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.LOCAL_DECREASE,
           "delayed decreased stack locally observed");
        ok(snow.observe(2,22,true,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.NONE,
           "no repeated local snowball count");
        snow.arm(2,30);
        ok(snow.observe(2,31,true,Signal.INVENTORY_REPLACED)==Outcome.AMBIGUOUS_REPLACEMENT,
           "unrelated inventory replacement never counted as projectile use");
        ok(snow.observe(2,32,true,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.NONE,
           "ambiguous replacement terminates evidence window");
        snow.arm(2,40);
        for(int i=1;i<=ProjectileObservationWindow.MAX_TICKS;i++)
            ok(snow.observe(2,40+i,true,Signal.NONE)==Outcome.PENDING,
               "absence of server-visible evidence does not imply failed native use");
        ok(snow.observe(2,49,true,Signal.NONE)==Outcome.EXPIRED,
           "bounded observation expires without modifying hotbar or retrying");
        ok(!snow.pending(),"expired window releases all pending observation");
        rod.arm(3,60);
        ok(rod.observe(4,1,false,Signal.ROD_HOOK_VISIBLE)==Outcome.INVALIDATED,
           "world replacement invalidates before backward-tick check");
        rod.arm(5,10);
        ok(rod.observe(5,11,false,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.INVALIDATED,
           "player recreation invalidates even with plausible item signal");
        rod.arm(6,20);
        reject(()->rod.observe(6,19,true,Signal.NONE),
               "same generation backward observation is denied");
        reject(()->rod.observe(6,21,true,null),"null client signal denied");
        rod.reset();
        ok(!rod.pending()&&rod.observe(6,21,true,Signal.ROD_HOOK_VISIBLE)==Outcome.NONE,
           "explicit lifecycle reset erases pending evidence");
        reject(()->rod.arm(-1,0),"negative epoch denied");
        reject(()->rod.arm(1,-1),"negative tick denied");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try {rod.observe(1,21,true,Signal.NONE);}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        foreign.start();foreign.join();
        ok(blocked.get(),"foreign world/network thread cannot mutate native evidence");
    }
    private static void lifecycleWithoutHotbarOwnership() {
        AutoRodPolicy rodPolicy=new AutoRodPolicy();
        ProjectileObservationWindow observation=new ProjectileObservationWindow();
        rodPolicy.decide(9,0,false,true,2,6,30);
        ok(rodPolicy.decide(9,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
           "native rod selection armed");
        rodPolicy.committed(AutoRodPolicy.Action.SELECT,9,1);
        ok(rodPolicy.decide(9,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
           "native rod use armed next tick");
        rodPolicy.committed(AutoRodPolicy.Action.USE,9,2);
        observation.arm(9,2);
        ok(rodPolicy.decide(9,3,false,true,6,6,30)==AutoRodPolicy.Action.RESTORE,
           "native rod restoration is not delayed by observations");
        rodPolicy.committed(AutoRodPolicy.Action.RESTORE,9,3);
        ok(!rodPolicy.ownsHotbar()&&observation.pending(),
           "observation remains pending after hotbar is safely restored");
        ok(observation.observe(9,3,true,Signal.NONE)==Outcome.PENDING,
           "post-restore local hook not yet observed");
        ok(observation.observe(9,4,true,Signal.ROD_HOOK_VISIBLE)==Outcome.LOCAL_HOOK,
           "hook observed only after rod module released hotbar");
        AutoSnowballPolicy snowPolicy=new AutoSnowballPolicy();
        snowPolicy.decide(11,0,false,true,2,5,20);
        snowPolicy.decide(11,1,true,true,2,5,20);
        snowPolicy.committed(AutoSnowballPolicy.Action.SELECT,11,1);
        snowPolicy.decide(11,2,true,true,5,5,20);
        snowPolicy.committed(AutoSnowballPolicy.Action.USE,11,2);
        observation.arm(11,2);
        snowPolicy.decide(11,3,false,true,5,5,20);
        snowPolicy.committed(AutoSnowballPolicy.Action.RESTORE,11,3);
        ok(!snowPolicy.ownsHotbar()&&observation.pending(),
           "snowball local inventory observation outlives selected slot");
        ok(observation.observe(11,4,true,Signal.SNOWBALL_COUNT_DECREASED)==Outcome.LOCAL_DECREASE,
           "delayed local inventory reconciliation does not extend hotbar lock");
    }
    private static void seeded() {
        ProjectileObservationWindow rod=new ProjectileObservationWindow();
        ProjectileObservationWindow snow=new ProjectileObservationWindow();
        Random rng=new Random(362189L);
        long hooks=0,counts=0,ambiguous=0,timeouts=0,invalid=0;
        for(int n=0;n<80000;n++) {
            ProjectileObservationWindow window=(n&1)==0?rod:snow;
            window.reset();
            long g=1+n/1499,tick=n+20;
            window.arm(g,tick);
            int variant=rng.nextInt(6);
            Outcome observed=Outcome.PENDING;
            for(int dt=1;dt<=9;dt++) {
                Signal signal=Signal.NONE;
                boolean same=true;
                long actualGen=g;
                if(dt==2) {
                    if(variant==0)signal=Signal.ROD_HOOK_VISIBLE;
                    if(variant==1)signal=Signal.SNOWBALL_COUNT_DECREASED;
                    if(variant==2)signal=Signal.INVENTORY_REPLACED;
                    if(variant==3)same=false;
                    if(variant==4)actualGen=g+1;
                }
                observed=window.observe(actualGen,tick+dt,same,signal);
                if(observed!=Outcome.PENDING)break;
            }
            Outcome expected=variant==0?Outcome.LOCAL_HOOK:
                variant==1?Outcome.LOCAL_DECREASE:
                variant==2?Outcome.AMBIGUOUS_REPLACEMENT:
                variant==3||variant==4?Outcome.INVALIDATED:Outcome.EXPIRED;
            ok(observed==expected,
               "80k seeded observation oracle for hook, count, inventory, world and expiry "+n);
            ok(!window.pending() &&
               window.observe(g,tick+10,true,Signal.ROD_HOOK_VISIBLE)==Outcome.NONE,
               "80k terminal windows never report duplicate observations "+n);
            if(observed==Outcome.LOCAL_HOOK)hooks++;
            if(observed==Outcome.LOCAL_DECREASE)counts++;
            if(observed==Outcome.AMBIGUOUS_REPLACEMENT)ambiguous++;
            if(observed==Outcome.EXPIRED)timeouts++;
            if(observed==Outcome.INVALIDATED)invalid++;
        }
        ok(hooks>10000&&counts>10000&&ambiguous>10000&&
           timeouts>10000&&invalid>20000,
           "80k replay exercises each distinct local evidence outcome");
        ok(checks>160000,"80k observation frames and postterminal properties checked");
    }
    public static void main(String[] args) throws Exception {
        exact();lifecycleWithoutHotbarOwnership();seeded();
        System.out.println("PROJECTILE_OBSERVATION_PASS assertions="+checks);
    }
}
