package dev.ghostfusion.tests;

import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.NativeAttackFrameGate;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;

/**
 * R3.81 final native output boundary: a Hit Select-required crosshair must
 * still contain the same registered target even when Clicker entity-only is
 * disabled; TriggerBot's sword requirement inspects the OUTPUT held item.
 *
 * All test samples are headless read-only models. No actual Minecraft
 * KeyBinding, attack, packet, or server-side hit is generated.
 */
public final class NativeAttackOutputRevalidationTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    private static void exact() {
        Object player=new Object(),enemy=new Object(),other=new Object();
        ok(!NativeAttackFrameGate.requireExactClickTarget(false,false,true),
            "disabled Hit Select cannot silently enable entity-only Clicker");
        ok(!NativeAttackFrameGate.requireExactClickTarget(false,true,false),
            "Hit Select without require target retains unrestricted Clicker");
        ok(NativeAttackFrameGate.requireExactClickTarget(true,false,false),
            "Clicker entity-only retains its own native crosshair fence");
        ok(NativeAttackFrameGate.requireExactClickTarget(false,true,true),
            "Hit Select require target adds final native crosshair fence");
        ok(NativeAttackFrameGate.target(enemy,enemy,enemy,player,false,20),
            "unchanged original entity and live registry remain valid");
        ok(!NativeAttackFrameGate.target(enemy,other,enemy,player,false,20),
            "new crosshair object invalidates Hit Select attack");
        ok(!NativeAttackFrameGate.target(enemy,enemy,other,player,false,20),
            "reused entity ID cannot authorize old target");
        ok(!NativeAttackFrameGate.target(enemy,enemy,enemy,player,true,20),
            "dead target vetoes native Hit Select attack");
        ok(!NativeAttackFrameGate.target(enemy,enemy,enemy,player,false,0),
            "zero-health target vetoes native Hit Select attack");

        // A rejected native target never commits Hit Select cooldown.
        HitSelectPolicy hit=new HitSelectPolicy();
        ok(hit.preview(true,true,0,2,10,0,"normal",1,0,1),
            "valid tick-start target stages shared attack intent");
        boolean output=(!NativeAttackFrameGate.requireExactClickTarget(
            false,true,true) ||
            NativeAttackFrameGate.target(enemy,other,enemy,player,false,20));
        ok(!output,"new target at OUTPUT rejects staged Clicker intent");
        hit.cancelPrepared();
        boolean refused=false;
        try {hit.committed(0,1);}
        catch(IllegalStateException expected){refused=true;}
        ok(refused,"Hit Select cannot commit a rejected native pulse");
        ok(hit.preview(true,true,0,2,10,0,"normal",1,1,1),
            "next genuine tick may admit freshly verified target");
        hit.committed(1,1);
        ok(!hit.preview(true,true,0,2,10,0,"normal",1,2,1),
            "real committed pulse enforces the configured shared gap");

        ok(NativeAttackFrameGate.triggerSwordAtOutput(true,true),
            "TriggerBot accepts a currently held native sword");
        ok(!NativeAttackFrameGate.triggerSwordAtOutput(true,false),
            "removing sword after acquisition revokes TriggerBot output");
        ok(NativeAttackFrameGate.triggerSwordAtOutput(false,false),
            "disabling optional sword requirement preserves legacy behavior");
        ok(!NativeAttackFrameGate.attackState(true,false,true,false,false),
            "new physical attack hold takes precedence over TriggerBot");
        ok(!NativeAttackFrameGate.attackState(false,true,false,false,false),
            "manual attack released after Clicker scheduling denies output");
        ok(NativeAttackFrameGate.attackState(false,true,true,false,false),
            "stable manual attack may use original Clicker pacing");

        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID id=new UUID(381L,1);
        ok(!trigger.due(2,0,true,id,2,8),"native TriggerBot starts acquiring");
        ok(!trigger.due(2,1,true,id,2,8),"native TriggerBot still acquiring");
        ok(trigger.due(2,2,true,id,2,8),"full dwell stages one attack");
        ok(!NativeAttackFrameGate.triggerSwordAtOutput(true,false),
            "sword removed before output denies staged TriggerBot");
        trigger.invalidateCandidate();
        refused=false;
        try {trigger.dispatched(2,2,id);}
        catch(IllegalStateException expected){refused=true;}
        ok(refused,"removed sword cannot consume completed native receipt");
        ok(!trigger.due(2,3,true,id,2,8),
            "after sword restoration TriggerBot must reacquire target");
    }

    private static void randomized() {
        Random rng=new Random(3812026L);
        Object player=new Object(),enemy=new Object(),replacement=new Object();
        int deniedByTarget=0,deniedBySword=0,manualVeto=0,approved=0;
        for(int i=0;i<90000;i++) {
            boolean clickEntityOnly=rng.nextBoolean();
            boolean hitSelectEnabled=rng.nextBoolean();
            boolean hitNeedsTarget=rng.nextBoolean();
            boolean sameCrosshair=rng.nextBoolean();
            boolean sameRegistry=rng.nextBoolean();
            boolean dead=rng.nextInt(17)==0;
            double health=rng.nextInt(19)==0?0:20;
            boolean expectedRequirement=clickEntityOnly ||
                (hitSelectEnabled&&hitNeedsTarget);
            boolean actualRequirement=
                NativeAttackFrameGate.requireExactClickTarget(
                    clickEntityOnly,hitSelectEnabled,hitNeedsTarget);
            ok(actualRequirement==expectedRequirement,
                "90k independent native Clicker target requirement "+i);
            boolean expectedTarget=sameCrosshair&&sameRegistry&&!dead&&health>0;
            boolean actualTarget=NativeAttackFrameGate.target(enemy,
                sameCrosshair?enemy:replacement,
                sameRegistry?enemy:replacement,
                player,dead,health);
            ok(actualTarget==expectedTarget,
                "90k independently verified world target identity "+i);
            boolean expectedClick=!expectedRequirement || expectedTarget;
            boolean actualClick=!actualRequirement||actualTarget;
            ok(actualClick==expectedClick,
                "90k Clicker final target admission matrix "+i);
            if(!actualClick)deniedByTarget++;

            boolean swordAtSample=rng.nextBoolean();
            boolean swordAtOutput=rng.nextBoolean();
            boolean requireSword=rng.nextBoolean();
            boolean expectedSword=!requireSword||swordAtOutput;
            boolean actualSword=NativeAttackFrameGate.triggerSwordAtOutput(
                requireSword,swordAtOutput);
            ok(actualSword==expectedSword,
                "90k current native sword, not old sword, oracle "+i);
            if(swordAtSample&&!swordAtOutput&&requireSword)deniedBySword++;
            boolean manualAtSample=rng.nextBoolean();
            boolean manualAtOutput=rng.nextBoolean();
            boolean useNow=rng.nextBoolean();
            boolean usingNow=rng.nextBoolean();
            boolean expectedTrigger=!manualAtSample&&!manualAtOutput &&
                !useNow&&!usingNow&&expectedSword;
            boolean actualTrigger=NativeAttackFrameGate.attackState(
                true,manualAtSample,manualAtOutput,useNow,usingNow) &&
                actualSword;
            ok(actualTrigger==expectedTrigger,
                "90k TriggerBot native manual/sword output veto "+i);
            if(manualAtOutput&&!manualAtSample)manualVeto++;
            boolean expectedHeld=manualAtSample&&manualAtOutput &&
                !useNow&&!usingNow;
            ok(NativeAttackFrameGate.attackState(false,manualAtSample,
                manualAtOutput,useNow,usingNow)==expectedHeld,
                "90k Clicker native physical attack continuity "+i);
            if(expectedClick&&expectedHeld)approved++;
        }
        ok(deniedByTarget>10000&&deniedBySword>5000&&manualVeto>10000&&
           approved>2000,"all native target/sword/manual change classes exercised");
        ok(checks>540000,"90k combined final attack boundary frames validated");
    }
    public static void main(String[] args) {
        exact();randomized();
        System.out.println("NATIVE_ATTACK_OUTPUT_REVALIDATION_PASS assertions="+checks);
    }
}
