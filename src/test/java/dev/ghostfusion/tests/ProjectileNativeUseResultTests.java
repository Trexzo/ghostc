package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.ProjectileInventoryFence;
import dev.ghostfusion.features.ProjectileNativeUseResult;
import dev.ghostfusion.features.ProjectileNativeUseResult.Outcome;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.host.ModuleActionTelemetry.Lane;
import dev.ghostfusion.host.ModuleActionTelemetry.Event;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native vanilla use RETURN classification, not server hit acceptance. */
public final class ProjectileNativeUseResultTests {
    private static long assertions;
    private static void ok(boolean yes,String why) {
        assertions++;
        if(!yes)throw new AssertionError(why);
    }
    private static void reject(Runnable r,String why) {
        boolean denied=false;
        try{r.run();}catch(IllegalArgumentException expected){denied=true;}
        ok(denied,why);
    }
    private static void truthTable() {
        ok(ProjectileNativeUseResult.classify(true,false,false)==Outcome.SPECTATOR_DENIED,
            "spectator cannot submit vanilla item use");
        ok(ProjectileNativeUseResult.classify(false,false,false)==Outcome.NOT_INVOKED,
            "preflight veto cannot invent a native item use");
        ok(ProjectileNativeUseResult.classify(false,true,false)==Outcome.SUBMITTED_UNCHANGED,
            "vanilla false means local stack UNCHANGED, not failed rod cast");
        ok(ProjectileNativeUseResult.classify(false,true,true)==Outcome.SUBMITTED_LOCAL_CHANGE,
            "vanilla true means local stack mutation, not server hit");
        ok(!Outcome.NOT_INVOKED.submitted()&&!Outcome.SPECTATOR_DENIED.submitted() &&
            Outcome.SUBMITTED_UNCHANGED.submitted()&&Outcome.SUBMITTED_LOCAL_CHANGE.submitted(),
            "only non-spectator actual native method invocation counts submitted");
        ok(!Outcome.SUBMITTED_UNCHANGED.localChange() &&
            Outcome.SUBMITTED_LOCAL_CHANGE.localChange(),
            "same-stack rod and depleted snowball remain distinct outcomes");
        reject(()->ProjectileNativeUseResult.classify(true,true,false),
            "calling original method in spectator mode violates controller guard");
        reject(()->ProjectileNativeUseResult.classify(true,false,true),
            "spectator could not mutate stack via a method never called");
        reject(()->ProjectileNativeUseResult.classify(false,false,true),
            "non-invoked native method cannot claim local item mutation");
    }
    private static void rodUnchanged() {
        AutoRodPolicy rod=new AutoRodPolicy();
        ProjectileInventoryFence fence=new ProjectileInventoryFence();
        ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        Object sword=new Object(),swordItem=new Object();
        Object stack=new Object(),rodItem=new Object();
        ok(rod.decide(1,0,false,true,2,6,30)==AutoRodPolicy.Action.NONE,
            "rod starts from observed released attack input");
        ok(rod.decide(1,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
            "valid original attack selects native fishing rod");
        rod.committed(AutoRodPolicy.Action.SELECT,1,1);
        ok(ledger.record(Lane.AUTOROD,Event.ATTEMPT),
            "native select begins one pending ledger transaction");
        fence.selected(1,1,2,6,sword,swordItem,1,0,stack,rodItem,1,0);
        ok(rod.decide(1,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
            "rod use follows committed selection");
        ok(fence.allowUse(1,2,2,6,sword,swordItem,1,0,stack,rodItem,1,0),
            "exact item permits vanilla use");
        // Vanilla source enqueues non-spectator C08 before returning false
        // for unchanged reference/count (the ordinary fishing rod path).
        Outcome outcome=ProjectileNativeUseResult.classify(false,true,false);
        ok(outcome.submitted()&&!outcome.localChange(),
            "unchanged local fishing rod must not be classified as cancelled");
        rod.committed(AutoRodPolicy.Action.USE,1,2);
        fence.nativeUsed(1,2);
        ok(ledger.record(Lane.AUTOROD,Event.DISPATCHED),
            "native call recorded without pretending server confirmation");
        ok(rod.decide(1,3,false,true,6,6,30)==AutoRodPolicy.Action.RESTORE,
            "unchanged fishing rod still requires old slot restoration");
        ok(fence.mayRestore(1,2,6,sword,swordItem,1,0,stack),
            "unchanged source+projectile preserve restoration");
        rod.committed(AutoRodPolicy.Action.RESTORE,1,3);
        ok(ledger.record(Lane.AUTOROD,Event.LOCAL_COMPLETE),
            "local restore ends submitted-but-unchanged use lifecycle");
        ok(!ledger.pending(Lane.AUTOROD) && !rod.ownsHotbar() &&
           ledger.count(Lane.AUTOROD,Event.CANCELLED)==0L,
            "false boolean never leaves phantom pending/cancelled rod cast");
    }
    private static void snowballDepletion() {
        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        ProjectileInventoryFence fence=new ProjectileInventoryFence();
        ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        Object sword=new Object(),swordItem=new Object();
        Object stack=new Object(),snowItem=new Object();
        ok(snow.decide(2,0,false,true,2,5,20)==AutoSnowballPolicy.Action.NONE,
            "snowball starts from release");
        ok(snow.decide(2,1,true,true,2,5,20)==AutoSnowballPolicy.Action.SELECT,
            "native snowball selected on physical edge");
        snow.committed(AutoSnowballPolicy.Action.SELECT,2,1);
        ok(ledger.record(Lane.AUTOSNOWBALL,Event.ATTEMPT),
            "snowball begins separate pending ledger lane");
        fence.selected(2,1,2,5,sword,swordItem,1,0,stack,snowItem,1,0);
        ok(snow.decide(2,2,true,true,5,5,20)==AutoSnowballPolicy.Action.USE,
            "selected snowball may be used next tick");
        ok(fence.allowUse(2,2,2,5,sword,swordItem,1,0,stack,snowItem,1,0),
            "source and one snowball unchanged before native use");
        Outcome outcome=ProjectileNativeUseResult.classify(false,true,true);
        ok(outcome.submitted()&&outcome.localChange(),
            "local depleted-snowball mutation is not a server-confirmed hit");
        snow.committed(AutoSnowballPolicy.Action.USE,2,2);
        fence.nativeUsed(2,2);
        ok(ledger.record(Lane.AUTOSNOWBALL,Event.DISPATCHED),
            "native snowball use invoked");
        ok(snow.decide(2,3,false,true,5,5,20)==AutoSnowballPolicy.Action.RESTORE,
            "depleted last snowball still requires safe restore");
        ok(fence.mayRestore(2,2,5,sword,swordItem,1,0,null),
            "vanilla last snowball removal accepted after native call");
        snow.committed(AutoSnowballPolicy.Action.RESTORE,2,3);
        ok(ledger.record(Lane.AUTOSNOWBALL,Event.LOCAL_CHANGE),
            "local stack mutation closes ledger instead of a fake cancellation");
        ok(!snow.ownsHotbar()&&!ledger.pending(Lane.AUTOSNOWBALL),
            "no phantom snowball ownership after local mutation");
    }
    private static void spectatorDenied() {
        AutoRodPolicy rod=new AutoRodPolicy();
        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        rod.decide(7,0,false,true,2,6,30);
        snow.decide(7,0,false,true,2,5,20);
        rod.decide(7,1,true,true,2,6,30);
        snow.decide(7,1,true,true,2,5,20);
        rod.committed(AutoRodPolicy.Action.SELECT,7,1);
        snow.committed(AutoSnowballPolicy.Action.SELECT,7,1);
        ok(ProjectileNativeUseResult.classify(true,false,false)==Outcome.SPECTATOR_DENIED,
           "vanilla spectator transition must not invoke use");
        ok(rod.decide(7,2,true,false,6,6,30)==AutoRodPolicy.Action.RESTORE,
           "rod owned slot transitions to restore on spectator admission loss");
        ok(snow.decide(7,2,true,false,5,5,20)==AutoSnowballPolicy.Action.RESTORE,
           "snowball owned slot transitions to restore on spectator admission loss");
        ok(!rod.castObserved()&&!snow.castObserved(),
           "preflight spectator veto never counts a locally submitted item use");
        rod.committed(AutoRodPolicy.Action.RESTORE,7,2);
        snow.committed(AutoSnowballPolicy.Action.RESTORE,7,2);
        ok(!rod.ownsHotbar()&&!snow.ownsHotbar(),
           "both non-dispatched spectator intents restore and close");
    }
    private static void seeded() throws Exception {
        Random rng=new Random(361189L);
        long denied=0,unchanged=0,changed=0,notInvoked=0;
        for(int i=0;i<60000;i++) {
            boolean spectator=rng.nextInt(11)==0;
            boolean invoked=!spectator && rng.nextInt(8)!=0;
            boolean localChanged=invoked&&rng.nextBoolean();
            Outcome value=ProjectileNativeUseResult.classify(
                spectator,invoked,localChanged);
            Outcome expected=spectator?Outcome.SPECTATOR_DENIED:
                !invoked?Outcome.NOT_INVOKED:
                localChanged?Outcome.SUBMITTED_LOCAL_CHANGE:Outcome.SUBMITTED_UNCHANGED;
            ok(value==expected,"60k vanilla boolean-result truth table "+i);
            ok(value.submitted()==invoked &&
               value.localChange()==(invoked&&localChanged),
               "60k independent submission/local-mutation separation "+i);
            if(value==Outcome.SPECTATOR_DENIED)denied++;
            else if(value==Outcome.NOT_INVOKED)notInvoked++;
            else if(value==Outcome.SUBMITTED_UNCHANGED)unchanged++;
            else changed++;
        }
        ok(denied>4500&&notInvoked>5000&&unchanged>18000&&changed>18000,
           "60k spectator, local unchanged, local changed and no-call exercised");
        AtomicBoolean checked=new AtomicBoolean();
        Thread other=new Thread(()->{
            ProjectileInventoryFence f=new ProjectileInventoryFence();
            checked.set(ProjectileNativeUseResult.classify(false,true,false).submitted()
                        && !f.mayRestore(0,2,6,null,null,0,0,null));
        });other.start();other.join();
        ok(checked.get(),"pure classifier does not leak native thread state");
        ok(assertions>120000,"60k seeded native lifecycle oracle ran");
    }
    public static void main(String[] args) throws Exception {
        truthTable();rodUnchanged();snowballDepletion();spectatorDenied();seeded();
        System.out.println("PROJECTILE_NATIVE_USE_RESULT_PASS assertions="+assertions);
    }
}
