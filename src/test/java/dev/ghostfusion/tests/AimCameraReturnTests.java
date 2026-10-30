package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimCameraReturnFence;
import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native camera handback admission, not a launched Minecraft test. */
public final class AimCameraReturnTests {
    private static long assertions;
    private static void ok(boolean good,String why) {
        assertions++;
        if(!good)throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String why) {
        boolean threw=false;
        try{action.run();}catch(RuntimeException expected){threw=true;}
        ok(threw,why);
    }

    private static void exact() throws Exception {
        AimCameraReturnFence f=new AimCameraReturnFence();
        Object world=new Object(),otherWorld=new Object();
        Object a=new Object(),b=new Object();
        UUID id=new UUID(1234,5678),otherId=new UUID(1234,5679);
        ok(f.consider(1,0,21,id,a,world,3),
            "without TriggerBot camera yield legacy first target is immediate");
        ok(!f.pending(),"ordinary aim movement does not create handback");
        rejects(()->f.applied(1,0),
                "ordinary camera output cannot commit a nonexistent handback");
        f.yieldCamera(1,1);
        ok(f.pending(),"TriggerBot valid native crosshair owns camera tick");
        ok(!f.consider(1,2,21,id,a,world,3),
            "first return frame does not bypass configured delay");
        ok(!f.consider(1,3,21,id,a,world,3),
            "consecutive return candidate remains delayed");
        ok(!f.consider(1,4,21,id,a,world,3),
            "third return frame still before exact deadline");
        ok(f.consider(1,5,21,id,a,world,3),
            "same exact live candidate earns configured handback dwell");
        rejects(()->f.applied(1,4),
                "old frame cannot commit prepared handback rotation");
        ok(f.pending(),"failed commit cannot release camera handback");
        f.applied(1,5);
        ok(!f.pending(),"only completed native camera write releases handback");
        rejects(()->f.applied(1,5),
                "one native camera write cannot double-commit handback");
        ok(f.consider(1,6,21,id,b,world,3),
            "normal Aim switch policy owns later target changes after release");
        f.yieldCamera(1,7);
        ok(!f.consider(1,8,21,id,a,world,2),
            "return from repeated yield starts independent dwell");
        ok(!f.consider(1,9,21,id,b,world,2),
            "same UUID and ID with replaced entity object resets dwell");
        ok(!f.consider(1,10,21,id,b,otherWorld,2),
            "same entity with different world cannot inherit dwell");
        ok(!f.consider(1,11,21,otherId,b,otherWorld,2),
            "same object and world with changed UUID must reacquire");
        ok(!f.consider(1,12,22,otherId,b,otherWorld,2),
            "changed native entity ID must reacquire");
        ok(!f.consider(1,13,22,otherId,b,otherWorld,2),
            "stable new entity below reacquisition deadline");
        ok(f.consider(1,14,22,otherId,b,otherWorld,2),
            "new entity independently earns full dwell");
        f.interrupt();
        ok(f.pending(),"manual override cancels pending dwell, not handback");
        rejects(()->f.applied(1,14),
                "interrupted prepared camera output cannot be committed");
        ok(!f.consider(1,15,22,otherId,b,otherWorld,2),
            "manual motion requires new uninterrupted return candidate");
        f.yieldCamera(1,16);
        ok(f.consider(1,17,21,id,a,world,0),
            "zero delay explicitly preserves old immediate behavior");
        f.applied(1,17);
        ok(!f.pending(),"immediate legacy handback still needs real camera commit");
        f.yieldCamera(1,18);
        ok(!f.consider(1,19,21,id,a,world,3),
            "new yield never inherits older return candidate");
        ok(f.consider(1,20,21,id,a,world,0),
            "live settings edit to zero delay unlocks explicit legacy behavior");
        f.applied(1,20);
        f.yieldCamera(2,0);
        ok(!f.consider(2,1,21,id,a,world,2),
            "fresh world generation cannot borrow old handback time");
        f.reset();
        ok(!f.pending(),"world/focus/Panic reset revokes handback");
        ok(f.consider(2,1,21,id,a,world,2),
            "after reset ordinary first target remains immediate");
        rejects(()->f.consider(2,1,21,id,a,world,2),
                "same client END frame cannot advance admission twice");
        rejects(()->f.yieldCamera(2,0),"retrograde camera yield cannot alter state");
        rejects(()->f.consider(2,2,21,null,a,world,2),
                "missing UUID never authorizes native return");
        rejects(()->f.consider(2,3,21,id,a,null,2),
                "missing native world never authorizes return");
        rejects(()->f.consider(2,4,21,id,a,world,11),
                "unbounded handback delay rejected");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{f.interrupt();}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        foreign.start();foreign.join();
        ok(blocked.get(),"Netty/foreign thread may not modify camera return");
    }

    private static void mouseAndTrigger() {
        AimCameraReturnFence gate=new AimCameraReturnFence();
        AimManualInputPolicy mouse=new AimManualInputPolicy();
        AimTargetSwitchPolicy switcher=new AimTargetSwitchPolicy();
        UUID id=new UUID(365,11);
        Object player=new Object(),world=new Object();
        boolean yielded=AimTriggerHandoff.yieldCamera(
            true,false,false,false,true,true);
        ok(yielded,"genuine unheld TriggerBot candidate takes camera ownership");
        gate.yieldCamera(11,0);
        ok(!mouse.suppressed(11,0,true,0,0,3,3),
           "first yield frame observes still mouse even without Aim write");
        gate.yieldCamera(11,1);
        ok(mouse.suppressed(11,1,true,4,0,3,3),
           "actual physical mouse movement DURING trigger yield starts hold");
        gate.yieldCamera(11,2);
        ok(mouse.suppressed(11,2,true,0,0,3,3),
           "mouse hold remains active even while Trigger owns camera");
        ok(mouse.suppressed(11,3,true,0,0,3,3),
           "first return frame remains manually suppressed");
        gate.interrupt();
        ok(!mouse.suppressed(11,4,true,0,0,3,3),
           "manual pause eventually expires with no stale retained hold");
        ok(!gate.consider(11,4,42,id,player,world,2),
           "only now a candidate can start return dwell");
        ok(!gate.consider(11,5,42,id,player,world,2),
           "return candidate must remain stable");
        ok(gate.consider(11,6,42,id,player,world,2),
           "return target earns its independent native handback dwell");
        ok(switcher.consider(11,6,42,id,2),
           "ordinary Aim switch policy may stage first candidate after yield");
        switcher.applied(11,6,42,id);
        gate.applied(11,6);
        ok(!gate.pending(),"both state machines commit only after native rotation");
        // A new TriggerBot candidate takes camera ownership only when native
        // crosshair is valid, never due to stale target/synthetic key alone.
        ok(!AimTriggerHandoff.yieldCamera(
            true,false,false,false,true,false),
           "lost crosshair gives no Trigger camera grant");
        ok(!AimTriggerHandoff.yieldCamera(
            true,true,false,false,true,true),
           "physical attack remains authoritative");
    }

    private static void seeded() {
        Random r=new Random(367189L);
        AimCameraReturnFence fence=new AimCameraReturnFence();
        Object w=new Object(),otherWorld=new Object();
        Object a=new Object(),b=new Object();
        UUID ua=new UUID(17,17),ub=new UUID(17,18);
        long ready=0,delayed=0,interruptions=0;
        for(int i=0;i<60000;i++) {
            fence.reset();
            long e=10+i,t=i*12L;
            int delay=r.nextInt(6);
            fence.yieldCamera(e,t);
            Object candidate=a; Object world=w;
            UUID id=ua;int entityId=7;
            boolean change=r.nextBoolean();
            int switchAt=2+r.nextInt(3);
            long since=-1;
            for(int k=1;k<=8;k++) {
                if(change&&k==switchAt) {
                    switch(i%4) {
                        case 0:candidate=b;break;
                        case 1:world=otherWorld;break;
                        case 2:id=ub;break;
                        default:entityId=8;break;
                    }
                    since=-1;
                }
                boolean manual=(i%11==0&&k==5);
                if(manual) {
                    fence.interrupt();
                    since=-1;interruptions++;
                }
                if(since<0)since=t+k;
                boolean expected=delay==0||t+k-since>=delay;
                boolean allowed=fence.consider(e,t+k,entityId,id,candidate,world,delay);
                ok(allowed==expected,"60k independent handback dwell/original-object oracle "+i+":"+k);
                if(allowed) {
                    fence.applied(e,t+k);
                    ok(!fence.pending(),"successful real camera commit terminates handback "+i);
                    ready++;
                    break;
                }
                delayed++;
            }
        }
        ok(ready>53000&&delayed>90000&&interruptions>1500,
           "60k mixed configured dwell, native switches and manual override exercised");
        ok(assertions>160000,"sufficient exact handback frame checks executed");
    }

    public static void main(String[] args) throws Exception {
        exact();mouseAndTrigger();seeded();
        System.out.println("AIM_CAMERA_RETURN_PASS assertions="+assertions);
    }
}
