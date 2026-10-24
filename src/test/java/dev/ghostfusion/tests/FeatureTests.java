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
    /** R2.93: signed subtraction must not preserve expired click windows. */
    private static void cpsWideElapsed() {
        Random random=new Random(2931023L);
        CpsMeter meter=new CpsMeter();
        for(int trial=0;trial<18000;trial++) {
            meter.clear();
            long first=Long.MIN_VALUE+random.nextInt(1000000);
            meter.mouse(0,true,first,true);
            meter.mouse(1,true,first+1,true);
            ok(meter.left(first+999999999L)==1 &&
               meter.right(first+999999999L)==1,"inside window "+trial);
            ok(meter.left(first+1000000000L)==0 &&
               meter.right(first+1000000000L)==1,"exact expiry "+trial);
            ok(meter.right(Long.MAX_VALUE)==0 && meter.left(Long.MAX_VALUE)==0,
               "wide forward interval evicts all stale clicks "+trial);
        }
        meter.clear();
        long nearMax=Long.MAX_VALUE-10000;
        meter.mouse(0,true,nearMax,true);
        ok(meter.left(Long.MAX_VALUE)==1,"recent click near MAX retained");
        meter.clear();
        meter.mouse(1,true,Long.MIN_VALUE,true);
        ok(meter.right(Long.MAX_VALUE)==0,"initial MIN timestamp expires");
    }
    /** R3.03: extreme mouse event rates must have fixed memory cost,
     * preserve the newest one-second edges, and resume exact counts. */
    private static void cpsBoundedFlood() {
        CpsMeter meter=new CpsMeter();
        final int cap=CpsMeter.MAX_WINDOW_CLICKS;
        ok(cap>=1024 && cap<=8192,"physical CPS window has a practical fixed budget");
        long last=0L;
        for(int i=0;i<30000;i++) {
            long now=100000L+1000L*i;
            meter.mouse(0,true,now,true);
            meter.mouse(0,false,now,true);
            meter.mouse(1,true,now,true);
            meter.mouse(1,false,now,true);
            last=now;
            if(i%257==0) {
                int expected=Math.min(i+1,cap);
                ok(meter.left(now)==expected && meter.right(now)==expected,
                   "bounded left/right CPS from 30k-edge flood "+i);
            }
        }
        ok(meter.left(last)==cap && meter.right(last)==cap,
           "both rolling queues saturate independently at exact cap");
        // The oldest retained observation is still in the window: older
        // dropped events must never resurrect as time advances.
        ok(meter.left(last+999999999L)==1 &&
           meter.right(last+999999999L)==1,
           "old input flood expires down to newest physical click");
        ok(meter.left(last+1000000000L)==0 &&
           meter.right(last+1000000000L)==0,
           "exact 1-second boundary expires the last retained edge");
        long start=last+1000000001L;
        for(int i=0;i<33;i++) {
            long now=start+10000L*i;
            meter.mouse(0,true,now,true);
            meter.mouse(0,false,now,true);
        }
        ok(meter.left(start+320000L)==33 && meter.right(start+320000L)==0,
           "ordinary true CPS accuracy resumes immediately after saturation");
        meter.mouse(0,true,start+330000L,false);
        ok(meter.left(start+330000L)==33,
           "GUI focus suppression never adds synthetic CPS");
        meter.clear();
        ok(meter.left(0L)==0 && meter.right(0L)==0,
           "world/reset clears saturated queues and timestamps");

        Random rnd=new Random(3032026L);
        for(int trial=0;trial<72;trial++) {
            meter.clear();
            long t=1L;
            int count=3000+rnd.nextInt(4000);
            for(int i=0;i<count;i++) {
                t+=1+rnd.nextInt(4000);
                meter.mouse(0,true,t,true);
                meter.mouse(0,false,t,true);
                if(i%511==0)
                    ok(meter.left(t)==Math.min(i+1,cap),
                       "seeded bounded event admission "+trial+"/"+i);
            }
            ok(meter.left(t)==Math.min(count,cap),
               "seeded cap always preserves newest observations "+trial);
            ok(meter.left(t+1000000000L)==0,
               "seeded burst fully expires and releases retained memory "+trial);
        }
    }
    /** R3.04: independent boxed reference model proves primitive rings
     * preserve one-second expiry, cap, focus, invalid buttons and reset. */
    private static void cpsPrimitiveRingOracle() {
        CpsMeter meter = new CpsMeter();
        java.util.Deque<Long> left = new java.util.ArrayDeque<Long>();
        java.util.Deque<Long> right = new java.util.ArrayDeque<Long>();
        boolean[] held = new boolean[2];
        Random rnd = new Random(3042026L);
        long time = Long.MIN_VALUE + 1000000000L;
        int saturated = 0;
        int checked = 0;
        for (int i = 0; i < 160000; i++) {
            if (i > 0 && i % 25007 == 0)
                time += 1000000001L;
            else
                time += 1 + rnd.nextInt(30000);
            while (!left.isEmpty() &&
                   Long.compareUnsigned(time-left.peekFirst(),1000000000L)>=0)
                left.removeFirst();
            while (!right.isEmpty() &&
                   Long.compareUnsigned(time-right.peekFirst(),1000000000L)>=0)
                right.removeFirst();
            // Mostly L/R alternation; occasional unknown button, and focus
            // suppression, to mirror real input routing under stress.
            int button = i%37==0 ? 7 : i%5==0 ? 1 : 0;
            boolean down = i%2==0;
            boolean accepting = i%173!=0;
            if (button==0 || button==1) {
                if (!accepting) {
                    held[button]=false;
                } else {
                    if (down && !held[button]) {
                        java.util.Deque<Long> destination=button==0?left:right;
                        if(destination.size()==CpsMeter.MAX_WINDOW_CLICKS)
                            destination.removeFirst();
                        destination.addLast(time);
                    }
                    held[button]=down;
                }
            }
            meter.mouse(button,down,time,accepting);
            if(left.size()==CpsMeter.MAX_WINDOW_CLICKS ||
               right.size()==CpsMeter.MAX_WINDOW_CLICKS)saturated++;
            if(i%13==0) {
                ok(meter.left(time)==left.size() &&
                   meter.right(time)==right.size(),
                   "primitive ring matches independent reference "+i);
                checked++;
            }
            if(i%49999==49998) {
                meter.clear();
                left.clear();right.clear();
                held[0]=false;held[1]=false;
            }
        }
        ok(saturated>20000 && checked>10000,
           "replay exercised repeated ring wrap, saturation and expiry");
        ok(meter.left(time)==left.size() && meter.right(time)==right.size(),
           "final ring state identical after wrap and reset");
        meter.clear();
        ok(meter.left(0L)==0 && meter.right(0L)==0,
           "clear restores empty state and timestamp baseline");
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
        ok("values".equals(host.targetHealthText()),"old Target HUD numeric mode default");
        host.uiActions().cycle("hud.target","health_text");
        ok("percent".equals(host.targetHealthText()),"percentage health readout selectable");
        ok(Math.abs(host.espLineColor().red-.62f)<.000001 &&
           Math.abs(host.espLineColor().green-.56f)<.000001 &&
           host.espLineColor().blue==1.0f &&
           Math.abs(host.espLineColor().opacity-.82f)<.000001,
           "existing purple RGBA preserved as legacy profile defaults");
        host.uiActions().fraction("render.esp","red",1.0);
        host.uiActions().fraction("render.esp","green",0.1);
        host.uiActions().fraction("render.esp","blue",0.0);
        host.uiActions().fraction("render.esp","opacity",0.5);
        ok(host.espLineColor().red==1f &&
           Math.abs(host.espLineColor().green-.1f)<.000001 &&
           host.espLineColor().blue==0f &&
           Math.abs(host.espLineColor().opacity-.55f)<.000001,
           "existing numeric GUI slider mutation updates all ESP color channels");
        ok(Math.abs(host.espCornerLength()-.26)<.000001,
           "corner style does not change existing default config");
        ok(Math.abs(host.espFillOpacity()-.18f)<.000001,
           "legacy profile retains independent translucent fill default");
        ok(!host.espDistanceFade() &&
           Math.abs(host.espFadeStart()-.50)<.000001 &&
           Math.abs(host.espFadeMinimum()-.25)<.000001,
           "legacy ESP profile keeps all line and fill opacity unchanged");
        ok(host.espMaxPlayers()==80,"old profiles retain all 80 ESP draws");
        host.uiActions().fraction("render.esp","max_players",0);
        ok(host.espMaxPlayers()==1,"existing numeric GUI can bound renderer to 1");
        host.uiActions().fraction("render.esp","max_players",1.0);
        ok(host.espMaxPlayers()==80,"original 80 draw limit restored");
        host.uiActions().bool("render.esp","distance_fade");
        host.uiActions().fraction("render.esp","fade_start",1.0);
        host.uiActions().fraction("render.esp","fade_min_opacity",0.0);
        ok(host.espDistanceFade()&&
           Math.abs(host.espFadeStart()-.90)<.000001 &&
           Math.abs(host.espFadeMinimum()-.05)<.000001,
           "existing ClickGUI adjusts fade toggle and both sliders");
        host.uiActions().fraction("render.esp","fill_opacity",1.0);
        ok(Math.abs(host.espFillOpacity()-.5f)<.000001,
           "fill opacity slider uses existing GUI/typed-settings gateway");
        host.uiActions().fraction("render.esp","corner_length",1.0);
        ok(Math.abs(host.espCornerLength()-.50)<.000001,
           "corner-length slider uses the established UI setting gateway");
        host.uiActions().fraction("render.esp","range",1.0);
        host.uiActions().bool("render.esp","through_walls");
        ok(!host.espShowHealth(),"legacy/default ESP health bars disabled");
        ok(host.espHealthSide()==dev.ghostfusion.features.EspHealthBarGeometry.Side.RIGHT &&
           Math.abs(host.espHealthGap()-.18)<1e-8,
           "legacy world-space health placement remains right plus 0.18");
        host.uiActions().cycle("render.esp","health_side");
        host.uiActions().fraction("render.esp","health_gap",1.0);
        ok(host.espHealthSide()==dev.ghostfusion.features.EspHealthBarGeometry.Side.LEFT &&
           Math.abs(host.espHealthGap()-.50)<1e-8,
           "GUI side/spacing controls update independently");
        host.uiActions().bool("render.esp","show_health");
        ok(host.espShowHealth(),"health bar setting toggles in GUI");
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
        ok("percent".equals(restored.targetHealthText()),
           "percentage preference persists through original profile store");
        ok(restored.espRange()==80.0 && restored.espThroughWalls(),"ESP settings persisted");
        ok(restored.espShowHealth(),"optional ESP health overlay persisted");
        ok(restored.espHealthSide()==dev.ghostfusion.features.EspHealthBarGeometry.Side.LEFT &&
           Math.abs(restored.espHealthGap()-.50)<1e-8,
           "left health placement and spacing persist via existing profile format");
        ok(Math.abs(restored.espCornerLength()-.5)<.000001,
           "corner length persists in the old typed profile store");
        ok(Math.abs(restored.espFillOpacity()-.5f)<.000001,
           "filled box opacity persisted through existing profile schema");
        ok(restored.espDistanceFade() &&
           Math.abs(restored.espFadeStart()-.90)<.000001 &&
           Math.abs(restored.espFadeMinimum()-.05)<.000001,
           "fade settings survive existing profile serializer");
        ok(restored.espMaxPlayers()==80,
           "full default draw limit preserved through original profile serializer");
        ok(restored.espLineColor().red==1f &&
           Math.abs(restored.espLineColor().green-.1f)<.000001 &&
           restored.espLineColor().blue==0f &&
           Math.abs(restored.espLineColor().opacity-.55f)<.000001,
           "RGBA ESP appearance survives save/restore without profile version change");
        ok("tracer".equals(restored.espStyle()),"ESP style persisted");
        restored.uiActions().cycle("render.esp","style");
        ok("both".equals(restored.espStyle()),"legacy combined ESP mode preserved");
        restored.uiActions().cycle("render.esp","style");
        ok("skeleton".equals(restored.espStyle()),"new skeleton ESP style selectable");
        restored.uiActions().cycle("render.esp","style");
        ok("box_skeleton".equals(restored.espStyle()),"new box skeleton style selectable");
        restored.uiActions().cycle("render.esp","style");
        ok("corner_box".equals(restored.espStyle()),"3D corner box style selectable");
        restored.uiActions().cycle("render.esp","style");
        ok("corner_skeleton".equals(restored.espStyle()),
           "corner box plus schematic skeleton selectable");
        restored.uiActions().cycle("render.esp","style");
        ok("filled_box".equals(restored.espStyle()),
           "translucent filled box style appended after seven existing modes");
        restored.uiActions().cycle("render.esp","style");
        ok("filled_skeleton".equals(restored.espStyle()),
           "filled box plus skeleton mode selectable");
        restored.uiActions().cycle("render.esp","style");
        ok("box".equals(restored.espStyle()),
           "cycle wraps to original default mode without reordering old entries");
        restored.persist();
        ok(restored.modules().get("hud.cps").state()==ModuleRegistry.State.DISABLED,
            "non-status module hotkey change persisted");
        restored.worldPresence(true);
        ok(restored.featureEnabled("hud.target") && restored.featureEnabled("render.esp") &&
           restored.featureEnabled("movement.sprint"),"live enabled modules restored");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        cps(); cpsWideElapsed(); cpsBoundedFlood(); cpsPrimitiveRingOracle();
        sprint(); targets(); featuresAndProfiles();
        ok(count>4000,"feature regression coverage substantial");
        System.out.println("FEATURE_REGRESSION_PASS assertions="+count);
    }
}
