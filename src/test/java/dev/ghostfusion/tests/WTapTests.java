package dev.ghostfusion.tests;

import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.features.WTapPolicy.Decision;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.ModuleRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real-event sprint-reset state and owner profile integration.
 * Native Forge event delivery and server knockback require in-game testing.
 */
public final class WTapTests {
    private static int n;
    private static void ok(boolean result,String reason) {
        n++;if(!result)throw new AssertionError(reason);
    }
    private static void rejects(Runnable task,String reason) {
        boolean failed=false;
        try {task.run();} catch(RuntimeException expected){failed=true;}
        ok(failed,reason);
    }
    private static void lifecycle() {
        WTapPolicy p=new WTapPolicy();
        ok(p.tick(true,true,0,1)==Decision.NONE,"default no sprint modification");
        ok(!p.attack(true,true,false,1,0,1),"non-sprinting player not forced to reset");
        ok(!p.attack(false,true,true,1,0,1),"disabled combat cannot reset sprint");
        ok(!p.attack(true,false,true,1,0,1),"blocked movement cannot reset sprint");
        ok(p.attack(true,true,true,2,1,1),"real native eligible attack resets sprint");
        ok(!p.attack(true,true,true,2,1,1),"same-tick duplicate attack rejected");
        ok(p.tick(true,true,1,1)==Decision.HOLD,"attack tick holds sprint off");
        ok(p.tick(true,true,2,1)==Decision.HOLD,"configured one extra tick off");
        ok(p.tick(true,true,3,1)==Decision.RESTORE,"reengage sprint at exactly tick+2");
        ok(p.tick(true,true,3,1)==Decision.NONE,"restoration cannot replay");
        ok(p.attack(true,true,true,1,4,1),"next attack after restoration allowed");
        ok(p.tick(true,false,5,1)==Decision.NONE,"releasing forward cancels restore");
        ok(p.tick(true,true,6,1)==Decision.NONE,"forward reentry never replays old event");
        ok(p.attack(true,true,true,1,6,1),"eligible attack can re-arm");
        ok(p.tick(false,true,7,1)==Decision.NONE,"disable resets pending action");
        ok(p.tick(true,true,8,1)==Decision.NONE,"reenable cannot synthesize a new attack");
        ok(p.attack(true,true,true,3,9,1),"third window arms");
        ok(p.tick(true,true,1,2)==Decision.NONE,"world change cancels original pending sprint");
        ok(p.attack(true,true,true,1,2,2),"new world can receive genuine fresh attack");
        ok(p.tick(true,true,3,2)==Decision.RESTORE,"world2 reengages independently");
        rejects(()->p.attack(true,true,true,0,4,2),"zero delay rejected");
        rejects(()->p.attack(true,true,true,4,4,2),"too-long delay rejected");
        rejects(()->p.attack(true,true,true,1,-1,2),"negative tick rejected");
        rejects(()->p.attack(true,true,true,1,4,-1),"negative epoch rejected");
        rejects(()->p.tick(true,true,2,2),"backwards tick rejected");
        p.reset();
        ok(p.tick(true,true,0,3)==Decision.NONE,"Panic hard reset forgets pending event");
        rejects(()->p.attack(true,true,true,3,Long.MAX_VALUE-1,3),
            "sprint restoration expiry arithmetic never overflows");
    }
    private static void randomized() {
        for(int seed=0;seed<65;seed++) {
            Random rng=new Random(3372026L+seed*41);
            WTapPolicy p=new WTapPolicy();
            long restoreAt=-1,lastAttack=-1;
            for(long tick=0;tick<400;tick++) {
                boolean active=rng.nextInt(10)!=0;
                boolean eligible=rng.nextInt(8)!=0;
                int delay=1+rng.nextInt(3);
                Decision expected;
                if(!active || !eligible) {
                    restoreAt=-1;expected=Decision.NONE;
                } else if(restoreAt<0) expected=Decision.NONE;
                else if(tick<restoreAt)expected=Decision.HOLD;
                else {
                    restoreAt=-1;expected=Decision.RESTORE;
                }
                Decision real=p.tick(active,eligible,tick,1);
                ok(real==expected,"26k sprint restart timing oracle "+seed+"/"+tick);
                boolean event=rng.nextBoolean();
                if(event) {
                    boolean sprinting=rng.nextInt(4)!=0;
                    boolean accept=active && eligible && sprinting &&
                        restoreAt<0 && lastAttack!=tick;
                    boolean got=p.attack(active,eligible,sprinting,delay,tick,1);
                    ok(got==accept,"native attack eligibility and cooldown "+seed+"/"+tick);
                    if(got) {
                        restoreAt=tick+delay;lastAttack=tick;
                        ok(!p.attack(active,eligible,sprinting,delay,tick,1),
                            "duplicate native attack cannot restart same tick");
                    }
                }
            }
        }
    }
    private static void hostIntegration() throws Exception {
        WTapPolicy state=new WTapPolicy();
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try {state.tick(true,true,0,1);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        worker.start();worker.join();
        ok(refused.get(),"WTap policy never runs across owner threads");
        Path dir=Files.createTempDirectory("ghostc-wtap-");
        HostRuntime host=new HostRuntime(dir);
        String id="combat.wtap";
        ok(host.modules().get(id).state()==ModuleRegistry.State.DISABLED,
            "true native WTap module disabled by default");
        ok(host.wtapDelayTicks()==1,"one tick default reset preserves normal attacks");
        host.worldPresence(true);
        host.uiActions().fraction(id,"reset_ticks",1.0);
        host.uiActions().toggle(id);
        ok(host.featureEnabled(id) && host.wtapDelayTicks()==3,
            "WTap GUI setting controls actual delay");
        host.close();
        HostRuntime loaded=new HostRuntime(dir);
        loaded.worldPresence(true);
        ok(loaded.featureEnabled(id) && loaded.wtapDelayTicks()==3,
            "WTap profile restore retains module plus delay");
        loaded.uiActions().toggle(id);
        loaded.close();
        HostRuntime disabled=new HostRuntime(dir);
        disabled.worldPresence(true);
        ok(!disabled.featureEnabled(id) && disabled.wtapDelayTicks()==3,
            "WTap explicit disable persists without losing timing");
        disabled.close();
    }
    public static void main(String[] args) throws Exception {
        lifecycle();
        randomized();
        hostIntegration();
        ok(n>=26000,"26k restart oracle and native event simulation");
        System.out.println("WTAP_PASS assertions="+n);
    }
}
