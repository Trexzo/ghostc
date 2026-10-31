package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimSnapshotNativeFence;
import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-thread, nonpersistent native provenance for immutable Aim snapshots. */
public final class AimSnapshotNativeFenceTests {
    private static long checks;
    private static void ok(boolean condition,String why) {
        checks++;
        if(!condition)throw new AssertionError(why);
    }
    private static void rejects(Runnable run,String why) {
        boolean rejected=false;
        try {run.run();}catch(RuntimeException expected){rejected=true;}
        ok(rejected,why);
    }
    private static boolean matches(AimSnapshotNativeFence f,long epoch,long tick,
                                   int id,UUID uuid,Object entity,Object world,
                                   Object player,Object registered) {
        return f.matches(epoch,tick,id,uuid,entity,world,player,registered);
    }
    private static void boundaries() throws Exception {
        AimSnapshotNativeFence fence=new AimSnapshotNativeFence();
        Object world=new Object(),otherWorld=new Object();
        Object self=new Object(),otherSelf=new Object();
        Object entity=new Object(),replacement=new Object();
        UUID uuid=new UUID(369,1),otherUuid=new UUID(369,2);
        ok(!matches(fence,1,0,9,uuid,entity,world,self,entity),
           "unpublished native entity cannot authorize camera");
        rejects(()->fence.begin(-1,0,world,self),"invalid generation refused");
        rejects(()->fence.begin(1,0,null,self),"unbound world refused");
        rejects(()->fence.begin(1,0,world,null),"unbound local player refused");
        fence.begin(1,0,world,self);
        ok(!matches(fence,1,0,9,uuid,entity,world,self,entity),
           "missing snapshot row may not authorize an aim suggestion");
        fence.record(9,uuid,entity);
        ok(matches(fence,1,0,9,uuid,entity,world,self,entity),
           "exact same-source native world/entity/UUID row is admitted");
        ok(!matches(fence,1,0,9,uuid,replacement,world,self,replacement),
           "replacement same numeric ID and UUID at identical pose rejected");
        ok(!matches(fence,1,0,9,otherUuid,entity,world,self,entity),
           "changed native UUID rejected");
        ok(!matches(fence,1,0,10,uuid,entity,world,self,entity),
           "different snapshot entity ID rejected");
        ok(!matches(fence,1,0,9,uuid,entity,otherWorld,self,entity),
           "world replacement rejected even if native player object unchanged");
        ok(!matches(fence,1,0,9,uuid,entity,world,otherSelf,entity),
           "local source-player recreation invalidates old snapshot");
        ok(!matches(fence,1,0,9,uuid,entity,world,self,replacement),
           "current-world registry object must still equal original entity");
        ok(!matches(fence,2,0,9,uuid,entity,world,self,entity),
           "wrong session generation cannot borrow old native witness");
        ok(!matches(fence,1,1,9,uuid,entity,world,self,entity),
           "same entity in later frame cannot borrow old snapshot row");
        ok(!matches(fence,1,0,9,uuid,null,world,self,null),
           "despawned entity cannot pass original witness");
        ok(!matches(fence,1,0,9,uuid,entity,null,self,entity),
           "null-world native candidate cannot pass");
        ok(!matches(fence,1,0,9,uuid,entity,world,null,entity),
           "null-source native candidate cannot pass");
        rejects(()->fence.record(9,uuid,replacement),
                "duplicate numeric ID invalidates entire captured witness");
        ok(fence.size()==0 && !matches(fence,1,0,9,uuid,entity,world,self,entity),
           "duplicate build fail closes and releases retained references");

        fence.begin(1,2,world,self);
        rejects(()->fence.record(9,uuid,self),"native local player cannot enter enemy row");
        ok(fence.size()==0,"invalid self-target clears witness");
        fence.begin(1,3,world,self);
        rejects(()->fence.record(9,null,entity),"missing UUID invalidates row");
        ok(fence.size()==0,"invalid UUID clears snapshot");
        fence.begin(1,4,world,self);
        for(int i=0;i<AimSnapshotNativeFence.MAX_ROWS;i++)
            fence.record(i+1,new UUID(369,i+1),new Object());
        ok(fence.size()==AimSnapshotNativeFence.MAX_ROWS,
           "exact capped snapshot allowance supports existing 514 max");
        rejects(()->fence.record(600,new UUID(369,600),new Object()),
                "snapshot witness cannot retain a 515th entity");
        ok(fence.size()==0,"overflow fail closes and erases all native row refs");
        fence.begin(9,100,world,self);
        fence.record(9,uuid,entity);
        AtomicBoolean denied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try {fence.matches(9,100,9,uuid,entity,world,self,entity);}
            catch(IllegalStateException expected){denied.set(true);}
        });foreign.start();foreign.join();
        ok(denied.get(),"foreign render or Netty thread cannot access owner witness");
        fence.clear();
        ok(fence.size()==0 &&
           !matches(fence,9,100,9,uuid,entity,world,self,entity),
           "combat finally clears all native references before later modules");
    }
    private static void nativeSnapshotSimulation() {
        Object world=new Object(),self=new Object();
        Object original=new Object(),reborn=new Object();
        UUID uuid=new UUID(369,999);
        List<WorldSnapshot.Entity> entities=new ArrayList<WorldSnapshot.Entity>();
        entities.add(new WorldSnapshot.Entity(15,0,0,3,20,true,true,true));
        WorldSnapshot snap=new WorldSnapshot(21,7,0,0,0,0,0,entities);
        AimAssistPolicy aim=new AimAssistPolicy();
        AimAssistPolicy.Suggestion proposal=aim.suggest(
            snap,1.62,1.62,6.0,90.0,4.0,true);
        ok(proposal!=null && proposal.entityId==15,
           "real immutable AimAssist suggestion picks original snapshot row");
        ok(proposal.sameTargetPosition(0,0,3),
           "recreated native entity can occupy identical copied coordinates");
        AimSnapshotNativeFence witness=new AimSnapshotNativeFence();
        witness.begin(21,7,world,self);
        witness.record(15,uuid,original);
        ok(!witness.matches(21,7,proposal.entityId,uuid,reborn,world,self,reborn),
           "same pose, entity ID, UUID AND world but NEW object cannot borrow Aim snapshot");
        ok(witness.matches(21,7,proposal.entityId,uuid,original,world,self,original),
           "original native instance passes independent publication witness");
        witness.clear();
        ok(!witness.matches(21,7,proposal.entityId,uuid,original,world,self,original),
           "snapshot may outlive ephemeral native witness without camera permission");
    }
    private static void seeded() {
        Random r=new Random(369189L);
        AimSnapshotNativeFence f=new AimSnapshotNativeFence();
        Object worldA=new Object(),worldB=new Object(),sourceA=new Object(),sourceB=new Object();
        Object candidateA=new Object(),candidateB=new Object();
        UUID uuidA=new UUID(17,1),uuidB=new UUID(17,2);
        long good=0,bad=0;
        for(int i=0;i<80000;i++) {
            long epoch=i+1,tick=7+i;
            f.begin(epoch,tick,worldA,sourceA);
            f.record(7,uuidA,candidateA);
            int mode=r.nextInt(10);
            int id=mode==1?8:7;
            UUID uuid=mode==2?uuidB:uuidA;
            Object entity=mode==3?candidateB:candidateA;
            Object world=mode==4?worldB:worldA;
            Object player=mode==5?sourceB:sourceA;
            Object registered=mode==6?candidateB:entity;
            long frame=mode==7?tick+1:tick;
            long generation=mode==8?epoch+1:epoch;
            boolean cleared=mode==9;
            if(cleared)f.clear();
            boolean outcome=matches(f,generation,frame,id,uuid,entity,world,player,registered);
            boolean expected=mode==0;
            ok(outcome==expected,"80k independent exact capture-to-camera identity oracle "+i);
            if(outcome)good++;else bad++;
            f.clear();
            ok(f.size()==0,"no native entity reference escapes END-frame combat "+i);
        }
        ok(good>7000 && bad>70000 && checks>160000,
           "80k positive/negative snapshots and 80k cleanup assertions exercised");
    }
    public static void main(String[] args) throws Exception {
        boundaries();nativeSnapshotSimulation();seeded();
        System.out.println("AIM_SNAPSHOT_NATIVE_FENCE_PASS assertions="+checks);
    }
}
