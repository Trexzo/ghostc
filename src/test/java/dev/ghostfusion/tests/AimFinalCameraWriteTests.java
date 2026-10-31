package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimFinalCameraWriteGate;
import dev.ghostfusion.features.AimFinalCameraWriteGate.SourcePose;
import dev.ghostfusion.features.AimFinalCameraWriteGate.TargetState;
import dev.ghostfusion.features.AimFinalCameraWriteGate.Inputs;
import dev.ghostfusion.features.AimFinalCameraWriteGate.Ticket;
import dev.ghostfusion.services.RotationCoordinator;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic last-moment native camera admission, not launched gameplay. */
public final class AimFinalCameraWriteTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    private static void reject(Runnable task,String why) {
        boolean threw=false;
        try{task.run();}catch(RuntimeException expected){threw=true;}
        ok(threw,why);
    }
    private static SourcePose src(double x,float yaw) {
        return new SourcePose(x,2,3,yaw,5,1.62);
    }
    private static TargetState target(double x,double health,boolean dead) {
        return new TargetState(x,2,6,health,dead,false);
    }
    private static Inputs input(boolean enabled,boolean connected,boolean focused,
                                boolean screenClear,boolean requireClick,
                                boolean respectMouse,boolean attackDown,
                                boolean useDown,boolean usingItem,boolean mouse,
                                int dx,int dy) {
        return new Inputs(enabled,connected,focused,screenClear,requireClick,
                          respectMouse,attackDown,useDown,usingItem,mouse,dx,dy);
    }
    private static Inputs valid() {
        return input(true,true,true,true,false,true,true,false,false,true,0,0);
    }
    private static boolean check(AimFinalCameraWriteGate gate,Ticket ticket,
                                 long epoch,long tick,Object world,Object player,
                                 Object snapshot,Object target,int id,UUID uuid,
                                 SourcePose source,TargetState state,Inputs inputs) {
        return gate.permits(ticket,epoch,tick,world,player,snapshot,
                            target,id,uuid,source,state,inputs);
    }
    private static void explicit() throws Exception {
        AimFinalCameraWriteGate gate=new AimFinalCameraWriteGate();
        Object world=new Object(),local=new Object(),snapshot=new Object(),enemy=new Object();
        Object other=new Object();
        UUID uuid=new UUID(370,1),changedUuid=new UUID(370,2);
        SourcePose pose=src(1,30f);
        TargetState state=target(9,20,false);
        Inputs inputs=valid();
        Ticket ticket=gate.capture(7,20,world,local,snapshot,enemy,14,uuid,
                                   pose,state,inputs);
        ok(check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                 pose,state,inputs),"unchanged original owner and live state passes");
        ok(!check(gate,ticket,7,21,world,local,snapshot,enemy,14,uuid,
                  pose,state,inputs),"later END tick cannot replay decision");
        ok(!check(gate,ticket,8,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,inputs),"world generation change rejects ticket");
        ok(!check(gate,ticket,7,20,other,local,snapshot,enemy,14,uuid,
                  pose,state,inputs),"new world with same entity data denied");
        ok(!check(gate,ticket,7,20,world,other,snapshot,enemy,14,uuid,
                  pose,state,inputs),"new local player denied");
        ok(!check(gate,ticket,7,20,world,local,other,enemy,14,uuid,
                  pose,state,inputs),"replaced immutable snapshot denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,other,14,uuid,
                  pose,state,inputs),"recreated target with identical UUID/ID denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,15,uuid,
                  pose,state,inputs),"native ID change denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,changedUuid,
                  pose,state,inputs),"native UUID change denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  src(1,31f),state,inputs),"manual camera turn after arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  src(2,30f),state,inputs),"local player movement after arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,target(10,20,false),inputs),"target moved after arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,target(9,19,false),inputs),"target health changed after arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,target(9,20,true),inputs),"native dead target denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,true,true,false,false,true,4,0)),
           "fresh physical mouse movement after earlier Aim mouse gate denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,true,false,false,false,true,0,0)),
           "physical attack release between arbitration and native write denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,true,true,true,false,true,0,0)),
           "right-click use edge after camera arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,true,true,false,true,true,0,0)),
           "using-item state changes after arbitration denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,false,true,false,true,true,false,false,true,0,0)),
           "focus loss before native write denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,false,false,true,true,false,false,true,0,0)),
           "GUI opened between arbitration and native write denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(false,true,true,true,false,true,true,false,false,true,0,0)),
           "Aim Assist disable between decision and native write denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,false,true,true,false,true,true,false,false,true,0,0)),
           "disconnect before native camera write denied");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,true,true,true,false,false,true,0,0)),
           "live require-click settings change denies prior ticket");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,false,true,false,false,true,0,0)),
           "live respect-mouse toggle denies prior ticket");
        ok(!check(gate,ticket,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,input(true,true,true,true,false,true,true,false,false,false,0,0)),
           "native mouse helper disappearance denies opt-in mouse precedence");

        Inputs unchangedWithoutMouse=input(true,true,true,true,false,false,
                                           true,false,false,false,0,0);
        Ticket legacy=gate.capture(7,20,world,local,snapshot,enemy,14,uuid,
                                   pose,state,unchangedWithoutMouse);
        ok(check(gate,legacy,7,20,world,local,snapshot,enemy,14,uuid,
                 pose,state,unchangedWithoutMouse),
           "default mouse-respect OFF accepts absent helper");
        ok(check(gate,legacy,7,20,world,local,snapshot,enemy,14,uuid,
                 pose,state,input(true,true,true,true,false,false,true,
                                  false,false,false,22,30)),
           "mouse deltas are ignored when respect-mouse remains explicitly OFF");
        Inputs released=input(true,true,true,true,true,false,false,false,false,false,0,0);
        Ticket noAttack=gate.capture(7,20,world,local,snapshot,enemy,14,uuid,
                                    pose,state,released);
        ok(!check(gate,noAttack,7,20,world,local,snapshot,enemy,14,uuid,
                  pose,state,released),
           "require-click ON never permits an unheld physical attack");
        reject(()->gate.capture(7,20,world,local,snapshot,local,14,uuid,
                               pose,state,inputs),
               "local player cannot be own target");
        reject(()->gate.capture(7,20,world,local,snapshot,enemy,-1,uuid,
                               pose,state,inputs),
               "negative entity ID rejected");
        reject(()->new SourcePose(Double.NaN,2,3,30,5,1.62),
               "nonfinite source pose rejected");
        reject(()->new TargetState(1,2,3,Double.POSITIVE_INFINITY,false,false),
               "nonfinite target health rejected");
        AtomicBoolean rejected=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{gate.permits(ticket,7,20,world,local,snapshot,enemy,14,
                             uuid,pose,state,inputs);}
            catch(IllegalStateException expected){rejected.set(true);}
        });foreign.start();foreign.join();
        ok(rejected.get(),"foreign thread cannot reuse native camera ticket");
    }
    private static void coordinatorDenial() {
        AimFinalCameraWriteGate gate=new AimFinalCameraWriteGate();
        Object world=new Object(),player=new Object(),snapshot=new Object(),target=new Object();
        UUID uuid=new UUID(370,3);
        SourcePose pose=src(0,0f);
        TargetState state=target(7,20,false);
        Inputs input=valid();
        Ticket ticket=gate.capture(11,8,world,player,snapshot,target,5,uuid,
                                   pose,state,input);
        RotationCoordinator rotations=new RotationCoordinator();
        rotations.request("combat.aimassist",RotationCoordinator.Scope.CAMERA,
                          12.5f,5f,10,8,1,11);
        RotationCoordinator.Decision decision=rotations.decide(
            RotationCoordinator.Scope.CAMERA,8,11,0,0);
        ok("combat.aimassist".equals(decision.owner),
           "real rotation coordinator granted original Aim camera intent");
        // Another local camera owner changes the native yaw after the
        // coordinator chose the intent: the final gate cancels without write.
        ok(!check(gate,ticket,11,8,world,player,snapshot,target,5,uuid,
                  src(0,2f),state,input),
           "winning coordinator request does not override manual camera change");
        rotations.cancel("combat.aimassist");
        ok(rotations.pending()==0,
           "denied Aim camera intent is removed rather than replayed next tick");
    }
    private static void seeded() {
        AimFinalCameraWriteGate gate=new AimFinalCameraWriteGate();
        Random rng=new Random(370189L);
        Object world=new Object(),otherWorld=new Object(),player=new Object(),
               otherPlayer=new Object(),snapshot=new Object(),otherSnapshot=new Object(),
               target=new Object(),otherTarget=new Object();
        UUID uuid=new UUID(370,189),otherUuid=new UUID(370,190);
        long granted=0,denied=0;
        for(int i=0;i<80000;i++) {
            long epoch=i+1,tick=10+i;
            double x=rng.nextDouble()*100;
            float yaw=rng.nextFloat()*180f-90f;
            SourcePose source=src(x,yaw),nowSource=source;
            TargetState state=target(x+4,20,false),nowTarget=state;
            Inputs initial=valid(),nowInput=initial;
            Object currentWorld=world,currentPlayer=player,
                   currentSnapshot=snapshot,currentTarget=target;
            long currentEpoch=epoch,currentTick=tick;
            int id=7;UUID currentUuid=uuid;
            int mode=i%24;
            switch(mode) {
                case 1:currentWorld=otherWorld;break;
                case 2:currentPlayer=otherPlayer;break;
                case 3:currentSnapshot=otherSnapshot;break;
                case 4:currentTarget=otherTarget;break;
                case 5:currentEpoch++;break;
                case 6:currentTick++;break;
                case 7:id=8;break;
                case 8:currentUuid=otherUuid;break;
                case 9:nowSource=src(x,yaw+1f);break;
                case 10:nowSource=src(x+1,yaw);break;
                case 11:nowTarget=target(x+5,20,false);break;
                case 12:nowTarget=target(x+4,19,false);break;
                case 13:nowTarget=target(x+4,20,true);break;
                case 14:nowInput=input(true,true,true,true,false,true,true,
                                       false,false,true,3,0);break;
                case 15:nowInput=input(true,true,true,true,false,true,false,
                                       false,false,true,0,0);break;
                case 16:nowInput=input(true,true,true,true,false,true,true,
                                       true,false,true,0,0);break;
                case 17:nowInput=input(true,true,true,false,false,true,true,
                                       false,false,true,0,0);break;
                case 18:nowInput=input(true,true,false,true,false,true,true,
                                       false,false,true,0,0);break;
                case 19:nowInput=input(false,true,true,true,false,true,true,
                                       false,false,true,0,0);break;
                case 20:nowInput=input(true,false,true,true,false,true,true,
                                       false,false,true,0,0);break;
                case 21:nowInput=input(true,true,true,true,true,true,true,
                                       false,false,true,0,0);break;
                case 22:nowInput=input(true,true,true,true,false,false,true,
                                       false,false,true,0,0);break;
                case 23:nowInput=input(true,true,true,true,false,true,true,
                                       false,false,false,0,0);break;
            }
            Ticket ticket=gate.capture(epoch,tick,world,player,snapshot,target,
                                       7,uuid,source,state,initial);
            boolean outcome=gate.permits(ticket,currentEpoch,currentTick,
                currentWorld,currentPlayer,currentSnapshot,currentTarget,
                id,currentUuid,nowSource,nowTarget,nowInput);
            ok(outcome==(mode==0),
               "80k independent session/pose/target/input pre-write oracle "+i);
            if(outcome)granted++;else denied++;
        }
        ok(granted>3000&&denied>75000&&checks>80000,
           "all veto classes and approved same-frame writes covered");
    }
    public static void main(String[] args) throws Exception {
        explicit();coordinatorDenial();seeded();
        System.out.println("AIM_FINAL_CAMERA_WRITE_PASS assertions="+checks);
    }
}
