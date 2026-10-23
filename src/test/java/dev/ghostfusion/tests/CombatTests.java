package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import dev.ghostfusion.services.WorldSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R0.6 deterministic combat-policy and shared-service regression tests. No Minecraft required. */
public final class CombatTests {
    private static int checks;
    private static void ok(boolean result,String description) {
        checks++;
        if(!result) throw new AssertionError(description);
    }
    private static void fails(Runnable task,String description) {
        boolean did=false;
        try { task.run(); } catch(RuntimeException expected) { did=true; }
        ok(did,description);
    }
    private static WorldSnapshot.Entity entity(int id,double x,double y,double z,boolean visible) {
        return new WorldSnapshot.Entity(id,x,y,z,20.0,true,visible,true);
    }
    private static WorldSnapshot world(List<WorldSnapshot.Entity> entities,float yaw,float pitch) {
        return new WorldSnapshot(1,1,0,0,0,yaw,pitch,entities);
    }
    private static void aimGeometry() {
        AimAssistPolicy policy=new AimAssistPolicy();
        WorldSnapshot frontal=world(Collections.singletonList(entity(2,0,0,4,true)),0f,0f);
        AimAssistPolicy.Suggestion aimed=policy.suggest(frontal,1.62,1.25,5,25,2,true);
        ok(aimed != null && aimed.entityId==2,"frontal target acquired");
        ok(aimed.sameTargetPosition(0,0,4),"same-tick aim target pose accepted");
        ok(aimed.sameSourcePose(0,0,0,0f,0f,1.62),
            "same-tick source player pose and eye height accepted");
        ok(!aimed.sameSourcePose(0.00001,0,0,0f,0f,1.62),
            "player horizontal motion after snapshot invalidates absolute aim");
        ok(!aimed.sameSourcePose(0,-0.00001,0,0f,0f,1.62),
            "player vertical motion after snapshot invalidates absolute aim");
        ok(!aimed.sameSourcePose(0,0,0.00001,0f,0f,1.62),
            "player depth motion after snapshot invalidates absolute aim");
        ok(!aimed.sameSourcePose(0,0,0,0.00001f,0f,1.62) &&
           !aimed.sameSourcePose(0,0,0,0f,-0.00001f,1.62),
            "camera yaw/pitch changes invalidate absolute aim");
        ok(!aimed.sameSourcePose(0,0,0,0f,0f,1.61),
            "sneak or other eye-height changes invalidate absolute aim");
        ok(!aimed.sameSourcePose(Double.NaN,0,0,0f,0f,1.62) &&
           !aimed.sameSourcePose(0,0,0,Float.NaN,0f,1.62) &&
           !aimed.sameSourcePose(0,0,0,0f,Float.POSITIVE_INFINITY,1.62) &&
           !aimed.sameSourcePose(0,0,0,0f,0f,Double.NaN),
            "nonfinite source coordinates, angles and eye height rejected");

        // Replay source movement/turns with a fixed accepted target.
        // This strictly verifies snapshot-to-camera freshness without
        // requiring Minecraft objects or speculative timing sleeps.
        Random sourceRandom=new Random(2522026L);
        for(int step=0;step<120000;step++) {
            int mutation=sourceRandom.nextInt(12);
            double sx=0,sy=0,sz=0,eye=1.62;
            float yaw=0,pitch=0;
            switch(mutation) {
                case 1:sx=sourceRandom.nextBoolean()?0.00001:-0.00001;break;
                case 2:sy=sourceRandom.nextBoolean()?0.00001:-0.00001;break;
                case 3:sz=sourceRandom.nextBoolean()?0.00001:-0.00001;break;
                case 4:yaw=0.125f;break;
                case 5:pitch=-0.125f;break;
                case 6:eye=1.54;break;
                case 7:sx=Double.NaN;break;
                case 8:sy=Double.POSITIVE_INFINITY;break;
                case 9:sz=Double.NEGATIVE_INFINITY;break;
                case 10:yaw=Float.NaN;break;
                case 11:eye=Double.POSITIVE_INFINITY;break;
                default:break;
            }
            ok(aimed.sameSourcePose(sx,sy,sz,yaw,pitch,eye)==(mutation==0),
                "120k source snapshot freshness replay "+step);
        }
        ok(!aimed.sameTargetPosition(0,0,4.00001),
           "moving target after snapshot invalidates calculated rotation");
        ok(!aimed.sameTargetPosition(0,1,4),
           "vertical target movement invalidates calculated rotation");
        ok(!aimed.sameTargetPosition(0,0,Double.NaN) &&
           !aimed.sameTargetPosition(Double.POSITIVE_INFINITY,0,4),
           "non-finite current target pose cannot apply camera rotation");
        ok(Math.abs(aimed.yaw)<0.00001,"south-facing camera aligned");
        ok(aimed.pitch>1.95 && aimed.pitch<=2.01,"vertical angular step bounded to two degrees");
        ok(aimed.angularError>5.0 && aimed.angularError<6.0,"angular error reflects player eye offsets");
        ok(policy.suggest(frontal,1.62,1.25,5,4,2,true)==null,"narrow FOV rejects target");
        ok(policy.suggest(frontal,1.62,1.25,3,25,2,true)==null,"range gate excludes target");
        AimAssistPolicy.Suggestion full=policy.suggest(frontal,1.62,1.25,5,25,30,true);
        ok(full != null && full.pitch>5.0 && full.pitch<6.0,"fast assist converges to exact target");
        WorldSnapshot east=world(Collections.singletonList(entity(4,4,0,0,true)),-90f,0f);
        AimAssistPolicy.Suggestion a=policy.suggest(east,1.62,1.25,5,30,10,true);
        ok(a!=null && Math.abs(a.yaw+90)<0.001,"east orientation conventions");
        WorldSnapshot behind=world(Collections.singletonList(entity(5,0,0,-4,true)),0f,0f);
        ok(policy.suggest(behind,1.62,1.25,6,89,15,true)==null,"behind camera rejected");
        WorldSnapshot hidden=world(Collections.singletonList(entity(7,0,0,4,false)),0f,0f);
        ok(policy.suggest(hidden,1.62,1.25,5,60,10,true)==null,"visible-only contract");
        ok(policy.suggest(hidden,1.62,1.25,5,60,10,false)!=null,"visibility override explicit");
        ok(AimAssistPolicy.wrapDegrees(358)==-2,"positive wrap");
        ok(AimAssistPolicy.wrapDegrees(-358)==2,"negative wrap");
        ok(AimAssistPolicy.wrapDegrees(540)==-180,"180 tie convention");
        fails(()->AimAssistPolicy.wrapDegrees(Double.NaN),"non-finite yaw rejected");
        fails(()->policy.suggest(frontal,1.62,1.25,5,30,0,true),"zero speed rejected");
        fails(()->policy.suggest(frontal,1.62,1.25,5,181,1,true),"invalid FOV rejected");
        fails(()->policy.suggest(frontal,Double.NaN,1.25,5,30,1,true),"eye offset rejected");
        fails(()->policy.suggest(null,1.62,1.25,5,30,1,true),"null world rejected");
        for(int seed=0;seed<120;seed++) {
            Random random=new Random(seed+20261018L);
            List<WorldSnapshot.Entity> candidates=new ArrayList<WorldSnapshot.Entity>();
            for(int i=1;i<=60;i++) {
                double x=(random.nextInt(150)-75)/10.0;
                double z=(random.nextInt(150)-75)/10.0;
                candidates.add(entity(i,x,0,z,true));
            }
            WorldSnapshot first=world(candidates,0,0);
            AimAssistPolicy.Suggestion s1=policy.suggest(first,1.62,1.25,7,90,3,true);
            Collections.shuffle(candidates,new Random(seed));
            AimAssistPolicy.Suggestion s2=policy.suggest(world(candidates,0,0),1.62,1.25,7,90,3,true);
            ok((s1==null)==(s2==null),"candidate shuffle presence "+seed);
            if(s1!=null) {
                ok(s1.entityId==s2.entityId,"candidate shuffle ID "+seed);
                ok(Math.abs(s1.yaw-s2.yaw)<0.00001 && Math.abs(s1.pitch-s2.pitch)<0.00001,
                   "candidate shuffle rotation "+seed);
                double angularTravel=Math.hypot(AimAssistPolicy.wrapDegrees(s1.yaw),s1.pitch);
                ok(angularTravel<=3.001,"bounded angular step "+seed);
            }
        }
    }
    private static void scheduler() {
        FixedClickScheduler s=new FixedClickScheduler();
        ok(!s.due(0,true,true,10),"first physical press belongs to vanilla");
        ok(!s.due(99000000,true,true,10),"interval not passed");
        ok(s.due(100000000,true,true,10),"one pulse at 100ms");
        ok(!s.due(100000000,true,true,10),"no duplicate in same frame");
        ok(s.due(350000000,true,true,10),"late frame at most one pulse");
        ok(!s.due(350000001,true,true,10),"no catchup burst");
        ok(!s.due(360000000,true,false,10),"release cancels timer");
        ok(!s.due(370000000,true,true,10),"new hold must arm without extra pulse");
        ok(s.due(470000000,true,true,10),"new hold fires after period");
        ok(!s.due(500000000,false,true,10),"disabled schedule dormant");
        ok(!s.due(600000000,true,true,10),"reenable arms a fresh timer");
        ok(s.due(700000000,true,true,10),"rearm works");
        fails(()->s.due(699999999,true,true,10),"time going backward rejected");
        fails(()->s.due(701000000,true,true,Double.NaN),"NaN CPS rejected");
        fails(()->s.due(701000000,true,true,Double.POSITIVE_INFINITY),"infinite CPS rejected");
        fails(()->s.due(701000000,true,true,0),"zero CPS rejected");
        fails(()->s.due(701000000,true,true,30),"excessive CPS rejected");
        s.reset();
        ok(!s.due(-1000000000L,true,true,10),"negative nanoTime origin is valid");
        ok(s.due(-900000000L,true,true,10),"negative time advances correctly");
        s.reset();
        long now=0;
        int pulses=0;
        for(int i=0;i<3000;i++) {
            now+=10000000L; // 10ms simulation with a 100ms schedule
            if(s.due(now,true,true,10)) pulses++;
            ok(pulses>=0 && pulses<=i/8+1,"no excess bursts "+i);
        }
        ok(pulses>=298 && pulses<=300,"fixed interval throughput");
        s.reset();
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try { s.due(0,true,true,10); } catch(IllegalStateException ex) { rejected.set(true); }
        });
        foreign.start();
        try { foreign.join(); } catch(InterruptedException ex) { throw new AssertionError(ex); }
        ok(rejected.get(),"scheduler client-thread ownership");
    }
    /** R2.93: huge forward nanoTime jumps cannot suppress overdue clicks. */
    private static void schedulerWideElapsed() {
        java.util.Random random=new java.util.Random(2932026L);
        FixedClickScheduler clock=new FixedClickScheduler();
        int[] rates={4,10,25};
        for(int trial=0;trial<30000;trial++) {
            clock.reset();
            long first=Long.MIN_VALUE+random.nextInt(1000000);
            int cps=rates[trial%rates.length];
            long period=(long)(1000000000.0/cps);
            ok(!clock.due(first,true,true,cps),"first press arms "+trial);
            ok(!clock.due(first+period-1,true,true,cps),"early interval "+trial);
            ok(clock.due(first+period,true,true,cps),"exact boundary "+trial);
            ok(!clock.due(first+period,true,true,cps),"duplicate blocked "+trial);
            ok(clock.due(Long.MAX_VALUE,true,true,cps),"huge forward interval due "+trial);
            ok(!clock.due(Long.MAX_VALUE,true,true,cps),"no delayed catchup "+trial);
            ok(!clock.due(Long.MAX_VALUE,false,false,cps),"release clears "+trial);
            ok(!clock.due(Long.MAX_VALUE,true,true,cps),"repress rearms "+trial);
        }
        clock.reset();
        ok(!clock.due(-200000000L,true,true,10),"negative nanoTime origin arms");
        ok(clock.due(-100000000L,true,true,10),"negative nanoTime origin fires");
    }
    private static void contracts() {
        SessionServices s=new SessionServices();
        s.world(true);
        s.tickAdvance();
        s.rotations().request("combat.aimassist",RotationCoordinator.Scope.CAMERA,
            30f,4f,15,s.tick(),1,s.generation());
        s.rotations().request("other",RotationCoordinator.Scope.CAMERA,
            90f,0f,1,s.tick(),1,s.generation());
        RotationCoordinator.Decision d=s.rotations().decide(RotationCoordinator.Scope.CAMERA,
            s.tick(),s.generation(),0,0);
        ok("combat.aimassist".equals(d.owner) && d.yaw==30,"single-writer priority preserved");
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,12,s.tick(),1,s.generation());
        ActionCoordinator.Decision decision=s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,s.tick(),s.generation());
        ok(decision!=null && "combat.clicker".equals(decision.owner),"clicker action resolved through arbiter");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,s.tick(),s.generation())==null,"one attack per tick");
        s.tickAdvance();
        ok(s.rotations().decide(RotationCoordinator.Scope.CAMERA,
            s.tick(),s.generation(),17,2).owner==null,"rotation request expiration");
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,12,s.tick(),1,s.generation());
        s.disconnect();
        ok(s.rotations().pending()==0 && s.actions().pending()==0,"disconnect cancels combat intents");
        s.world(true);s.tickAdvance();
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,s.tick(),s.generation())==null,"no stale attack after join");
        s.close();
    }
    private static void profiles() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-combat-profiles-");
        HostRuntime host=new HostRuntime(folder);
        String aimId="combat.aimassist",clickId="combat.clicker";
        ok(host.modules().get(aimId).state()==ModuleRegistry.State.DISABLED,"aim disabled by default");
        ok(host.modules().get(clickId).state()==ModuleRegistry.State.DISABLED,"clicker disabled by default");
        ok(!host.featureEnabled(aimId),"world required");
        host.worldPresence(true);
        host.uiActions().fraction(aimId,"range",1);
        host.uiActions().fraction(aimId,"fov",0.5);
        host.uiActions().fraction(aimId,"degrees_per_tick",0.5);
        host.uiActions().bool(aimId,"require_click");
        host.uiActions().bool(aimId,"visible_only");
        host.uiActions().fraction(clickId,"cps",1);
        host.uiActions().bool(clickId,"entity_only");
        ok(host.aimRange()==6 && host.aimFov()==47.5 && host.aimDegreesPerTick()==7.75,
           "typed bounded aim settings");
        ok(!host.aimRequireClick() && !host.aimVisibleOnly(),"aim flags configurable");
        ok(host.clickCps()==15 && !host.clickEntityOnly(),"clicker settings configurable");
        host.uiActions().toggle(aimId); host.uiActions().toggle(clickId);
        ok(host.featureEnabled(aimId) && host.featureEnabled(clickId),"live module enabled");
        host.uiActions().bind(clickId,45);
        host.key(45,true,false);
        host.key(45,false,false);
        ok(!host.featureEnabled(clickId),"bind disables clicker");
        host.close();
        HostRuntime restored=new HostRuntime(folder);
        ok(restored.aimRange()==6 && restored.aimFov()==47.5 && restored.aimDegreesPerTick()==7.75,
           "aim numeric profile roundtrip");
        ok(!restored.aimRequireClick() && !restored.aimVisibleOnly(),"aim flags persist");
        ok(restored.clickCps()==15 && !restored.clickEntityOnly(),"clicker preferences persist");
        ok(restored.modules().get(aimId).state()==ModuleRegistry.State.ENABLED,"enabled aim persisted");
        ok(restored.modules().get(clickId).state()==ModuleRegistry.State.DISABLED,"disabled clicker persisted");
        restored.worldPresence(true);
        ok(restored.featureEnabled(aimId) && !restored.featureEnabled(clickId),"world honors restored states");
        restored.disconnect();
        ok(!restored.featureEnabled(aimId),"disconnect suppresses all combat modules");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        aimGeometry(); scheduler(); schedulerWideElapsed(); contracts(); profiles();
        ok(checks>3000,"substantial combat policy suite");
        System.out.println("COMBAT_REGRESSION_PASS assertions="+checks);
    }
}
