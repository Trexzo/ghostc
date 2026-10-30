package dev.ghostfusion.tests;

import dev.ghostfusion.features.ProjectileHookProvenance;
import dev.ghostfusion.features.ProjectileObservationWindow;
import dev.ghostfusion.features.ProjectileObservationWindow.Outcome;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exact native hook identity evidence; no launched Minecraft or server ACK. */
public final class ProjectileHookProvenanceTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String why) {
        boolean bad=false;
        try {r.run();}catch(RuntimeException expected){bad=true;}
        ok(bad,why);
    }
    private static void boundaries() throws Exception {
        ProjectileHookProvenance gate=new ProjectileHookProvenance();
        Object player=new Object(),world=new Object(),hook=new Object();
        Object other=new Object(),otherWorld=new Object();
        ok(!gate.eligible(player,world,hook,player,world,hook,false),
           "unarmed client hook may not count");
        rejects(()->gate.arm(null,world,null),"no unbound player accepted");
        rejects(()->gate.arm(player,null,null),"no unbound world accepted");
        gate.arm(player,world,null);
        ok(gate.eligible(player,world,hook,player,world,hook,false),
           "new registered live hook of original player/world qualifies local evidence");
        ok(!gate.eligible(player,world,null,player,world,null,false),
           "null fishEntity cannot prove a hook");
        ok(!gate.eligible(other,world,hook,player,world,hook,false),
           "different local player cannot borrow a hook");
        ok(!gate.eligible(player,otherWorld,hook,player,world,hook,false),
           "world swap cannot borrow old hook");
        ok(!gate.eligible(player,world,hook,other,world,hook,false),
           "another angler's hook cannot be credited to this player");
        ok(!gate.eligible(player,world,hook,player,otherWorld,hook,false),
           "fishEntity pointing to another world is not a same-world hook");
        ok(!gate.eligible(player,world,hook,player,world,other,false),
           "same native hook ID but different world-registry object rejected");
        ok(!gate.eligible(player,world,hook,player,world,hook,true),
           "dead native hook cannot provide live evidence");
        gate.arm(player,world,hook);
        ok(!gate.eligible(player,world,hook,player,world,hook,false),
           "PREEXISTING hook must not be treated as newly created");
        ok(gate.eligible(player,world,other,player,world,other,false),
           "a fresh distinct hook may qualify after the baseline");
        gate.reset();
        ok(!gate.eligible(player,world,other,player,world,other,false),
           "reset removes the old world/player/hook reference");
        AtomicBoolean wrongThread=new AtomicBoolean();
        Thread t=new Thread(()->{
            try {gate.eligible(player,world,hook,player,world,hook,false);}
            catch(IllegalStateException expected){wrongThread.set(true);}
        });
        t.start();t.join();
        ok(wrongThread.get(),"network thread cannot mutate or sample owner hook");
    }
    private static void observationIsNotServerAck() {
        Object player=new Object(),world=new Object(),fresh=new Object();
        ProjectileHookProvenance gate=new ProjectileHookProvenance();
        ProjectileObservationWindow window=new ProjectileObservationWindow();
        gate.arm(player,world,null);
        window.arm(1,10);
        Signal sameTick=gate.eligible(player,world,fresh,player,world,fresh,false)?
            Signal.ROD_HOOK_VISIBLE:Signal.NONE;
        ok(window.observe(1,10,true,sameTick)==Outcome.PENDING,
           "same-tick client hook is not a delayed observation");
        ok(window.observe(1,11,true,Signal.NONE)==Outcome.PENDING,
           "no hook in next tick remains inconclusive");
        ok(window.observe(1,12,true,sameTick)==Outcome.LOCAL_HOOK,
           "exact same-world owned hook may yield LOCAL_HOOK, never server hit");
        ok(window.observe(1,13,true,sameTick)==Outcome.NONE,
           "single-hook evidence cannot be double counted");
        window.arm(2,20);
        gate.arm(player,world,fresh);
        ok(!gate.eligible(player,world,fresh,player,world,fresh,false),
           "old persistent hook is not a new cast");
        for(int d=1;d<=ProjectileObservationWindow.MAX_TICKS;d++)
            ok(window.observe(2,20+d,true,Signal.NONE)==Outcome.PENDING,
               "old hook not mistaken for new observation");
        ok(window.observe(2,29,true,Signal.ROD_HOOK_VISIBLE)==Outcome.EXPIRED,
           "expired window rejects even a late hook candidate");
    }
    private static void randomized() {
        Random rng=new Random(364189L);
        ProjectileHookProvenance gate=new ProjectileHookProvenance();
        Object p=new Object(),w=new Object(),current=new Object(),other=new Object();
        long allowed=0,blocked=0;
        for(int i=0;i<80000;i++) {
            gate.reset();
            Object baseline=rng.nextBoolean()?null:new Object();
            gate.arm(p,w,baseline);
            int variant=rng.nextInt(10);
            Object hook=variant==1?baseline:variant==2?null:current;
            Object nativePlayer=variant==3?other:p;
            Object nativeWorld=variant==4?other:w;
            Object angler=variant==5?other:p;
            Object hookWorld=variant==6?other:w;
            Object registry=variant==7?other:hook;
            boolean dead=variant==8;
            boolean expected=(variant==0||variant==9)&&hook!=baseline;
            boolean actual=gate.eligible(nativePlayer,nativeWorld,
                hook,angler,hookWorld,registry,dead);
            ok(actual==expected,"80k independent exact hook identity oracle "+i);
            if(actual)allowed++;else blocked++;
        }
        ok(allowed>12000&&blocked>60000,
           "80k native ownership/world/dead/registry/baseline negatives exercised");
        ok(checks>80000,"80k hook samples verified");
    }
    public static void main(String[] args) throws Exception {
        boundaries();observationIsNotServerAck();randomized();
        System.out.println("PROJECTILE_HOOK_PROVENANCE_PASS assertions="+checks);
    }
}
