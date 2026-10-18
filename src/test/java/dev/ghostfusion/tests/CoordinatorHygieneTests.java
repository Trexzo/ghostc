package dev.ghostfusion.tests;

import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.util.concurrent.atomic.AtomicBoolean;

/** Regression for G3 phase/future request fences and long-running intent hygiene. */
public final class CoordinatorHygieneTests {
    private static int checks;
    private static void yes(boolean test,String reason) {
        checks++;
        if(!test) throw new AssertionError(reason);
    }
    private static void rejected(Runnable task,String reason) {
        boolean fails=false;
        try { task.run(); } catch(RuntimeException expected) { fails=true; }
        yes(fails,reason);
    }
    private static void futureIntents() {
        ActionCoordinator actions=new ActionCoordinator();
        actions.request("m.future",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,3,10,2,5);
        yes(actions.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,9,5)==null,
            "future action not applied a tick early");
        yes(actions.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,10,4)==null,
            "action rejected from wrong epoch");
        yes(actions.pending()==1,"wrong epoch doesn't destructively consume");
        yes(actions.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,10,5)!=null,
            "action usable exactly at start");
        yes(actions.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,10,5)==null,
            "same type cannot consume twice");
        actions.request("m.later",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,2,12,2,5);
        yes(actions.prune(11,5)==0,"does not prune future requests");
        yes(actions.prune(14,5)==1,"prunes expired request");
        yes(actions.pending()==0,"no actions left");
        rejected(()->actions.prune(-1,5),"reject negative prune tick");
        rejected(()->actions.prune(1,-1),"reject invalid generation");
        actions.shutdown();

        RotationCoordinator rotations=new RotationCoordinator();
        rotations.request("m.aim",RotationCoordinator.Scope.CAMERA,50,5,2,10,3,5);
        yes(rotations.decide(RotationCoordinator.Scope.CAMERA,9,5,2,3).owner==null,
            "future camera request not applied early");
        yes("m.aim".equals(rotations.decide(RotationCoordinator.Scope.CAMERA,10,5,2,3).owner),
            "camera request active at requested tick");
        yes(rotations.prune(10,6)==1,"wrong-session intent cleaned");
        yes(rotations.decide(RotationCoordinator.Scope.CAMERA,11,5,2,3).owner==null,
            "pruned camera request never revives");
        rotations.request("m.aim",RotationCoordinator.Scope.OUTBOUND,19,-4,2,13,3,5);
        yes(rotations.prune(15,5)==0,"not yet expired");
        yes(rotations.prune(16,5)==1,"outbound rotation expiry pruned");
        rejected(()->rotations.prune(-1,5),"rotation prune invalid tick");
        rotations.shutdown();
    }
    private static void longSession() {
        SessionServices s=new SessionServices();
        s.world(true);
        for(int i=0;i<1800;i++) {
            s.tickAdvance();
            long tick=s.tick(),epoch=s.generation();
            s.actions().request("m."+i,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,0,i%10,tick,1,epoch);
            s.rotations().request("r."+i,RotationCoordinator.Scope.CAMERA,
                i%360,0,i%3,tick,1,epoch);
            yes(s.actions().pending()==1,"one action carried in frame "+i);
            yes(s.rotations().pending()==1,"one rotation carried in frame "+i);
        }
        s.tickAdvance();
        yes(s.actions().pending()==0 && s.rotations().pending()==0,
            "long-running requests expire with no dispatch");
        // Generation changes atomically clear any new requests.
        s.actions().request("m.current",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,1,s.tick(),2,s.generation());
        s.rotations().request("r.current",RotationCoordinator.Scope.CAMERA,
            10,5,1,s.tick(),2,s.generation());
        s.disconnect();
        yes(s.actions().pending()==0 && s.rotations().pending()==0,"disconnect resets both queues");
        s.world(true);
        s.tickAdvance();
        yes(s.actions().pending()==0 && s.rotations().pending()==0,"reconnect remains clear");
        s.close();
    }
    private static void ownership() throws Exception {
        ActionCoordinator a=new ActionCoordinator();
        RotationCoordinator r=new RotationCoordinator();
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread thread=new Thread(()->{
            try { a.prune(10,1); } catch(IllegalStateException expected) { refused.set(true); }
        });
        thread.start();thread.join();
        yes(refused.get(),"off-thread action prune rejected");
        refused.set(false);
        Thread second=new Thread(()->{
            try { r.prune(10,1); } catch(IllegalStateException expected) { refused.set(true); }
        });
        second.start();second.join();
        yes(refused.get(),"off-thread rotation prune rejected");
        a.shutdown();r.shutdown();
        rejected(()->a.prune(10,1),"stopped action cannot be reused");
        rejected(()->r.prune(10,1),"stopped rotation cannot be reused");
    }
    public static void main(String[] args) throws Exception {
        futureIntents();longSession();ownership();
        yes(checks>3500,"substantial coordinator hygiene assertions");
        System.out.println("COORDINATOR_HYGIENE_PASS assertions="+checks);
    }
}
