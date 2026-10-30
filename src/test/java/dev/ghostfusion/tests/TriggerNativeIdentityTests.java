package dev.ghostfusion.tests;

import dev.ghostfusion.features.TriggerNativeIdentityFence;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.ActionCoordinator.Type;
import dev.ghostfusion.services.ActionCoordinator.Phase;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native entity-object/world identity across TriggerBot acquisition. */
public final class TriggerNativeIdentityTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String why) {
        boolean refused=false;
        try{action.run();}catch(RuntimeException expected){refused=true;}
        ok(refused,why);
    }
    private static void basic() throws Exception {
        TriggerNativeIdentityFence f=new TriggerNativeIdentityFence();
        UUID a=UUID.fromString("123e4567-e89b-12d3-a456-426614173661");
        UUID b=UUID.fromString("123e4567-e89b-12d3-a456-426614173662");
        Object player=new Object(),other=new Object(),world=new Object(),otherWorld=new Object();
        ok(!f.observe(1,0,true,a,42,player,world),
           "first player observation cannot replace a prior candidate");
        ok(f.permits(1,0,a,42,player,world),
           "first exact original entity may be inspected at native boundary");
        ok(!f.permits(1,0,a,42,other,world),
           "same UUID/ID different actual native EntityPlayer is not original");
        ok(!f.permits(1,0,a,42,player,otherWorld),
           "world replacement denied even if UUID and ID reused");
        ok(!f.permits(1,0,b,42,player,world),
           "another UUID never gets native consent");
        ok(!f.permits(1,0,a,41,player,world),
           "another numeric entity ID never gets consent");
        ok(!f.permits(1,1,a,42,player,world),
           "previous tick cannot authorize a later native attack pulse");
        ok(!f.observe(1,1,true,a,42,player,world),
           "exact same crosshair entity preserves continuous acquisition");
        ok(f.observe(1,2,true,a,42,other,world),
           "replacement object with same UUID/ID invalidates old dwell");
        ok(f.permits(1,2,a,42,other,world),
           "new entity may begin a fresh acquisition");
        ok(f.observe(1,3,true,a,42,other,otherWorld),
           "same UUID and entity but different world invalidates dwell");
        ok(f.observe(1,4,true,b,42,other,otherWorld),
           "new UUID immediately invalidates old dwell");
        ok(f.observe(1,5,false,null,-1,null,null),
           "missing native crosshair clears pending target");
        ok(!f.permits(1,5,b,42,other,otherWorld),
           "lost target cannot dispatch the old entity");
        ok(!f.observe(1,6,false,null,-1,null,null),
           "repeated invalid crosshair does not claim new replacement");
        ok(!f.observe(1,7,true,a,42,player,world),
           "new candidate after a gap starts clean");
        f.invalidate();
        ok(!f.permits(1,7,a,42,player,world),
           "failed final native hit/range check revokes selected candidate");
        ok(!f.observe(2,0,true,a,42,player,world),
           "new Minecraft session never inherits prior target state");
        ok(!f.permits(1,0,a,42,player,world),
           "old world session not permitted after generation switch");
        rejects(()->f.observe(2,0,true,a,42,player,world),
                "same client tick cannot double-advance acquisition");
        rejects(()->f.observe(2,-1,false,null,-1,null,null),
                "invalid negative game time");
        rejects(()->f.observe(2,1,true,null,42,player,world),
                "eligible missing UUID rejected");
        rejects(()->f.observe(2,1,true,a,-1,player,world),
                "eligible missing native ID rejected");
        rejects(()->f.observe(2,1,true,a,42,null,world),
                "eligible missing native entity rejected");
        rejects(()->f.observe(2,1,true,a,42,player,null),
                "eligible missing world rejected");
        AtomicBoolean denied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{f.permits(2,0,a,42,player,world);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        foreign.start();foreign.join();
        ok(denied.get(),"foreign Netty thread cannot read owner-thread native identity");
    }
    private static void dwellAndCooldown() {
        TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        ActionCoordinator attacks=new ActionCoordinator();
        Object entityA=new Object(),replacement=new Object(),world=new Object();
        UUID id=UUID.fromString("123e4567-e89b-12d3-a456-426614173663");
        int acquire=2,cooldown=8;
        // Unheld native target stays under crosshair while Aim Assist yields.
        for(int t=0;t<=2;t++) {
            boolean yielded=AimTriggerHandoff.yieldCamera(true,false,false,false,true,true);
            ok(yielded,"valid unheld live crosshair yields Aim Assist, never skips dwell");
            boolean changed=fence.observe(5,t,yielded,id,14,entityA,world);
            if(changed)trigger.invalidateCandidate();
            boolean due=trigger.due(5,t,yielded,id,acquire,cooldown);
            ok(due==(t==2),"TriggerBot waits complete stable native dwell");
        }
        ok(fence.permits(5,2,id,14,entityA,world),
           "native action only for exact object that earned dwell");
        attacks.request("combat.triggerbot",Type.ATTACK,Phase.OUTPUT,0,10,2,1,5);
        ActionCoordinator.Decision grant=attacks.consume(Type.ATTACK,Phase.OUTPUT,2,5);
        ok(grant!=null&&"combat.triggerbot".equals(grant.owner),
           "single shared coordinator issues one candidate");
        trigger.dispatched(5,2,id);
        ok(!attacks.hasPending(Type.ATTACK,Phase.OUTPUT,2,5),
           "consumed attack cannot be double-dispatched");
        ok(fence.observe(5,3,true,id,14,replacement,world),
           "same UUID/ID replaced native object must restart acquired dwell");
        trigger.invalidateCandidate();
        ok(!trigger.due(5,3,true,id,acquire,cooldown),
           "new object cannot inherit pre-replacement ready state");
        ok(!trigger.due(5,4,true,id,acquire,cooldown),
           "new object must earn two fresh ticks");
        ok(!fence.observe(5,5,true,id,14,replacement,world),
           "new object remains stable");
        ok(!trigger.due(5,5,true,id,acquire,cooldown),
           "old real-use cooldown remains even after replacement");
        ok(!fence.observe(5,6,true,id,14,replacement,world),
           "still same native world entity");
        ok(!trigger.due(5,6,true,id,acquire,cooldown),
           "cannot reset cooldown using a new EntityPlayer object");
        ok(!fence.observe(5,10,true,id,14,replacement,world),
           "stable new native object at cooldown boundary");
        ok(trigger.due(5,10,true,id,acquire,cooldown),
           "after original cooldown and replacement dwell, target may be ready");
        // A native crosshair change AFTER preview must not allow dispatch.
        ok(!fence.permits(5,10,id,14,entityA,world),
           "stale pre-replacement entity cannot consume ready native output");
        trigger.invalidateCandidate();
        fence.invalidate();
        ok(!fence.permits(5,10,id,14,replacement,world),
           "last-moment final native veto remains fail-closed");
        ok(!trigger.due(5,11,true,id,acquire,cooldown),
           "final native veto must reacquire on a later tick");
        ok(!trigger.due(5,12,true,id,acquire,cooldown),
           "final veto does not lend old dwell");
        ok(!trigger.due(5,13,false,null,acquire,cooldown),
           "physical manual attack revokes target and its pending permission");
        ok(trigger.state()==TriggerBotPolicy.State.IDLE,
           "manual physical hold leaves native trigger disarmed");
    }
    private static void seeded() {
        Random r=new Random(366189L);
        TriggerNativeIdentityFence fence=new TriggerNativeIdentityFence();
        UUID id=UUID.fromString("123e4567-e89b-12d3-a456-426614173664");
        UUID otherId=UUID.fromString("123e4567-e89b-12d3-a456-426614173665");
        Object world=new Object(),otherWorld=new Object();
        Object a=new Object(),b=new Object();
        long replaced=0,allowed=0,denied=0;
        for(int i=0;i<80000;i++) {
            fence.reset();
            long epoch=1+i/2000,t=i+10;
            Object original=r.nextBoolean()?a:b;
            fence.observe(epoch,t,true,id,10,original,world);
            int mode=r.nextInt(10);
            Object next=mode==1? (original==a?b:a):original;
            Object nextWorld=mode==2?otherWorld:world;
            UUID uuid=mode==3?otherId:id;
            int entityId=mode==4?11:10;
            boolean eligible=mode!=5;
            boolean changed=fence.observe(epoch,t+1,eligible,
                eligible?uuid:null,eligible?entityId:-1,
                eligible?next:null,eligible?nextWorld:null);
            ok(changed==(mode>=1&&mode<=5),
               "80k source-switch detection for UUID/object/world/ID/missing target "+i);
            boolean correct=eligible && fence.permits(epoch,t+1,
                uuid,entityId,next,nextWorld);
            ok(correct==eligible,"80k exact same-tick accepted entity "+i);
            boolean attackAllowed=eligible && mode<=5;
            boolean finalGrant=fence.permits(epoch,t+1,
                mode==6?otherId:uuid,
                mode==7?12:entityId,
                mode==8?(next==a?b:a):next,
                mode==9?otherWorld:nextWorld);
            boolean expected=eligible && mode!=6 && mode!=7 && mode!=8 && mode!=9;
            ok(finalGrant==expected,
               "80k final native identity mutation veto after acquisition "+i);
            if(changed)replaced++;
            if(finalGrant)allowed++;else denied++;
        }
        ok(replaced>30000&&allowed>30000&&denied>30000,
           "all 80k replacement and last-moment source mutations covered");
        ok(checks>240000,"80k triple-oracle frames executed");
    }
    public static void main(String[] args) throws Exception {
        basic();dwellAndCooldown();seeded();
        System.out.println("TRIGGER_NATIVE_IDENTITY_PASS assertions="+checks);
    }
}
