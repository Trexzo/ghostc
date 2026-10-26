package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.PatternClickScheduler;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.32 offline combat upgrade contract; original-game behavior still needs acceptance. */
public final class CombatDepthTests {
    private static int checks;
    private static void ok(boolean yes, String why) {
        checks++;
        if (!yes) throw new AssertionError(why);
    }
    private static void rejects(Runnable callback, String why) {
        boolean rejected = false;
        try { callback.run(); } catch (RuntimeException expected) { rejected = true; }
        ok(rejected, why);
    }
    private static void intervalReplay(String mode) {
        PatternClickScheduler a = new PatternClickScheduler(3242026L);
        PatternClickScheduler b = new PatternClickScheduler(3242026L);
        long now = 0, smallest = Long.MAX_VALUE, largest = 0L;
        ok(!a.due(now, true, true, 6, 14, mode), "a begins with vanilla held press");
        ok(!b.due(now, true, true, 6, 14, mode), "b begins with vanilla held press");
        for (int i=0; i<10000; i++) {
            long period = a.intervalNanos();
            ok(period == b.intervalNanos(), "same-seed reproducibility " + i);
            ok(period >= (long)(1000000000.0/14)-1 &&
               period <= (long)(1000000000.0/6)+1,
               "interval stays inside bounded CPS range " + i);
            smallest = Math.min(smallest,period);
            largest = Math.max(largest,period);
            ok(!a.due(now + period - 1L, true, true, 6, 14, mode),
               "cannot click before deadline " + i);
            ok(!b.due(now + period - 1L, true, true, 6, 14, mode),
               "independent replay remains early " + i);
            now += period;
            ok(a.due(now, true, true, 6, 14, mode), "due at deadline " + i);
            ok(b.due(now, true, true, 6, 14, mode), "same seed same output " + i);
            ok(!a.due(now, true, true, 6, 14, mode), "no same-tick duplicate " + i);
        }
        ok(largest - smallest > 30000000L, "nonfixed interval variation observable");
        ok(!a.due(now+1,true,false,6,14,mode),"released binding disarms");
        ok(a.intervalNanos()==0,"release clears pending interval");
        ok(!a.due(now+2,true,true,6,14,mode),"new held press must rearm");
        ok(!a.due(now+3,false,true,6,14,mode),"disabled module disarms");
        ok(!a.due(now+4,true,true,6,14,mode),"reenable no extra pulse");
        ok(!a.due(now+5,true,true,6,14,"balanced".equals(mode)?"range":"balanced"),
           "switching cadence rearms, never spends previous interval");
        ok(!a.due(now+6,true,true,7,13,"balanced".equals(mode)?"range":"balanced"),
           "live bounds edits rearm, never transfer pending cadence");
    }
    private static void schedulerRejectsInvalidState() throws Exception {
        PatternClickScheduler p=new PatternClickScheduler(1);
        rejects(()->p.due(0,true,true,15,4,"range"),"reversed API CPS bounds rejected");
        rejects(()->p.due(0,true,true,0,5,"range"),"zero min rejected");
        rejects(()->p.due(0,true,true,4,26,"range"),"max above cap rejected");
        rejects(()->p.due(0,true,true,Double.NaN,8,"range"),"NaN rejected");
        rejects(()->p.due(0,true,true,4,Double.POSITIVE_INFINITY,"range"),"infinity rejected");
        rejects(()->p.due(0,true,true,6,10,"butterfly"),"unsupported pattern rejected");
        ok(!p.due(-1000000000L,true,true,10,10,"range"),"negative nanoTime valid");
        ok(p.due(-900000000L,true,true,10,10,"range"),"exact negative-origin period due");
        rejects(()->p.due(-900000001L,true,true,10,10,"range"),"clock rollback rejected");
        p.reset();
        ok(!p.due(Long.MIN_VALUE,true,true,6,14,"range"),"min long origin arms");
        ok(p.due(Long.MAX_VALUE,true,true,6,14,"range"),"large forward elapsed due");
        ok(!p.due(Long.MAX_VALUE,true,true,6,14,"range"),"no catch-up at max");
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread outsider=new Thread(()->{
            try {p.due(1,true,true,6,14,"range");}
            catch(IllegalStateException expected){denied.set(true);}
        });
        outsider.start();outsider.join();
        ok(denied.get(),"owner-thread fence retained");
    }
    private static void aimAxes() {
        AimAssistPolicy p=new AimAssistPolicy();
        WorldSnapshot.Entity target = new WorldSnapshot.Entity(
            17,1,0,4,20,true,true,true);
        WorldSnapshot world = new WorldSnapshot(
            1,1,0,0,0,0,0,Collections.singletonList(target));
        AimAssistPolicy.Suggestion original=p.suggest(world,1.62,1.25,6,90,60,true);
        AimAssistPolicy.Suggestion defaultAxes=p.suggest(world,1.62,1.25,6,90,60,true,1,1);
        ok(original!=null && defaultAxes!=null,"target in valid original FOV");
        ok(original.yaw==defaultAxes.yaw && original.pitch==defaultAxes.pitch,
           "legacy 1.0/1.0 exactly matches original output");
        AimAssistPolicy.Suggestion horizontal=p.suggest(world,1.62,1.25,6,90,60,true,.25,1.0);
        AimAssistPolicy.Suggestion vertical=p.suggest(world,1.62,1.25,6,90,60,true,1.0,.25);
        ok(Math.abs(horizontal.yaw-original.yaw*.25)<.0001,
           "independent horizontal fraction works");
        ok(Math.abs(horizontal.pitch-original.pitch)<.0001,
           "horizontal setting does not weaken vertical");
        ok(Math.abs(vertical.yaw-original.yaw)<.0001,
           "vertical setting does not affect horizontal");
        ok(Math.abs(vertical.pitch-original.pitch*.25)<.0001,
           "independent vertical fraction works");
        AimAssistPolicy.Suggestion capped=p.suggest(world,1.62,1.25,6,90,1,true,.75,.60);
        ok(Math.hypot(capped.yaw,capped.pitch)<=1.00001,
           "combined maximum turn still absolute bound");
        ok(capped.sameTargetPosition(1,0,4),"new suggestion still pins actual target pose");
        ok(!capped.sameSourcePose(.01,0,0,0f,0f,1.62),
           "new suggestion still rejects a moved local player");
        AimAssistPolicy.Suggestion raised=p.suggest(world,1.62,1.70,6,90,60,true,1,1);
        ok(raised!=null && raised.pitch<original.pitch,"adjustable upper-body aim point changes pitch");
        rejects(()->p.suggest(world,1.62,1.25,6,90,3,true,0,1),"zero yaw fraction rejected");
        rejects(()->p.suggest(world,1.62,1.25,6,90,3,true,1,1.01),"overshoot axis rejected");
        rejects(()->p.suggest(world,1.62,1.25,6,90,3,true,Double.NaN,1),"NaN fraction rejected");
    }
    private static void profileSettings() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r332-combat-");
        HostRuntime host=new HostRuntime(dir);
        String click="combat.clicker", aim="combat.aimassist";
        ok("fixed".equals(host.clickPattern()) && host.clickCps()==10,
           "fixed clicker and legacy 10 CPS default retained");
        ok(host.aimHorizontalFraction()==1 && host.aimVerticalFraction()==1 &&
           host.aimTargetHeight()==1.25,"all aim defaults retain original behavior");
        host.uiActions().cycle(click,"pattern");
        host.uiActions().fraction(click,"min_cps",.3);
        host.uiActions().fraction(click,"max_cps",.8);
        host.uiActions().fraction(aim,"horizontal_fraction",.4);
        host.uiActions().fraction(aim,"vertical_fraction",.6);
        host.uiActions().fraction(aim,"target_height",1.0);
        ok("balanced".equals(host.clickPattern()) &&
            host.clickMinCps()>4 && host.clickMaxCps()<15,
            "new click choices are UI-adjustable");
        ok(host.aimHorizontalFraction()<1 && host.aimVerticalFraction()<1 &&
           host.aimTargetHeight()==1.7,"independent aim settings exposed");
        final double min=host.clickMinCps(),max=host.clickMaxCps();
        final double h=host.aimHorizontalFraction(),v=host.aimVerticalFraction();
        host.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok("balanced".equals(loaded.clickPattern()) &&
            loaded.clickMinCps()==min && loaded.clickMaxCps()==max,
            "click pattern and CPS bounds persist across real profile roundtrip");
        ok(loaded.aimHorizontalFraction()==h && loaded.aimVerticalFraction()==v &&
            loaded.aimTargetHeight()==1.7,
            "aim axis and height settings roundtrip without reset");
        loaded.uiActions().fraction(click,"min_cps",1.0);
        loaded.uiActions().fraction(click,"max_cps",0.0);
        ok(loaded.clickMinCps()>loaded.clickMaxCps(),
            "crossed GUI slider values remain representable for Forge normalization");
        loaded.close();
    }
    public static void main(String[] args) throws Exception {
        intervalReplay("balanced");
        intervalReplay("range");
        schedulerRejectsInvalidState();
        aimAxes();
        profileSettings();
        ok(checks>70000,"substantial independent click trace replay");
        System.out.println("COMBAT_DEPTH_PASS assertions="+checks);
    }
}
