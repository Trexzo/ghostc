package dev.ghostfusion.tests;

import dev.ghostfusion.features.DamageMotionHandoff;
import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.VelocityPolicy;
import java.util.Random;

/**
 * Pure R3.77 damage-motion arbitration. This models local native tick
 * admission; it never calls Minecraft or claims remote knockback/ACK.
 */
public final class VelocityJumpHandoffTests {
    private static long assertions;
    private static void ok(boolean value,String why) {
        assertions++;
        if(!value)throw new AssertionError(why);
    }
    private static VelocityPolicy.Adjustment sample(VelocityPolicy v,
            long epoch,long tick,int hurt,double y,
            double horizontal,double vertical) {
        return v.sample(true,true,hurt,0.4,y,-0.3,
            horizontal,vertical,tick,epoch);
    }
    private static void exact() {
        VelocityPolicy velocity=new VelocityPolicy();
        JumpResetPolicy jump=new JumpResetPolicy();
        ok(sample(velocity,1,0,0,0.4,60,50)==null,
            "first velocity frame only seeds damage baseline");
        ok(!jump.preview(1,0,0,true,8,12),"jump initially seeds baseline");
        VelocityPolicy.Adjustment vertical=sample(velocity,1,1,10,0.4,60,50);
        ok(vertical!=null && vertical.y==0.2 &&
            DamageMotionHandoff.verticalChanged(0.4,vertical),
            "hurt-mode changes native positive vertical motion");
        ok(!DamageMotionHandoff.jumpAvailable(true,true),
            "the first native vertical write reserves jump output");
        ok(!jump.preview(1,1,10,
            DamageMotionHandoff.jumpAvailable(true,true),8,12),
            "same damage edge cannot also jump over reduced knockback");
        ok(sample(velocity,1,2,9,0.3,60,50)==null,
            "decaying hurt cannot reapply velocity");
        ok(!jump.preview(1,2,9,true,8,12),
            "suppressed jump cannot replay on later eligible tick");
        ok(sample(velocity,1,3,0,0.2,60,50)==null,"hurt returns idle");
        ok(!jump.preview(1,3,0,true,8,12),"jump also returns idle");
        VelocityPolicy.Adjustment horizontal=sample(
            velocity,1,4,10,0.4,25,100);
        ok(horizontal!=null && horizontal.x==0.1 &&
            !DamageMotionHandoff.verticalChanged(0.4,horizontal),
            "horizontal-only Velocity does not reserve vertical impulse");
        ok(jump.preview(1,4,10,
            DamageMotionHandoff.jumpAvailable(true,false),8,12),
            "horizontal Velocity and Jump Reset can coexist");
        jump.committed(1,4);
        ok(!jump.preview(1,5,9,true,8,12),"jump cooldown still enforced");
        ok(sample(velocity,1,5,9,0.2,30,100)==null,
            "no repeated local horizontal motion after damage edge");
        ok(sample(velocity,1,6,0,0.2,30,100)==null,"reset hurt baseline");
        ok(!jump.preview(1,6,0,true,8,12),"reset jump hurt baseline");
        VelocityPolicy.Adjustment falling=sample(
            velocity,1,7,10,-0.35,25,0);
        ok(falling!=null && falling.y==-0.35 &&
            !DamageMotionHandoff.verticalChanged(-0.35,falling),
            "falling Y is never a reserved upward Velocity write");
        ok(!jump.preview(1,7,10,true,8,12),
            "independent Jump Reset cooldown still applies");
        ok(!DamageMotionHandoff.verticalChanged(0.35,null),
            "packet mode provides no synchronous local vertical proof");
        ok(DamageMotionHandoff.jumpAvailable(true,false) &&
           !DamageMotionHandoff.jumpAvailable(false,false),
            "packet-mode observation and module denial are not conflated");

        ModuleInteractionStateMachine owner=new ModuleInteractionStateMachine();
        ModuleInteractionStateMachine.Enabled enabled=
            new ModuleInteractionStateMachine.Enabled(true,true,true,true,true,
                false,false,false,true);
        ModuleInteractionStateMachine.Frame f=owner.advance(2,0,true,
            ModuleInteractionStateMachine.Scene.GAME,true,enabled,true);
        ok(!f.jumpReset && f.velocity && f.wtap,
            "WTap reservation takes priority before any hurt-mode handoff");
        f=owner.advance(2,1,true,
            ModuleInteractionStateMachine.Scene.GAME,true,enabled,false);
        ok(f.jumpReset && f.velocity && f.wtap,
            "once WTap is released Jump Reset returns to normal arbitration");
        velocity.reset();
        jump.reset();
        ok(sample(velocity,2,0,10,0.4,30,40)==null &&
            !jump.preview(2,0,10,true,8,12),
            "new world with existing hurtTime must not manufacture output");
        ok(!DamageMotionHandoff.jumpAvailable(true,true),
            "partial vertical native write conservatively preempts jump");
    }
    private static void randomized() {
        VelocityPolicy velocity=new VelocityPolicy();
        JumpResetPolicy jump=new JumpResetPolicy();
        Random rng=new Random(3772026L);
        long epoch=-1,lastCommitted=-1;
        int priorHurt=-1,previousMin=-1,previousCooldown=-1;
        for(int i=0;i<80000;i++) {
            long nowEpoch=1+i/941;
            long tick=i%941;
            if(epoch!=nowEpoch) {
                epoch=nowEpoch;
                velocity.reset();
                jump.reset();
                priorHurt=previousMin=previousCooldown=-1;
                lastCommitted=-1;
            }
            int hurt=rng.nextInt(9)==0?10:rng.nextInt(9);
            double y=(rng.nextInt(81)-40)/100.0;
            int horizontal=rng.nextInt(101),vertical=rng.nextInt(101);
            boolean packetMode=rng.nextInt(7)==0;
            VelocityPolicy.Adjustment adjusted;
            if(packetMode) {
                velocity.reset();
                adjusted=null;
            } else {
                adjusted=sample(velocity,epoch,tick,hurt,y,horizontal,vertical);
            }
            boolean verticalOwned=DamageMotionHandoff.verticalChanged(y,adjusted);
            boolean oracle=adjusted!=null && y>0 && vertical<100;
            ok(verticalOwned==oracle,
                "80k local Y mutation ownership oracle "+i);
            // The outer arbiter can deny Jump Reset entirely, including
            // an exclusive WTap reservation or unavailable game scene.
            boolean planGranted=rng.nextInt(13)!=0;
            boolean physicalAllowed=rng.nextInt(8)!=0;
            if(!planGranted) {
                jump.reset();
                priorHurt=previousMin=previousCooldown=-1;
                lastCommitted=-1;
                ok(!DamageMotionHandoff.jumpAvailable(false,verticalOwned),
                    "denied plan can never produce a jump "+i);
                continue;
            }
            boolean admit=DamageMotionHandoff.jumpAvailable(
                physicalAllowed,verticalOwned);
            boolean expected;
            if(previousMin!=8 || previousCooldown!=12) {
                priorHurt=hurt;
                previousMin=8;
                previousCooldown=12;
                expected=false;
            } else if(priorHurt<0) {
                priorHurt=hurt;expected=false;
            } else {
                boolean rising=hurt>priorHurt && hurt>=8;
                priorHurt=hurt;
                expected=admit && rising &&
                    (lastCommitted<0||tick-lastCommitted>=12);
            }
            boolean accepted=jump.preview(epoch,tick,hurt,admit,8,12);
            ok(accepted==expected,
                "80k Jump Reset fresh damage and vertical arbitration "+i);
            if(accepted) {
                if(rng.nextInt(7)==0) jump.cancelPrepared();
                else {
                    jump.committed(epoch,tick);
                    lastCommitted=tick;
                }
            }
            ok(!jump.preview(epoch,tick,hurt,admit,8,12),
                "80k duplicate tick never jumps twice "+i);
        }
        ok(assertions>=160000,"80k seeded damage-motion handoff frames checked");
    }
    public static void main(String[] args) {
        exact();
        randomized();
        System.out.println("VELOCITY_JUMP_HANDOFF_PASS assertions="+assertions);
    }
}
