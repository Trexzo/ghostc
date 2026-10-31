package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimTargetLock;
import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimCameraReturnFence;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Real native identity is an opaque reference here. Only the Forge adapter
 * verifies actual EntityPlayer, world-registry, mouse and camera authority.
 * Never calls Minecraft or claims real camera motion.
 */
public final class AimNativeRetentionTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void reject(Runnable r,String why) {
        boolean denied=false;
        try {r.run();} catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static void exact() throws Exception {
        AimTargetSwitchPolicy switcher=new AimTargetSwitchPolicy();
        AimTargetLock lock=new AimTargetLock();
        UUID u=new UUID(368,10),v=new UUID(368,11);
        Object world=new Object(),otherWorld=new Object();
        Object first=new Object(),replacement=new Object(),other=new Object();
        ok(switcher.considerNative(1,0,7,u,first,world,3),
           "first native target starts immediately under existing behavior");
        reject(()->switcher.appliedNative(1,0,7,u,replacement,world),
               "prepared native camera target object cannot be swapped");
        reject(()->switcher.appliedNative(1,0,7,u,first,otherWorld),
               "prepared native world cannot be swapped");
        switcher.appliedNative(1,0,7,u,first,world);
        ok(switcher.considerNative(1,1,7,u,first,world,3),
           "same exact native EntityPlayer retains camera target");
        switcher.appliedNative(1,1,7,u,first,world);
        ok(!switcher.considerNative(1,2,7,u,replacement,world,3),
           "SAME UUID and SAME numeric ID but replaced EntityPlayer must delay");
        ok(!switcher.considerNative(1,3,7,u,replacement,world,3),
           "replaced object may not borrow original camera dwell");
        ok(!switcher.considerNative(1,4,7,u,replacement,world,3),
           "replacement stable candidate still short of configured delay");
        ok(switcher.considerNative(1,5,7,u,replacement,world,3),
           "replacement independently earns full three-tick dwell");
        switcher.cancelPrepared();
        reject(()->switcher.appliedNative(1,5,7,u,replacement,world),
               "denied camera request never retains a new native object");
        ok(switcher.considerNative(1,6,7,u,replacement,world,3),
           "stable dwell survives a denied rotation, but no stale commit");
        switcher.appliedNative(1,6,7,u,replacement,world);
        ok(!switcher.considerNative(1,7,7,u,replacement,otherWorld,3),
           "same player and ID in a different world requires reacquisition");
        ok(!switcher.considerNative(1,8,8,v,other,world,3),
           "A-to-B changes native object, ID and UUID and restarts dwell");
        ok(!switcher.considerNative(1,9,7,u,first,world,3),
           "rapid A-B-A switching cannot reuse B candidate or earlier A commit");
        ok(!switcher.considerNative(1,10,7,u,first,world,3),
           "return to A remains delayed despite earlier camera ownership");
        ok(!switcher.considerNative(1,11,7,u,first,world,3),
           "return to A not yet admitted");
        ok(switcher.considerNative(1,12,7,u,first,world,3),
           "stable return to A may reenter after fresh delay");
        switcher.appliedNative(1,12,7,u,first,world);
        ok(!switcher.considerNative(1,13,7,u,replacement,world,3),
           "same UUID/ID replacement still protected after repeated switch");
        ok(switcher.considerNative(1,14,7,u,replacement,world,0),
           "explicit switch-delay zero retains existing immediate legacy behavior");
        switcher.appliedNative(1,14,7,u,replacement,world);
        ok(switcher.considerNative(2,0,7,u,first,otherWorld,3),
           "new client generation is a fresh first target");
        switcher.appliedNative(2,0,7,u,first,otherWorld);
        reject(()->switcher.considerNative(2,-1,7,u,first,world,3),
               "negative world tick rejected");
        reject(()->switcher.considerNative(2,1,7,u,null,world,3),
               "missing native entity cannot borrow identity");
        reject(()->switcher.considerNative(2,1,7,u,first,null,3),
               "missing world cannot borrow identity");
        reject(()->switcher.appliedNative(2,0,7,u,first,otherWorld),
               "already consumed camera rotation cannot double commit");
        switcher.reset();
        ok(switcher.consider(2,2,7,u,3),
           "legacy pure-core target API still accepts first UUID candidate");
        switcher.applied(2,2,7,u);
        ok(switcher.consider(2,3,7,u,3),
           "legacy API continues same player retention unchanged");
        switcher.applied(2,3,7,u);
        ok(!switcher.considerNative(2,4,7,u,first,world,3),
           "legacy UUID retention cannot certify a new native entity identity");
        switcher.reset();

        ok(lock.preferred(5,0,true)==-1,"hard lock initially has no hint");
        lock.appliedNative(5,0,7,u,first,world);
        ok(lock.preferred(5,1,true)==7,"verified hard lock retains native ID as hint");
        ok(lock.sameIdentity(u),"old UUID-only core query remains valid");
        ok(lock.sameNativeIdentity(u,first,world),
           "hard lock requires exact object and world identity");
        ok(!lock.sameNativeIdentity(u,replacement,world),
           "reused UUID+ID object cannot inherit native hard lock");
        ok(!lock.sameNativeIdentity(u,first,otherWorld),
           "world swap cannot retain original target object");
        ok(!lock.sameNativeIdentity(v,first,world),
           "different UUID cannot inherit native hard lock");
        lock.clear();
        ok(!lock.sameNativeIdentity(u,first,world)&&lock.preferred(5,2,true)==-1,
           "native object replacement clears old preferred target completely");
        lock.appliedNative(5,2,7,u,replacement,world);
        ok(lock.sameNativeIdentity(u,replacement,world),
           "hard lock must be explicitly reapplied after real camera write");
        ok(lock.preferred(5,3,false)==-1 &&
           !lock.sameNativeIdentity(u,replacement,world),
           "disabled hard lock clears all native object/world references");
        ok(lock.preferred(6,0,true)==-1,
           "new world generation cannot resurrect previous lock");
        lock.applied(6,0,7,u);
        ok(lock.sameIdentity(u)&&!lock.sameNativeIdentity(u,first,world),
           "legacy pure UUID lock never falsely claims native provenance");
        lock.reset();

        AimCameraReturnFence handback=new AimCameraReturnFence();
        AimManualInputPolicy manual=new AimManualInputPolicy();
        handback.yieldCamera(10,0);
        ok(manual.suppressed(10,0,true,5,0,3,3),
           "manual native mouse motion suppresses camera while Trigger owns it");
        handback.yieldCamera(10,1);
        ok(manual.suppressed(10,1,true,0,0,3,3),
           "manual pause persists through camera yield");
        handback.interrupt();
        switcher.reset();
        lock.reset();
        ok(!manual.suppressed(10,3,true,0,0,3,3),
           "exact physical mouse pause expiry permits new acquisition");
        ok(!handback.consider(10,3,7,u,first,world,2),
           "new camera handback requires native target dwell");
        ok(!handback.consider(10,4,7,u,replacement,world,2),
           "same UUID ID but different native object resets handback");
        ok(!handback.consider(10,5,7,u,replacement,world,2),
           "replacement still has not finished handback dwell");
        ok(handback.consider(10,6,7,u,replacement,world,2),
           "fresh exact native replacement earns handback");
        handback.applied(10,6);
        AtomicBoolean denied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try{switcher.considerNative(2,6,7,u,first,world,3);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();worker.join();
        ok(denied.get(),"foreign Netty thread cannot alter Aim native switching");
    }
    private static void seeded() {
        AimTargetSwitchPolicy policy=new AimTargetSwitchPolicy();
        Random random=new Random(368189L);
        UUID uuidA=new UUID(33,1),uuidB=new UUID(33,2);
        Object worldA=new Object(),worldB=new Object();
        Object objectA=new Object(),objectB=new Object();
        long delayed=0,admitted=0,foreignDenied=0;
        for(int i=0;i<80000;i++) {
            policy.reset();
            long epoch=i+1,t=20L;
            int delay=1+random.nextInt(5);
            Object applied=objectA;
            policy.considerNative(epoch,t,7,uuidA,applied,worldA,delay);
            policy.appliedNative(epoch,t,7,uuidA,applied,worldA);
            int mode=i%8;
            // Same-object stay, same-UUID replacement, world swap, UUID/ID
            // switch and A->B->A all use exact original native identities.
            int id=mode==4?8:7;
            UUID candidateUuid=mode==3?uuidB:uuidA;
            Object candidate=mode==1||mode==5||mode==6?objectB:objectA;
            Object world=mode==2||mode==7?worldB:worldA;
            boolean same=(id==7 && candidateUuid.equals(uuidA) &&
                candidate==objectA && world==worldA);
            int first=1;
            boolean got=policy.considerNative(epoch,t+first,id,candidateUuid,
                candidate,world,delay);
            ok(got==same,"80k independent exact native target continuity oracle "+i);
            if(same) {
                policy.appliedNative(epoch,t+first,id,candidateUuid,candidate,world);
                admitted++;
                continue;
            }
            delayed++;
            if(mode==6) {
                // Switch back to original before full dwell must restart from
                // a new candidate after the interrupted pending replacement.
                candidate=objectA;
                got=policy.considerNative(epoch,t+2,id,candidateUuid,candidate,world,delay);
                ok(!got,"80k rapid A-B-A interruption cannot borrow earlier dwell "+i);
                first=2;
                foreignDenied++;
            }
            for(int dt=1;dt<=delay;dt++) {
                boolean due=policy.considerNative(epoch,t+first+dt,id,candidateUuid,
                    candidate,world,delay);
                ok(due==(dt==delay),"80k fresh exact native dwell deadline "+i+":"+dt);
                if(due) {
                    policy.appliedNative(epoch,t+first+dt,id,candidateUuid,candidate,world);
                    admitted++;
                }
            }
        }
        ok(delayed>64000&&admitted==80000&&foreignDenied>9000,
           "80k native object/world/UUID/ID continuity and interrupted switch cases");
        ok(checks>240000,"substantial independently checked native retention episodes");
    }
    public static void main(String[] args) throws Exception {
        exact();seeded();
        System.out.println("AIM_NATIVE_RETENTION_PASS assertions="+checks);
    }
}
