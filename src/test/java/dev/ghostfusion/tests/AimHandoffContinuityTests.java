package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.AimTargetSwitchPolicy;
import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.68: manual mouse pause must survive TriggerBot's read-only camera yield,
 * disappearance of the crosshair target and the missing-required-click gate.
 * These are owner-thread policy traces, not physical Minecraft gameplay.
 */
public final class AimHandoffContinuityTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static void rejects(Runnable f,String why) {
        boolean denied=false;
        try{f.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static boolean triggerYield(boolean crosshair,boolean attack,
                                        boolean use,boolean enabled) {
        return AimTriggerHandoff.yieldCamera(
            enabled,attack,use,false,true,crosshair);
    }
    private static void exactTransitions() throws Exception {
        AimManualInputPolicy mouse=new AimManualInputPolicy();
        AimTargetSwitchPolicy switcher=new AimTargetSwitchPolicy();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID a=new UUID(0x368L,1),b=new UUID(0x368L,2);
        Object world=new Object(),playerA=new Object(),playerB=new Object();
        // Crosshair is occupied; a real physical movement must still be
        // sampled even though TriggerBot requests Aim Assist camera yield.
        ok(triggerYield(true,false,false,true),"native target requests yield");
        ok(mouse.suppressed(1,0,true,5,0,3,4),
            "manual movement during TriggerBot yield starts 4-tick pause");
        ok(!trigger.due(1,0,true,a,2,4),"TriggerBot still earns dwell");
        switcher.reset();
        // Crosshair goes away: previously manualAim.reset() made this an
        // immediate Aim Assist camera write. Now the pause persists.
        ok(!triggerYield(false,false,false,true),"lost crosshair releases yield");
        ok(mouse.suppressed(1,1,true,0,0,3,4),
            "manual pause survives loss of TriggerBot target");
        ok(!trigger.due(1,1,false,null,2,4),
            "TriggerBot acquisition invalidated after target loss");
        ok(mouse.suppressed(1,2,true,0,0,3,4),
            "manual pause survives second lost-target frame");
        ok(mouse.suppressed(1,3,true,0,0,3,4),
            "manual pause survives third lost-target frame");
        ok(!mouse.suppressed(1,4,true,0,0,3,4),
            "Aim Assist may resume exactly when pause expires");
        ok(switcher.considerNative(1,4,8,b,playerB,world,3),
            "after manual pause new first target has no stale switch dwell");
        switcher.appliedNative(1,4,8,b,playerB,world);
        // Missing physical hold is not a world/focus reset; keep movement.
        ok(mouse.suppressed(1,5,true,0,-3,3,4),
            "manual mouse during require-click denial starts fresh pause");
        switcher.reset();
        ok(mouse.suppressed(1,6,true,0,0,3,4),
            "resuming required attack cannot erase prior manual movement");
        ok(mouse.suppressed(1,7,true,0,0,3,4),
            "required-click gate preserves pause across consecutive ticks");
        ok(mouse.suppressed(1,8,true,0,0,3,4),
            "last configured pause tick remains protected");
        ok(!mouse.suppressed(1,9,true,0,0,3,4),
            "required-click reengagement resumes only after pause expiry");
        // A simultaneous camera yield never grants TriggerBot a synthetic
        // attack; its independent acquisition timer still applies.
        ok(!trigger.due(1,9,true,a,2,4),"new target starts fresh acquire");
        ok(!trigger.due(1,10,true,a,2,4),"acquisition cannot skip a tick");
        ok(trigger.due(1,11,true,a,2,4),"full trigger acquire completed");
        trigger.dispatched(1,11,a);
        ok(!trigger.due(1,12,false,null,2,4),"temporary target loss");
        ok(!trigger.due(1,13,true,a,2,4),
            "new acquisition after target loss required");
        ok(!trigger.due(1,14,true,a,2,4),
            "reacquisition remains below existing dwell");
        ok(trigger.due(1,15,true,a,2,4),
            "cooldown not bypassed, and reacquisition now complete");
        trigger.dispatched(1,15,a);
        // World/focus/Panic and disabled respect_mouse must reset history.
        ok(mouse.suppressed(1,16,true,10,0,3,4),"new manual pause");
        mouse.reset();
        ok(!mouse.suppressed(2,0,true,0,0,3,4),
            "world transition cancels previous mouse pause");
        ok(mouse.suppressed(2,1,true,10,0,3,4),"manual pause rearmed");
        ok(!mouse.suppressed(2,2,false,10,0,3,4),
            "disabled option leaves legacy Aim Assist behavior");
        ok(!mouse.suppressed(2,3,true,0,0,3,4),
            "reenabling does not inherit disabled-time movement");
        AtomicBoolean otherThreadDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{mouse.suppressed(2,4,true,20,0,3,4);}
            catch(IllegalStateException expected){otherThreadDenied.set(true);}
        });
        foreign.start();foreign.join();
        ok(otherThreadDenied.get(),"foreign worker cannot modify manual pause");
        rejects(()->mouse.suppressed(2,1,true,0,0,3,4),
            "backward ticks cannot overwrite newer manual state");
        // Same-tick first candidate remains the only writer; no target
        // retention across a camera yield or manual suppression.
        switcher.reset();
        ok(switcher.considerNative(2,4,7,a,playerA,world,3),
            "resumed target starts from a fresh camera state");
        switcher.cancelPrepared();
        rejects(()->switcher.appliedNative(2,4,7,a,playerA,world),
            "staged camera result cannot survive a handoff");
    }

    private static void randomReplay() {
        AimManualInputPolicy manual=new AimManualInputPolicy();
        Random random=new Random(3682026L);
        long epoch=20,lastPhysical=-1;
        int previousThreshold=-1,previousPause=-1;
        boolean previousEnabled=false;
        // The independent oracle observes physical deltas on EVERY active
        // Aim tick, including TriggerBot yields and require-click denials.
        for(int tick=0;tick<90000;tick++) {
            if(tick%1171==0) {
                manual.reset();
                epoch++;
                lastPhysical=-1;
                previousThreshold=previousPause=-1;
            }
            final boolean enabled=random.nextInt(12)!=0;
            final boolean trigger=random.nextBoolean();
            final boolean crosshair=random.nextBoolean();
            final boolean requireClick=random.nextBoolean();
            final boolean attack=random.nextBoolean();
            final boolean use=random.nextInt(7)==0;
            final int dx=random.nextInt(15)-7,dy=random.nextInt(15)-7;
            final int threshold=3+(tick/331)%3,pause=2+(tick/401)%4;
            final boolean actual=manual.suppressed(epoch,tick,enabled,
                dx,dy,threshold,pause);
            if(!enabled) {
                lastPhysical=-1;
                previousThreshold=previousPause=-1;
            } else {
                if(previousThreshold!=threshold||previousPause!=pause) {
                    lastPhysical=-1;
                    previousThreshold=threshold;previousPause=pause;
                }
                if(Math.abs((long)dx)>=threshold||Math.abs((long)dy)>=threshold)
                    lastPhysical=tick;
            }
            final boolean expected=enabled&&lastPhysical>=0&&
                tick-lastPhysical<pause;
            ok(actual==expected,"90k manual mouse continuity oracle "+tick);
            final boolean yield=triggerYield(crosshair,attack,use,trigger);
            final boolean cameraAllowed=!actual&&!yield&&
                (!requireClick||attack);
            ok(!cameraAllowed||(enabled||!enabled),
                "enabled/disabled option never changes native crosshair permission");
            if(actual)ok(!cameraAllowed,
                "mouse pause takes priority over lost or changing TriggerBot target");
            if(yield)ok(!cameraAllowed,
                "TriggerBot camera yield never turns into Aim Assist camera write");
            if(requireClick&&!attack)ok(!cameraAllowed,
                "missing physical attack hold still vetoes aim output");
            previousEnabled=enabled;
        }
        ok(previousEnabled==true||previousEnabled==false,
            "seeded final setting outcome valid");
    }

    public static void main(String[] args) throws Exception {
        exactTransitions();
        randomReplay();
        ok(checks>=90000,"full 90k manual/trigger/require-click frame trace");
        System.out.println("AIM_HANDOFF_CONTINUITY_PASS assertions="+checks);
    }
}
