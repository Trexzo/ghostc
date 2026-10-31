package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.67 exact native player object/world camera identity contract. */
public final class AimNativeIdentityTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    private static void rejects(Runnable run,String why) {
        boolean denied=false;
        try{run.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static UUID id(int n) {return new UUID(0x3670000L,n+42L);}
    private static void exact() throws Exception {
        AimTargetSwitchPolicy policy=new AimTargetSwitchPolicy();
        Object world=new Object(),otherWorld=new Object();
        Object original=new Object(),respawn=new Object(),other=new Object();
        UUID uuid=id(1),newUuid=id(2);
        ok(policy.considerNative(1,0,7,uuid,original,world,3),
            "first target has no switch delay");
        rejects(()->policy.appliedNative(1,0,7,uuid,respawn,world),
            "wrong native object cannot commit");
        rejects(()->policy.applied(1,0,7,uuid),
            "legacy UUID-only commit cannot bypass a native candidate");
        policy.appliedNative(1,0,7,uuid,original,world);
        ok(!policy.considerNative(1,1,7,uuid,respawn,world,3),
            "same UUID and entity ID respawn must reacquire");
        ok(!policy.considerNative(1,2,7,uuid,respawn,world,3),
            "respawn cannot inherit previously applied camera target");
        ok(!policy.considerNative(1,3,7,uuid,respawn,world,3),
            "respawn remains below exact switch dwell");
        ok(policy.considerNative(1,4,7,uuid,respawn,world,3),
            "respawn earns full native dwell");
        policy.cancelPrepared();
        rejects(()->policy.appliedNative(1,4,7,uuid,respawn,world),
            "canceled rotation cannot commit");
        ok(policy.considerNative(1,5,7,uuid,respawn,world,3),
            "stable native candidate survives canceled camera output");
        policy.appliedNative(1,5,7,uuid,respawn,world);
        ok(!policy.considerNative(1,6,7,uuid,respawn,otherWorld,3),
            "same target across world objects is a new native identity");
        ok(!policy.considerNative(1,7,7,uuid,respawn,otherWorld,3),
            "world change cannot inherit old dwell");
        ok(!policy.considerNative(1,8,7,uuid,respawn,otherWorld,3),
            "world change remains below full dwell");
        ok(policy.considerNative(1,9,7,uuid,respawn,otherWorld,3),
            "new world has earned independent dwell");
        policy.appliedNative(1,9,7,uuid,respawn,otherWorld);
        ok(!policy.considerNative(1,10,8,newUuid,other,otherWorld,3),
            "ordinary player switch still delayed");
        ok(policy.considerNative(1,11,8,newUuid,other,otherWorld,0),
            "default zero-delay switching remains immediate");
        policy.appliedNative(1,11,8,newUuid,other,otherWorld);
        policy.reset();
        rejects(()->policy.appliedNative(1,11,8,newUuid,other,otherWorld),
            "reset invalidates previous camera decision");
        rejects(()->policy.considerNative(1,12,8,newUuid,null,otherWorld,3),
            "null entity denied");
        rejects(()->policy.considerNative(1,12,8,newUuid,other,null,3),
            "null world denied");
        AimTargetLock lock=new AimTargetLock();
        ok(lock.preferred(2,0,true)==-1,"new native target lock is empty");
        lock.appliedNative(2,0,7,uuid,original,world);
        ok(lock.sameNativeIdentity(uuid,original,world),"exact native lock valid");
        ok(!lock.sameNativeIdentity(uuid,respawn,world),
            "same UUID and entity ID replacement cannot inherit lock");
        ok(!lock.sameNativeIdentity(uuid,original,otherWorld),
            "world replacement cannot inherit lock");
        lock.clear();
        ok(!lock.sameNativeIdentity(uuid,original,world),"manual override clears lock");
        ok(lock.preferred(3,0,true)==-1,"epoch reset clears hard lock");
        lock.appliedNative(3,0,7,uuid,original,world);
        lock.reset();
        ok(!lock.sameNativeIdentity(uuid,original,world),"panic clears native lock");
        AtomicBoolean foreignDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{policy.considerNative(2,1,7,uuid,original,world,3);}
            catch(IllegalStateException expected){foreignDenied.set(true);}
        });
        foreign.start();foreign.join();
        ok(foreignDenied.get(),"foreign thread cannot mutate owner policy");
    }
    private static void randomReplay() {
        AimTargetSwitchPolicy policy=new AimTargetSwitchPolicy();
        Random r=new Random(3672026L);
        Object[] entities=new Object[5];
        for(int i=0;i<entities.length;i++)entities[i]=new Object();
        Object world=new Object();
        UUID appliedUuid=null,pendingUuid=null;
        Object appliedEntity=null,appliedWorld=null,pendingEntity=null,pendingWorld=null;
        int appliedId=-1,pendingId=-1;
        long since=-1,epoch=5;
        final int delay=3;
        for(int tick=0;tick<80000;tick++) {
            if(tick%1009==0) {
                policy.reset();epoch++;
                appliedId=pendingId=-1;
                appliedUuid=pendingUuid=null;
                appliedEntity=appliedWorld=pendingEntity=pendingWorld=null;
                since=-1;
            }
            if(r.nextInt(71)==0)entities[r.nextInt(entities.length)]=new Object();
            if(r.nextInt(313)==0)world=new Object();
            int picked=r.nextInt(7)-1;
            if(picked<0||picked>=entities.length) {
                policy.reset();appliedId=pendingId=-1;
                appliedUuid=pendingUuid=null;
                appliedEntity=appliedWorld=pendingEntity=pendingWorld=null;
                since=-1;
                continue;
            }
            UUID uuid=id(picked);Object entity=entities[picked];
            boolean sameApplied=appliedId==picked&&uuid.equals(appliedUuid)&&
                appliedEntity==entity&&appliedWorld==world;
            boolean samePending=pendingId==picked&&uuid.equals(pendingUuid)&&
                pendingEntity==entity&&pendingWorld==world;
            boolean expected;
            if(appliedUuid==null||sameApplied) {
                expected=true;
                pendingId=-1;pendingUuid=null;pendingEntity=pendingWorld=null;since=-1;
            }else if(!samePending) {
                expected=false;pendingId=picked;pendingUuid=uuid;
                pendingEntity=entity;pendingWorld=world;since=tick;
            }else expected=tick-since>=delay;
            boolean actual=policy.considerNative(epoch,tick,picked,uuid,entity,world,delay);
            ok(expected==actual,"80k independent exact-identity replay "+tick);
            if(actual) {
                if(r.nextInt(4)==0)policy.cancelPrepared();
                else {
                    policy.appliedNative(epoch,tick,picked,uuid,entity,world);
                    appliedId=picked;appliedUuid=uuid;
                    appliedEntity=entity;appliedWorld=world;
                    pendingId=-1;pendingUuid=null;pendingEntity=pendingWorld=null;since=-1;
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        exact();randomReplay();
        ok(checks>50000,"80k deterministic events include >50k live candidate checks");
        System.out.println("AIM_NATIVE_IDENTITY_PASS assertions="+checks);
    }
}
