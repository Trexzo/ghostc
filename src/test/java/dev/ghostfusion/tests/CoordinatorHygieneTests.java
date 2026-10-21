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
    private static void rotationScopeIsolationAndCapacity() {
        RotationCoordinator r=new RotationCoordinator();
        r.request("dual.scope",RotationCoordinator.Scope.CAMERA,10,2,4,25,3,7);
        r.request("dual.scope",RotationCoordinator.Scope.OUTBOUND,80,9,8,25,5,7);
        yes(r.pending()==2,"one owner retains independent camera and outbound intents");
        RotationCoordinator.Decision cam=r.decide(RotationCoordinator.Scope.CAMERA,25,7,0,0);
        RotationCoordinator.Decision out=r.decide(RotationCoordinator.Scope.OUTBOUND,25,7,0,0);
        yes("dual.scope".equals(cam.owner) && cam.yaw==10 && cam.pitch==2,
            "camera request survives outbound intent from same owner");
        yes("dual.scope".equals(out.owner) && out.yaw==80 && out.pitch==9,
            "outbound request survives camera intent from same owner");
        r.request("dual.scope",RotationCoordinator.Scope.CAMERA,42,-12,9,25,2,7);
        yes(r.pending()==2,"same-owner same-scope updates in place");
        yes(r.decide(RotationCoordinator.Scope.CAMERA,25,7,0,0).yaw==42,
            "camera replacement doesn't alter outbound intent");
        yes(r.decide(RotationCoordinator.Scope.OUTBOUND,25,7,0,0).yaw==80,
            "outbound remains independent after camera replacement");
        yes(r.prune(27,7)==1 && r.pending()==1,
            "camera expiry removes only expired scope");
        yes(r.decide(RotationCoordinator.Scope.OUTBOUND,27,7,0,0).yaw==80,
            "longer outbound lifetime survives scoped camera expiry");
        r.cancel("dual.scope");
        yes(r.pending()==0,"cancel owner revokes all scopes");
        r.request("dual.scope",RotationCoordinator.Scope.CAMERA,20,0,1,30,10,7);
        r.request("dual.scope",RotationCoordinator.Scope.OUTBOUND,-20,0,1,30,10,7);
        r.clear();
        yes(r.pending()==0,"clear releases both scopes");
        for(int i=0;i<128;i++){
            String owner="feature."+i;
            r.request(owner,RotationCoordinator.Scope.CAMERA,i,0,1,100,200,8);
            r.request(owner,RotationCoordinator.Scope.OUTBOUND,-i,0,1,100,200,8);
        }
        yes(r.pending()==256,"256 intent slots bounded across both rotation scopes");
        rejected(()->r.request("feature.extra",RotationCoordinator.Scope.CAMERA,
            0,0,1,100,1,8),"257th distinct scoped intent rejected");
        yes(r.pending()==256,"capacity rejection does not mutate existing state");
        r.request("feature.64",RotationCoordinator.Scope.OUTBOUND,-60,2,5,100,100,8);
        yes(r.pending()==256,"existing scope replacement is legal at full capacity");
        yes(r.decide(RotationCoordinator.Scope.OUTBOUND,100,8,0,0).yaw==-60,
            "higher priority updated outbound request wins after capacity replacement");
        r.cancel("feature.64");
        yes(r.pending()==254,"cancellation frees exactly two owner scopes");
        r.request("feature.extra",RotationCoordinator.Scope.CAMERA,90,0,7,100,1,8);
        r.request("feature.extra",RotationCoordinator.Scope.OUTBOUND,91,0,7,100,1,8);
        yes(r.pending()==256,"capacity recycled after paired cancellation");
        yes(r.prune(101,8)==2 && r.pending()==254,
            "expired owner-scoped intents release both capacity slots");
        yes(r.prune(300,8)==254 && r.pending()==0,
            "all remaining intents expire at bounded lifetime");
        r.shutdown();

        // Deterministic long sessions repeatedly submit dual scoped requests
        // from the same 40 owners and rotate the generation. No stale intent
        // may leak into a new world or erase its sibling scope.
        RotationCoordinator replay=new RotationCoordinator();
        for(int tick=1;tick<=4500;tick++){
            long generation=(tick/900)+1;
            if(tick%900==0)replay.clear();
            String owner="module."+tick%40;
            replay.request(owner,RotationCoordinator.Scope.CAMERA,
                tick%360,0,tick,tick,2,generation);
            replay.request(owner,RotationCoordinator.Scope.OUTBOUND,
                -(tick%360),0,tick,tick,2,generation);
            yes(replay.pending()<=80,"long session remains bounded with two scope keys");
            yes(owner.equals(replay.decide(RotationCoordinator.Scope.CAMERA,tick,generation,0,0).owner),
                "latest camera intent available at owner tick");
            yes(owner.equals(replay.decide(RotationCoordinator.Scope.OUTBOUND,tick,generation,0,0).owner),
                "latest outbound intent available at owner tick");
            replay.prune(tick+1,generation);
        }
        replay.clear();
        yes(replay.pending()==0,"dual-scope replay releases all intents");
        replay.shutdown();
    }
    private static void actionPhaseIsolationAndCapacity() {
        ActionCoordinator a=new ActionCoordinator();
        a.request("dual.action",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.PRE_INPUT,10,1,25,4,7);
        a.request("dual.action",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,20,5,25,6,7);
        yes(a.pending()==2,"same owner retains independent action phases");
        ActionCoordinator.Decision pre=a.consume(
            ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.PRE_INPUT,25,7);
        yes(pre!=null && pre.argument==10 && pre.phase==ActionCoordinator.Phase.PRE_INPUT,
            "early phase survives later phase request");
        yes(a.consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,25,7)==null,
            "at most one action per TYPE per tick despite two phases");
        yes(a.pending()==1,"unconsumed other phase preserved");
        ActionCoordinator.Decision output=a.consume(
            ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.OUTPUT,26,7);
        yes(output!=null && output.argument==20,
            "other phase eligible on later tick within its TTL");
        yes(a.pending()==0,"consumed phases release both entries");

        a.request("dual.action",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.PRE_INPUT,1,1,40,3,7);
        a.request("dual.action",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,2,1,40,6,7);
        a.request("dual.action",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.PRE_INPUT,3,2,40,2,7);
        yes(a.pending()==2,"same phase update replaces only its own request");
        ActionCoordinator.Decision first=a.consume(
            ActionCoordinator.Type.USE,ActionCoordinator.Phase.PRE_INPUT,40,7);
        yes(first!=null && first.argument==3,"same-phase replacement value is authoritative");
        yes(a.consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,41,7).argument==2,
            "other phase survives replacement and later consumption");
        a.request("dual.action",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.PRE_INPUT,0,1,50,2,7);
        a.request("dual.action",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,1,50,2,7);
        a.cancelOwner("dual.action");
        yes(a.pending()==0,"owner cancellation revokes all types and phases");

        for(int i=0;i<128;i++){
            String owner="feature."+i;
            a.request(owner,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.PRE_INPUT,i,1,100,100,8);
            a.request(owner,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,i,1,100,100,8);
        }
        yes(a.pending()==256,"256 owner/type/phase slots bounded");
        rejected(()->a.request("extra",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.PRE_INPUT,0,1,100,1,8),
            "257th distinct action intent refused without eviction");
        yes(a.pending()==256,"capacity refusal keeps existing requests");
        a.request("feature.64",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,999,3,100,100,8);
        yes(a.pending()==256,"same owner/type/phase replacement allowed at capacity");
        yes(a.consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,100,8).argument==999,
            "priority replacement visible at full capacity");
        a.cancelOwner("feature.64");
        yes(a.pending()==254,"cancel paired phase intents after consume");
        // One 'feature.64' request was consumed, so cancelOwner removes
        // its remaining PRE_INPUT entry; another independent slot remains.
        a.cancelOwner("feature.0");
        yes(a.pending()==252,"owner cancellation removes every remaining phase");
        a.request("extra",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.PRE_INPUT,0,1,101,1,8);
        a.request("extra",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,1,101,1,8);
        yes(a.pending()==254,"vacant phase slots can be reused");
        yes(a.prune(102,8)==2,"short-lived added phases expire together");
        yes(a.prune(200,8)==252 && a.pending()==0,
            "long-lived owner phases fully expire");
        a.shutdown();

        // 4,000 owner-tick cycles stress composite keys, phase selection,
        // generation changes, cancel and expiration without retained intents.
        ActionCoordinator replay=new ActionCoordinator();
        for(int tick=1;tick<=4000;tick++){
            long gen=tick/800+1;
            if(tick%800==0)replay.reset();
            String owner="module."+tick%40;
            replay.request(owner,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.PRE_INPUT,1,2,tick,2,gen);
            replay.request(owner,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,2,4,tick,2,gen);
            yes(replay.pending()<=80,"long action phase replay bounded");
            yes(replay.consume(ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.PRE_INPUT,tick,gen)!=null,
                "early phase proposed on current tick");
            yes(replay.consume(ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,tick,gen)==null,
                "one type only across phases at same tick");
            replay.cancelOwner(owner);
            replay.prune(tick+1,gen);
            yes(replay.pending()==0,"phase replay leaves no uncancelled work");
        }
        replay.shutdown();
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
        futureIntents();rotationScopeIsolationAndCapacity();
        actionPhaseIsolationAndCapacity();longSession();ownership();
        yes(checks>3500,"substantial coordinator hygiene assertions");
        System.out.println("COORDINATOR_HYGIENE_PASS assertions="+checks);
    }
}
