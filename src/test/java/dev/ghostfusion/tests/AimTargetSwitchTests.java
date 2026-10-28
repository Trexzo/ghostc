package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.51: only consecutive live-UUID candidates can change Aim Assist's
 * authoritative camera target. Never retains a stale old camera write.
 * Java policy CI is NOT a launched native Minecraft acceptance test.
 */
public final class AimTargetSwitchTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++; if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable task,String why) {
        boolean denied=false;
        try {task.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static UUID uuid(int n) {
        return new UUID(0x123456789abcdefL,n+42L);
    }
    private static void exactSwitches() {
        AimTargetSwitchPolicy p=new AimTargetSwitchPolicy();
        UUID a=uuid(1),b=uuid(2),replacement=uuid(3);
        ok(p.consider(1,0,7,a,3),"first target has no initial delay");
        rejects(()->p.applied(1,0,7,b),"commit requires exact native target UUID");
        p.applied(1,0,7,a);
        rejects(()->p.applied(1,0,7,a),"same tick cannot commit twice");
        ok(p.consider(1,1,7,a,3),"same player can continue moving camera");
        p.applied(1,1,7,a);
        ok(!p.consider(1,2,8,b,3),"switch to B does not snap camera");
        ok(!p.consider(1,3,8,b,3),"B must remain selected");
        ok(!p.consider(1,4,8,b,3),"B still below exact delay");
        ok(p.consider(1,5,8,b,3),"B can switch at exact delay");
        p.cancelPrepared();
        rejects(()->p.applied(1,5,8,b),"denied rotation cannot commit B");
        ok(p.consider(1,6,8,b,3),"B's stable dwell survives denied write");
        p.applied(1,6,8,b);
        ok(!p.consider(1,7,7,a,3),"switch back to A independently delayed");
        ok(!p.consider(1,8,7,a,3),"A must reacquire");
        ok(!p.consider(1,9,7,a,3),"A's old commit does not bypass dwell");
        ok(p.consider(1,10,7,a,3),"A becomes eligible after stable dwell");
        p.applied(1,10,7,a);
        ok(!p.consider(1,11,7,replacement,3),
            "reused entity ID with different UUID is a new target");
        ok(p.consider(1,12,7,replacement,0),
            "zero delay preserves immediate legacy target switching");
        p.applied(1,12,7,replacement);
        ok(p.consider(2,0,99,b,3),"new world has no stale target-switch wait");
        p.applied(2,0,99,b);
        ok(!p.consider(2,1,7,a,3),"new world B to A switch is delayed");
        p.reset();
        rejects(()->p.applied(2,1,7,a),"Panic cannot commit stale candidate");
        ok(p.consider(2,2,7,a,3),"post Panic starts a new first target");
        p.applied(2,2,7,a);
        rejects(()->p.consider(2,1,7,a,3),"monotonic owner tick required");
        rejects(()->p.consider(2,3,-1,a,3),"invalid native ID refused");
        rejects(()->p.consider(2,3,7,null,3),"unknown native UUID refused");
        rejects(()->p.consider(2,3,7,a,11),"unbounded delay refused");
        AtomicBoolean denied=new AtomicBoolean();
        Thread other=new Thread(()->{
            try{p.consider(2,3,7,a,3);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        other.start();try{other.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(denied.get(),"foreign Netty/render thread cannot mutate camera owner");
    }
    private static void randomReplay() {
        AimTargetSwitchPolicy p=new AimTargetSwitchPolicy();
        Random r=new Random(3512026L);
        final int delay=3;
        int current=-1, pending=-1, pendingSince=-1;
        UUID currentUuid=null, pendingUuid=null;
        long epoch=1;
        for(int tick=0;tick<50000;tick++) {
            if(tick%701==0) {
                p.reset();
                current=pending=pendingSince=-1;
                currentUuid=pendingUuid=null;
                epoch++;
            }
            int candidate=r.nextInt(5)-1;
            if(candidate<0) {
                p.reset();
                current=pending=pendingSince=-1;
                currentUuid=pendingUuid=null;
                continue;
            }
            // IDs intentionally repeat with independently changing identities.
            UUID id=uuid(candidate+(tick/137)*10);
            boolean expected;
            if(currentUuid==null || (candidate==current&&id.equals(currentUuid))) {
                expected=true;
                pending=-1;pendingSince=-1;pendingUuid=null;
            }else if(pending!=candidate || !id.equals(pendingUuid)) {
                pending=candidate;pendingUuid=id;pendingSince=tick;
                expected=false;
            }else expected=tick-pendingSince>=delay;
            boolean actual=p.consider(epoch,tick,candidate,id,delay);
            ok(actual==expected,"50k independent UUID candidate oracle "+tick);
            if(actual) {
                if(r.nextInt(4)==0) {
                    p.cancelPrepared();
                    // Native rejection cannot change the committed target.
                }else{
                    p.applied(epoch,tick,candidate,id);
                    current=candidate;currentUuid=id;
                    pending=-1;pendingSince=-1;pendingUuid=null;
                }
            }
        }
    }
    private static void persistedProfile() throws Exception {
        Path root=Files.createTempDirectory("ghostc-r351-switch");
        HostRuntime host=new HostRuntime(root);
        ok(host.aimSwitchDelayTicks()==0,"legacy aim target switch is default");
        host.worldPresence(true);
        ok(host.modules().get("combat.aimassist").settings().containsKey(
            "switch_delay_ticks"),"real COMBAT module registers switch setting");
        host.uiActions().fraction("combat.aimassist","switch_delay_ticks",.5);
        ok(host.aimSwitchDelayTicks()==5,"F7 type-safe slider changes native delay");
        host.close();
        HostRuntime loaded=new HostRuntime(root);
        ok(loaded.aimSwitchDelayTicks()==5,"profile roundtrip preserves delay");
        loaded.worldPresence(true);
        loaded.uiActions().toggle("combat.aimassist");
        ok(loaded.featureEnabled("combat.aimassist"),"Aim Assist can enable normally");
        dev.ghostfusion.host.PanicController.disableAll(loaded);
        ok(!loaded.featureEnabled("combat.aimassist"),
            "Panic disables Aim Assist without implicit reenable");
        loaded.close();
        HostRuntime after=new HostRuntime(root);
        ok(after.aimSwitchDelayTicks()==5,"Panic preserves configured switch delay");
        ok(!after.featureEnabled("combat.aimassist"),
            "Aim Assist remains off after Panic persistence");
        after.close();
    }
    public static void main(String[] args) throws Exception {
        exactSwitches();
        randomReplay();
        persistedProfile();
        ok(checks>30000,"switch policy and native-commit oracle coverage");
        System.out.println("AIM_TARGET_SWITCH_PASS assertions="+checks);
    }
}
