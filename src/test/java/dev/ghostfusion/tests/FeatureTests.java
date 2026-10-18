package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.features.CpsMeter;
import dev.ghostfusion.features.SprintPolicy;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R0.5 deterministic policy, focus, module, profile and target-display regressions. */
public final class FeatureTests {
    private static int count;
    private static void ok(boolean result, String why) {
        count++;
        if (!result) throw new AssertionError(why);
    }
    private static void fails(Runnable action, String why) {
        boolean failed = false;
        try { action.run(); } catch (RuntimeException expected) { failed = true; }
        ok(failed, why);
    }
    private static void cps() {
        CpsMeter meter = new CpsMeter();
        ok(meter.left(0) == 0 && meter.right(0) == 0, "initial CPS");
        meter.mouse(0, true, 100, true);
        meter.mouse(0, true, 110, true);
        meter.mouse(1, true, 130, true);
        ok(meter.left(130) == 1 && meter.right(130) == 1, "edge not held or repeated");
        meter.mouse(0, false, 150, true);
        meter.mouse(0, true, 180, true);
        ok(meter.left(180) == 2, "second edge counted");
        meter.mouse(1, false, 200, true);
        meter.mouse(1, true, 300, false);
        ok(meter.right(301) == 1, "GUI mouse input suppressed");
        meter.mouse(1, true, 340, true);
        ok(meter.right(341) == 2, "focus restores fresh edge");
        ok(meter.left(1000000100L) == 1, "one-second inclusive eviction");
        ok(meter.left(1000000180L) == 0, "all expired");
        ok(meter.right(1000000341L) == 0, "right expired");
        fails(() -> meter.left(20), "out-of-order clock rejected");
        meter.clear();
        ok(meter.left(0) == 0 && meter.right(0) == 0, "reset timestamps and counts");
        meter.mouse(0, true, 1, true);
        meter.clear();
        meter.mouse(0, true, 1, true);
        ok(meter.left(2) == 1, "lost release cannot persist across world change");
        meter.mouse(7, true, 3, true);
        ok(meter.left(3) == 1, "unknown buttons ignored");
        final AtomicBoolean threadRejected = new AtomicBoolean(false);
        Thread outsider = new Thread(() -> {
            try { meter.left(4); } catch (IllegalStateException expected) { threadRejected.set(true); }
        });
        outsider.start();
        try { outsider.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
        ok(threadRejected.get(), "counter client-thread fenced");
        Random random = new Random(20261018);
        long t = 1000;
        meter.clear();
        int total = 0;
        for (int i = 0; i < 4000; i++) {
            t += 500000L + random.nextInt(300000);
            boolean down = (i % 3) == 0;
            meter.mouse(0, down, t, true);
            if (down) total++;
            int actual = meter.left(t);
            ok(actual >= 0 && actual <= total, "nonnegative rolling CPS " + i);
        }
        meter.clear();
        for (int i=0; i<20; i++) {
            long now = i*100000000L;
            meter.mouse(0, true, now, true);
            meter.mouse(0, false, now+1, true);
            ok(meter.left(now+1) == Math.min(10,i+1), "one-second bounded events");
        }
    }
    private static void sprint() {
        for (int flags = 0; flags < 64; flags++) {
            boolean enabled=(flags&1)!=0, focused=(flags&2)!=0,
                onGround=(flags&4)!=0, collided=(flags&8)!=0,
                sneaking=(flags&16)!=0, using=(flags&32)!=0;
            boolean expected=enabled && focused && onGround && !collided && !sneaking && !using;
            boolean actual = SprintPolicy.shouldSprint(
                enabled,focused,onGround,collided,sneaking,using,1.0,20.0,false,false,false);
            ok(actual == expected, "sprint flags " + flags);
        }
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,0.6,20,false,false,false),
            "sideways/backward no sprint");
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,1,6,false,false,false),
            "starvation respects gate");
        ok(SprintPolicy.shouldSprint(true,true,true,false,false,false,1,6,false,false,true),
            "explicit sprint input at hunger boundary");
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,1,20,true,false,false),
            "flying rejected");
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,1,20,false,true,false),
            "blindness rejected");
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,Double.NaN,20,false,false,false),
            "nan input rejected");
        ok(!SprintPolicy.shouldSprint(true,true,true,false,false,false,1,Double.NaN,false,false,false),
            "nan food rejected");
    }
    private static void targets() {
        TargetHudPolicy policy = new TargetHudPolicy();
        ok(policy.select(null,8,true)==null,"null world empty");
        List<WorldSnapshot.Entity> list = new ArrayList<WorldSnapshot.Entity>();
        list.add(new WorldSnapshot.Entity(5,3,0,0,2,true,true,true));
        list.add(new WorldSnapshot.Entity(6,2,0,0,1,true,false,true));
        list.add(new WorldSnapshot.Entity(3,2,0,0,0,false,true,true));
        WorldSnapshot world=new WorldSnapshot(1,1,0,0,0,0,0,list);
        ok(policy.select(world,8,true).id==5,"visible nearby target");
        ok(policy.select(world,8,false).id==6,"optional invisibility/visibility filter");
        ok(policy.select(world,1,true)==null,"distance clipping");
        fails(()->policy.select(world,0,true),"range validated");
        for(int seed=0;seed<80;seed++) {
            Random random=new Random(seed);
            List<WorldSnapshot.Entity> candidates=new ArrayList<WorldSnapshot.Entity>();
            for (int i=0;i<64;i++) {
                double x=1+random.nextInt(15);
                candidates.add(new WorldSnapshot.Entity(i,x,0,0,5,true,true,true));
            }
            WorldSnapshot original=new WorldSnapshot(3,seed,0,0,0,0,0,candidates);
            WorldSnapshot.Entity winner=policy.select(original,24,true);
            Collections.shuffle(candidates,new Random(seed*17+3));
            WorldSnapshot shuffled=new WorldSnapshot(3,seed,0,0,0,0,0,candidates);
            ok(winner.id==policy.select(shuffled,24,true).id,"stable selection "+seed);
        }
    }
    private static void featuresAndProfiles() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r05-feature-");
        HostRuntime host=new HostRuntime(folder);
        String[] ids={"hud.keystrokes","hud.cps","hud.target","render.esp","movement.sprint"};
        for(String id:ids) {
            ok(host.modules().get(id).state()==ModuleRegistry.State.DISABLED,"features disabled by default "+id);
            ok(!host.featureEnabled(id),"world state required "+id);
        }
        host.worldPresence(true);
        for(String id:ids) {
            host.uiActions().toggle(id);
            ok(host.featureEnabled(id),"live module enabled "+id);
        }
        host.uiActions().bool("hud.keystrokes","show_jump");
        host.uiActions().bool("hud.cps","show_right");
        host.uiActions().fraction("hud.target","range",1.0);
        host.uiActions().bool("hud.target","visible_only");
        host.uiActions().fraction("render.esp","range",1.0);
        host.uiActions().bool("render.esp","through_walls");
        host.uiActions().cycle("render.esp","style");
        ok(!host.keysJump() && !host.cpsRight(),"UI bool settings applied");
        ok(host.targetRange()==24.0 && !host.targetVisible(),"target settings applied");
        ok(host.espRange()==80.0 && host.espThroughWalls(),"ESP settings applied");
        ok("tracer".equals(host.espStyle()),"ESP style cycled");
        host.modules().bind("hud.cps",45);
        host.key(45,true,false);
        host.key(45,false,false);
        ok(!host.featureEnabled("hud.cps"),"module bind toggled");
        host.worldPresence(false);
        ok(!host.featureEnabled("render.esp") && !host.featureEnabled("movement.sprint"),
            "all features disabled by disconnected gating");
        host.close();
        HostRuntime restored=new HostRuntime(folder);
        ok(!restored.keysJump() && !restored.cpsRight(),"booleans persisted");
        ok(restored.targetRange()==24.0 && !restored.targetVisible(),"target settings persisted");
        ok(restored.espRange()==80.0 && restored.espThroughWalls(),"ESP settings persisted");
        ok("tracer".equals(restored.espStyle()),"ESP style persisted");
        ok(restored.modules().get("hud.cps").state()==ModuleRegistry.State.DISABLED,
            "non-status module hotkey change persisted");
        restored.worldPresence(true);
        ok(restored.featureEnabled("hud.target") && restored.featureEnabled("render.esp") &&
           restored.featureEnabled("movement.sprint"),"live enabled modules restored");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        cps(); sprint(); targets(); featuresAndProfiles();
        ok(count>4000,"feature regression coverage substantial");
        System.out.println("FEATURE_REGRESSION_PASS assertions="+count);
    }
}
