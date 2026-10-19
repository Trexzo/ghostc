package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldIdentityTracker;
import dev.ghostfusion.services.SessionServices;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R1.4: exact world/player identity, missing-snapshot fences and state transitions. */
public final class WorldLifecycleTests {
    private static int checks;
    private static void ok(boolean yes,String what) {
        checks++;
        if(!yes) throw new AssertionError(what);
    }
    private static void basic() throws Exception {
        WorldIdentityTracker tracker=new WorldIdentityTracker();
        Object world1=new Object(), world2=new Object(), player1=new Object(), player2=new Object();
        ok(tracker.observe(null,null)==WorldIdentityTracker.Transition.ABSENT,"initial empty");
        ok(tracker.observe(world1,null)==WorldIdentityTracker.Transition.ABSENT,"partial world not ready");
        ok(!tracker.ready(),"partial world rejected");
        ok(tracker.observe(world1,player1)==WorldIdentityTracker.Transition.ENTER,"first full join");
        ok(tracker.ready(),"ready once both exist");
        ok(tracker.observe(world1,player1)==WorldIdentityTracker.Transition.STABLE,"identity stable");
        ok(tracker.observe(world2,player1)==WorldIdentityTracker.Transition.SWITCH,
           "dimension swap without absent frame");
        ok(tracker.observe(world2,player2)==WorldIdentityTracker.Transition.SWITCH,
           "local player replacement without absent frame");
        ok(tracker.observe(world2,player2)==WorldIdentityTracker.Transition.STABLE,"new identity stable");
        ok(tracker.observe(null,player2)==WorldIdentityTracker.Transition.LEAVE,"null world exits");
        ok(!tracker.ready(),"exit releases identity");
        ok(tracker.observe(world2,player2)==WorldIdentityTracker.Transition.ENTER,"new join");
        tracker.reset();
        ok(!tracker.ready(),"scheduled network disconnect clears identity");
        ok(tracker.observe(world2,player2)==WorldIdentityTracker.Transition.ENTER,
           "post-network join starts new generation");
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try {tracker.observe(world1,player1);}catch(IllegalStateException e){rejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(rejected.get(),"owner-thread fence");
    }
    private static void snapshots() {
        ok(WorldSnapshot.validPlayerPose(0,0,0,0,0),"valid origin pose");
        ok(WorldSnapshot.validPlayerPose(123,-128,42,720,-90),"rotation wrap permitted");
        ok(WorldSnapshot.validPlayerPose(-42,3.5,99,-361,90),"pitch maximum accepted");
        ok(!WorldSnapshot.validPlayerPose(Double.NaN,0,0,0,0),"reject nonfinite player x");
        ok(!WorldSnapshot.validPlayerPose(0,Double.POSITIVE_INFINITY,0,0,0),"reject nonfinite y");
        ok(!WorldSnapshot.validPlayerPose(0,0,Double.NEGATIVE_INFINITY,0,0),"reject nonfinite z");
        ok(!WorldSnapshot.validPlayerPose(0,0,0,Float.NaN,0),"reject nonfinite yaw");
        ok(!WorldSnapshot.validPlayerPose(0,0,0,0,Float.NaN),"reject nonfinite pitch");
        ok(!WorldSnapshot.validPlayerPose(0,0,0,0,90.01f),"reject upper invalid pitch");
        ok(!WorldSnapshot.validPlayerPose(0,0,0,0,-90.01f),"reject lower invalid pitch");
        SessionServices s=new SessionServices();
        s.world(true);
        s.tickAdvance();
        WorldSnapshot original=new WorldSnapshot(s.generation(),s.tick(),0,0,0,0,0,
            Collections.<WorldSnapshot.Entity>emptyList());
        s.publish(original);
        ok(s.snapshot()==original,"snapshot accepted for exact session tick");
        s.actions().request("click",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,1,s.tick(),2,s.generation());
        s.rotations().request("aim",RotationCoordinator.Scope.CAMERA,0,0,1,
            s.tick(),2,s.generation());
        s.tickAdvance();
        ok(s.snapshot()==null,"snapshot expires at next tick before publisher runs");
        ok(s.rotations().pending()==1 && s.actions().pending()==1,
           "intent pruning and snapshot lifecycle separate");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,s.tick(),s.generation())!=null,
            "coordinator still independently applies its documented TTL");
        WorldSnapshot later=new WorldSnapshot(s.generation(),s.tick(),0,0,0,0,0,
            Collections.<WorldSnapshot.Entity>emptyList());
        s.publish(later);
        ok(s.snapshot()==later,"valid new publisher snapshot replaces it");
        s.tickAdvance();
        ok(s.snapshot()==null,"every new tick requires fresh publication");
        boolean rejected=false;
        try {s.publish(later);}catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"stale frame cannot republish");
        s.world(false);
        ok(s.snapshot()==null,"world leave always clears snapshot");
        s.close();
    }
    private static void randomizedTransitions() throws Exception {
        Random rng=new Random(20261019L);
        Object[] worlds={new Object(),new Object(),new Object(),new Object()};
        Object[] players={new Object(),new Object(),new Object(),new Object()};
        WorldIdentityTracker tracker=new WorldIdentityTracker();
        HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-world-identity-"));
        Object oldWorld=null,oldPlayer=null;
        for(int step=0;step<13000;step++) {
            int wPick=rng.nextInt(worlds.length+2);
            int pPick=rng.nextInt(players.length+2);
            Object currentWorld=wPick<worlds.length?worlds[wPick]:null;
            Object currentPlayer=pPick<players.length?players[pPick]:null;
            boolean oldReady=oldWorld!=null&&oldPlayer!=null;
            boolean newReady=currentWorld!=null&&currentPlayer!=null;
            WorldIdentityTracker.Transition expect=!newReady?
                (oldReady?WorldIdentityTracker.Transition.LEAVE:WorldIdentityTracker.Transition.ABSENT):
                !oldReady?WorldIdentityTracker.Transition.ENTER:
                (oldWorld==currentWorld&&oldPlayer==currentPlayer)?
                WorldIdentityTracker.Transition.STABLE:WorldIdentityTracker.Transition.SWITCH;
            WorldIdentityTracker.Transition got=tracker.observe(currentWorld,currentPlayer);
            ok(got==expect,"world transition classification "+step);
            long generation=host.services().generation();
            if(got==WorldIdentityTracker.Transition.SWITCH||
               got==WorldIdentityTracker.Transition.LEAVE)host.worldPresence(false);
            if(got==WorldIdentityTracker.Transition.ENTER||
               got==WorldIdentityTracker.Transition.SWITCH)host.worldPresence(true);
            host.worldPresence(newReady);
            long change=got==WorldIdentityTracker.Transition.SWITCH?2:
                (got==WorldIdentityTracker.Transition.LEAVE ||
                 got==WorldIdentityTracker.Transition.ENTER)?1:0;
            ok(host.services().generation()==generation+change,
               "host session generation invalidation "+step);
            ok(host.worldPresent()==newReady && tracker.ready()==newReady,
               "host/tracker readiness parity "+step);
            host.tick();
            ok(host.services().snapshot()==null,"no stale snapshot across ticks "+step);
            oldWorld=newReady?currentWorld:null;
            oldPlayer=newReady?currentPlayer:null;
        }
        host.close();
    }
    public static void main(String[] args) throws Exception {
        basic(); snapshots(); randomizedTransitions();
        ok(checks>50000,"large deterministic world lifecycle replay");
        System.out.println("WORLD_LIFECYCLE_PASS assertions="+checks);
    }
}
