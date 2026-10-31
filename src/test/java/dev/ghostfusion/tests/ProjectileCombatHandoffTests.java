package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.features.ProjectileCombatHandoff;
import dev.ghostfusion.features.ProjectileOwnerArbiter;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.69 — begin/end projectile ownership, manual camera precedence and
 * fresh post-projectile attack admission. Headless logic, not gameplay.
 */
public final class ProjectileCombatHandoffTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++; if(!b)throw new AssertionError(why);
    }
    private static void exact() throws Exception {
        ProjectileOwnerArbiter.Owner N=ProjectileOwnerArbiter.Owner.NONE;
        ProjectileOwnerArbiter.Owner R=ProjectileOwnerArbiter.Owner.ROD;
        ProjectileOwnerArbiter.Owner S=ProjectileOwnerArbiter.Owner.SNOWBALL;
        ProjectileOwnerArbiter.Owner C=ProjectileOwnerArbiter.Owner.CONFLICT;
        ok(!ProjectileCombatHandoff.reserved(N,false,false),
            "idle hotbar has no hidden cooldown");
        ok(ProjectileCombatHandoff.reserved(N,true,false),
            "new AutoRod select reserves camera this tick");
        ok(ProjectileCombatHandoff.reserved(N,false,true),
            "new AutoSnowball select reserves camera this tick");
        ok(ProjectileCombatHandoff.reserved(R,false,false),
            "AutoRod cancels/finishes mid-tick: no same-frame Aim resume");
        ok(ProjectileCombatHandoff.reserved(S,false,false),
            "AutoSnowball cancels mid-tick: no same-frame TriggerBot replay");
        ok(ProjectileCombatHandoff.reserved(C,false,false),
            "impossible double owner fails closed");
        boolean rejected=false;
        try {ProjectileCombatHandoff.reserved(null,false,false);}
        catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"missing ownership is not silently treated as safe");

        AimManualInputPolicy mouse=new AimManualInputPolicy();
        // The adapter's new suspendForProjectile method samples mouse even
        // while combat outputs are excluded and other combat state resets.
        ok(!mouse.suppressed(1,0,true,0,0,3,4),"idle mouse baseline");
        ok(mouse.suppressed(1,1,true,4,0,3,4),
            "manual mouse movement during AutoRod SELECT starts pause");
        ok(mouse.suppressed(1,2,true,0,0,3,4),
            "pause survives AutoRod USE");
        ok(mouse.suppressed(1,3,true,0,0,3,4),
            "pause survives AutoRod RESTORE");
        ok(mouse.suppressed(1,4,true,0,0,3,4),
            "first actual post-restore frame still respects mouse");
        ok(!mouse.suppressed(1,5,true,0,0,3,4),
            "camera resumes exactly at configured manual deadline");
        ok(mouse.suppressed(1,6,true,-6,0,3,3),
            "AutoSnowball SELECT movement re-arms new pause setting");
        ok(mouse.suppressed(1,7,true,0,0,3,3),
            "AutoSnowball USE preserves manual mouse history");
        ok(mouse.suppressed(1,8,true,0,0,3,3),
            "AutoSnowball RESTORE preserves manual mouse history");
        ok(!mouse.suppressed(1,9,true,0,0,3,3),
            "no extra post-projectile camera delay");
        ok(!mouse.suppressed(1,10,false,20,0,3,3),
            "respect_mouse disabled resets pause and preserves legacy behavior");

        PhysicalInputGate held=new PhysicalInputGate();
        final int attack=-100,use=-99;
        ok(!held.allowed(2,true,true,false,false,attack,use),
            "initial release observed");
        ok(held.allowed(2,true,true,false,true,attack,use),
            "ordinary real hold may be admitted before projectile");
        held.reset(); // combat.resetNonMouse() while projectile is exclusive
        ok(!held.allowed(2,true,true,false,true,attack,use),
            "held click on projectile release cannot inherit a pressed state");
        ok(!held.allowed(2,true,true,false,false,attack,use),
            "fresh physical release arms next click");
        ok(held.allowed(2,true,true,false,true,attack,use),
            "fresh player press, not a buffered click, rearms normally");

        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID identity=new UUID(0x369L,8);
        ok(!trigger.due(2,0,true,identity,2,4),
            "crosshair target begins normal acquisition");
        ok(!trigger.due(2,1,true,identity,2,4),
            "acquisition cannot be shortcut");
        ok(trigger.due(2,2,true,identity,2,4),
            "full valid target dwell is required");
        trigger.dispatched(2,2,identity);
        trigger.reset(); // projectiles reset in-flight TriggerBot acquisition
        ok(!trigger.due(2,6,true,identity,2,4),
            "no saved target readiness after projectile");
        ok(!trigger.due(2,7,true,identity,2,4),
            "crosshair must reacquire after projectile");
        ok(trigger.due(2,8,true,identity,2,4),
            "normal fresh dwell can complete without extra cooldown");

        AtomicBoolean ownerDenied=new AtomicBoolean();
        Thread t=new Thread(()->{
            try {mouse.suppressed(1,11,true,5,0,3,4);}
            catch(IllegalStateException expected){ownerDenied.set(true);}
        });
        t.start();t.join();
        ok(ownerDenied.get(),"foreign Netty thread cannot mutate manual camera history");
        mouse.reset();
        ok(!mouse.suppressed(3,0,true,0,0,3,4),
            "world/panic/focus transition drops manual pause");
    }
    private static void randomized() {
        AimManualInputPolicy mouse=new AimManualInputPolicy();
        Random rng=new Random(3692026L);
        long epoch=10,lastPhysical=-1;
        int priorThreshold=-1,priorPause=-1;
        ProjectileOwnerArbiter.Owner[] owners=ProjectileOwnerArbiter.Owner.values();
        for(int tick=0;tick<90000;tick++) {
            if(tick%997==0) {
                mouse.reset();epoch++;
                lastPhysical=-1;priorThreshold=priorPause=-1;
            }
            boolean nowRod=rng.nextInt(7)==0,nowSnow=rng.nextInt(9)==0;
            ProjectileOwnerArbiter.Owner atStart=owners[rng.nextInt(owners.length)];
            boolean expectedReserved=atStart!=ProjectileOwnerArbiter.Owner.NONE||
                nowRod||nowSnow;
            boolean actualReserved=ProjectileCombatHandoff.reserved(
                atStart,nowRod,nowSnow);
            ok(actualReserved==expectedReserved,
                "90k independent first/last hotbar ownership matrix "+tick);
            boolean enabled=rng.nextInt(13)!=0;
            int deltaX=rng.nextInt(13)-6,deltaY=rng.nextInt(13)-6;
            int threshold=2+(tick/389)%3,pause=2+(tick/461)%4;
            boolean actual=mouse.suppressed(epoch,tick,enabled,
                deltaX,deltaY,threshold,pause);
            if(!enabled) {
                lastPhysical=-1;priorThreshold=priorPause=-1;
            } else {
                if(threshold!=priorThreshold||pause!=priorPause) {
                    lastPhysical=-1;priorThreshold=threshold;priorPause=pause;
                }
                if(Math.abs((long)deltaX)>=threshold||
                   Math.abs((long)deltaY)>=threshold)lastPhysical=tick;
            }
            boolean expected=enabled&&lastPhysical>=0&&tick-lastPhysical<pause;
            ok(actual==expected,"90k independent physical-mouse oracle "+tick);
            boolean plan=rng.nextBoolean(),manual=rng.nextInt(17)==0;
            boolean conflict=atStart==ProjectileOwnerArbiter.Owner.CONFLICT;
            boolean combatCalled=plan&&!actualReserved&&!manual&&!conflict;
            boolean expectedCalled=plan&&atStart==ProjectileOwnerArbiter.Owner.NONE&&
                !nowRod&&!nowSnow&&!manual&&!conflict;
            ok(combatCalled==expectedCalled,
                "90k exclusive projectile/camera admission frames "+tick);
            if(actualReserved)ok(!combatCalled,
                "exclusive slot denies synthetic camera and attack output");
        }
        ok(checks>270000,"large deterministic handoff matrix executed");
    }
    public static void main(String[] args) throws Exception {
        exact();
        randomized();
        System.out.println("PROJECTILE_COMBAT_HANDOFF_PASS assertions="+checks);
    }
}
