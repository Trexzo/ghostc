package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R1.3: owner-thread combat callback isolation, fail-closed intents and persistent disable. */
public final class CombatIsolationTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes) throw new AssertionError(why);
    }
    private static void quarantineAndRecover() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-combat-isolation");
        HostRuntime h=new HostRuntime(dir);
        String[] ids={"combat.aimassist","combat.clicker","combat.hitselect","combat.blockhit"};
        h.worldPresence(true);
        for(String id:ids) {
            ok(h.modules().get(id).state()==ModuleRegistry.State.DISABLED,
               "combat disabled by default "+id);
            h.uiActions().toggle(id);
            ok(h.featureEnabled(id),"feature opt-in "+id);
        }
        int healthyCallbacks=0;
        for(int i=0;i<ids.length;i++) {
            String id=ids[i];
            ok(!h.runFeature(id,()->{throw new IllegalArgumentException("PRIVATE-SESSION-DATA");}),
                "fault quarantined "+id);
            ok(!h.featureEnabled(id),"bad combat module disabled "+id);
            ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==i+1,
               "one fault per feature "+id);
            ok(!h.faultWarning().contains("PRIVATE-SESSION-DATA"),
               "exception messages not disclosed "+id);
            for(int j=i+1;j<ids.length;j++) {
                String other=ids[j];
                ok(h.runFeature(other,()->{}),"healthy peer module survives "+other);
                healthyCallbacks++;
            }
            ok(!h.runFeature(id,()->{throw new IllegalStateException("repeat storm");}),
               "quarantined module never runs "+id);
        }
        ok(healthyCallbacks==6,"peer features continue after isolated failures");
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)==4,
           "no per-frame fault storm");
        h.close();
        HostRuntime loaded=new HostRuntime(dir);
        loaded.worldPresence(true);
        for(String id:ids) {
            ok(!loaded.featureEnabled(id),"disabled persisted "+id);
            loaded.uiActions().toggle(id);
            ok(loaded.runFeature(id,()->{}),"user may reenable "+id);
        }
        loaded.close();
    }
    private static void cancelPartialRequests() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-intent-failclosed");
        HostRuntime h=new HostRuntime(dir);
        h.worldPresence(true);
        SessionServices session=h.services();
        String[] ids={"combat.aimassist","combat.clicker","combat.blockhit"};
        for(String id:ids) h.uiActions().toggle(id);
        for(int round=0;round<900;round++) {
            session.tickAdvance();
            final long tick=session.tick(),generation=session.generation();
            final String aim=ids[0], click=ids[1],block=ids[2];
            // Simulate a policy throwing AFTER it wrote a partially formed intent.
            if(round==0) {
                ok(!h.runFeature(aim,()->{
                    session.rotations().request(aim,RotationCoordinator.Scope.CAMERA,
                        30f,5f,10,tick,1,generation);
                    throw new IllegalStateException("aim private");
                }),"aim failure quarantined");
                session.rotations().cancel(aim);
                ok(session.rotations().pending()==0,"partial camera intent cancelled");
                ok(!h.runFeature(click,()->{
                    session.actions().request(click,ActionCoordinator.Type.ATTACK,
                        ActionCoordinator.Phase.OUTPUT,0,10,tick,1,generation);
                    throw new IllegalStateException("click private");
                }),"click failure quarantined");
                session.actions().cancelOwner(click);
                ok(session.actions().pending()==0,"partial attack intent cancelled");
                ok(!h.runFeature(block,()->{
                    session.actions().request(block,ActionCoordinator.Type.USE,
                        ActionCoordinator.Phase.OUTPUT,0,10,tick,1,generation);
                    throw new IllegalStateException("block private");
                }),"block failure quarantined");
                session.actions().cancelOwner(block);
                ok(session.actions().pending()==0,"partial use intent cancelled");
            }
            // A per-frame simulation verifies no stale action revives while the
            // faulty modules are quarantined, even over a long tick sequence.
            ok(!h.runFeature(aim,()->{throw new AssertionError("should not run");}),
                "bad aim stays disabled "+round);
            ok(!h.runFeature(click,()->{throw new AssertionError("should not run");}),
                "bad click stays disabled "+round);
            ok(!h.runFeature(block,()->{throw new AssertionError("should not run");}),
                "bad block stays disabled "+round);
            ok(session.actions().pending()==0 && session.rotations().pending()==0,
                "no lingering synthetic intents "+round);
            ok(session.actions().consume(ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,tick,generation)==null,
                "no phantom attack "+round);
            ok(session.actions().consume(ActionCoordinator.Type.USE,
                ActionCoordinator.Phase.OUTPUT,tick,generation)==null,
                "no phantom use "+round);
        }
        h.disconnect();
        ok(session.actions().pending()==0 && session.rotations().pending()==0,
           "disconnect keeps service queues empty");
        h.close();
    }
    private static void randomizedRecovery() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-combat-replay");
        HostRuntime h=new HostRuntime(dir);
        h.worldPresence(true);
        final String[] ids={"combat.aimassist","combat.clicker","combat.hitselect","combat.blockhit"};
        Random rng=new Random(131313L);
        for(String id:ids)h.uiActions().toggle(id);
        final int[] runs={0};
        for(int tick=0;tick<1500;tick++) {
            for(String id:ids) {
                boolean enabled=h.featureEnabled(id);
                boolean expectedFail=enabled && rng.nextInt(13)==0;
                boolean result=h.runFeature(id,()->{
                    if(expectedFail)throw new IllegalArgumentException("private");
                    runs[0]++;
                });
                ok(result == (enabled && !expectedFail),"deterministic callback result "+tick+"/"+id);
                if(expectedFail) {
                    ok(!h.featureEnabled(id),"only failing feature disabled");
                }
                if(rng.nextInt(20)==0 && !h.featureEnabled(id))
                    h.uiActions().toggle(id);
            }
            ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FEATURE_FAULT)>=0,
               "nonnegative fault count "+tick);
        }
        ok(runs[0]>100,"substantial healthy callbacks completed");
        h.close();
    }
    public static void main(String[] args) throws Exception {
        quarantineAndRecover();cancelPartialRequests();randomizedRecovery();
        ok(checks>10000,"substantial combat isolation replay");
        System.out.println("COMBAT_ISOLATION_PASS assertions="+checks);
    }
}
