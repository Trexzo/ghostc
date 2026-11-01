package dev.ghostfusion.tests;

import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Denial;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Resource;
import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.features.WTapPolicy.Decision;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.78: a WTap denial consumes the current hurt edge but retains a previously
 * committed Jump Reset cooldown. Independent seeded tick oracle. No game
 * launch, packet, movement writer, or server hit is simulated.
 */
public final class WTapJumpContinuityTests {
    private static long assertions;
    private static void ok(boolean actual,String why) {
        assertions++;
        if(!actual)throw new AssertionError(why);
    }
    private static final ModuleInteractionStateMachine.Enabled ENABLED=
        new ModuleInteractionStateMachine.Enabled(true,true,true,true,true,
            false,false,false,true);

    private static void exact() throws Exception {
        JumpResetPolicy jump=new JumpResetPolicy();
        WTapPolicy wtap=new WTapPolicy();
        ModuleInteractionStateMachine arbiter=new ModuleInteractionStateMachine();
        ok(!jump.preview(1,0,0,true,8,12),"new epoch seeds baseline");
        ok(jump.preview(1,1,10,true,8,12),"genuine damage edge arms jump");
        jump.committed(1,1);
        ok(wtap.attack(true,true,true,2,2,1),
            "native attack starts sprint reservation following prior jump");
        for(int tick=2;tick<=5;tick++) {
            final boolean pending=wtap.pending();
            ModuleInteractionStateMachine.Frame frame=arbiter.advance(
                1,tick,true,ModuleInteractionStateMachine.Scene.GAME,
                true,ENABLED,pending);
            ok(!frame.jumpReset && frame.wtap &&
               frame.denial(Resource.JUMP_RESET)==Denial.WTAP_RESERVED,
                "WTap owns each original reserved native frame "+tick);
            final int hurt=(tick==2||tick==4)?9:(tick==3?10:0);
            ok(!jump.preview(1,tick,hurt,false,8,12),
                "blocked hurt sample cannot jump during reservation "+tick);
            Decision decision=wtap.tick(true,true,tick,1);
            ok(decision==(tick==3?Decision.RESET:
                 tick==4?Decision.HOLD:
                 tick==5?Decision.RESTORE:Decision.NONE),
                "WTap native timing unchanged "+tick);
        }
        ok(!wtap.pending(),"reservation releases only after restore");
        ModuleInteractionStateMachine.Frame reopened=arbiter.advance(1,6,true,
            ModuleInteractionStateMachine.Scene.GAME,true,ENABLED,wtap.pending());
        ok(reopened.jumpReset && reopened.denial(Resource.JUMP_RESET)==Denial.NONE,
            "Jump Reset regains normal native grant after WTap release");
        ok(!jump.preview(1,6,10,true,8,12),
            "original committed jump cooldown survives WTap suppression");
        ok(!jump.preview(1,7,9,true,8,12),"no delayed jump after denied event");
        ok(!jump.preview(1,8,0,true,8,12),"return to idle");
        ok(!jump.preview(1,9,10,true,8,12),
            "fresh damage remains denied during preserved cooldown");
        ok(!jump.preview(1,10,9,true,8,12),"denied damage never replays");
        ok(!jump.preview(1,11,0,true,8,12),"reset hurt baseline");
        ok(!jump.preview(1,12,0,true,8,12),"no synthetic edge at cooldown end");
        ok(jump.preview(1,13,10,true,8,12),
            "new real damage exactly at cooldown boundary can jump");
        jump.committed(1,13);
        ok(!jump.preview(1,13,10,true,8,12),"same-tick duplicate cannot jump");
        ok(wtap.attack(true,true,true,1,14,1),
            "new native attack can reserve after prior restoration");
        ok(!jump.preview(1,14,9,false,8,12),
            "new WTap window records declining hurt");
        ok(!jump.preview(1,15,10,false,8,12),
            "rising hurt inside WTap hold consumed without jump");
        ok(!jump.preview(1,16,9,true,8,12),
            "hurt blocked by WTap cannot jump once ownership released");

        // Different denial reasons must remain hard-reset boundaries.
        ModuleInteractionStateMachine.Frame gui=arbiter.advance(1,16,true,
            ModuleInteractionStateMachine.Scene.OTHER_GUI,true,ENABLED,false);
        ok(gui.denial(Resource.JUMP_RESET)==Denial.WRONG_SCREEN,
            "GUI denial is not misclassified as WTap reservation");
        jump.reset();
        ok(!jump.preview(1,17,10,true,8,12),
            "GUI/focus hard reset re-seeds damaged player without jumping");
        ok(!jump.preview(2,0,10,true,8,12),
            "new native world/epoch never reuses old hurt or cooldown");
        ok(!jump.preview(2,1,0,true,8,12),"new epoch returns to idle");
        ok(jump.preview(2,2,10,true,8,12),
            "new epoch permits only a fresh damage edge");
        jump.cancelPrepared();
        AtomicBoolean rejected=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{jump.preview(2,3,9,false,8,12);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(rejected.get(),"Netty/foreign thread cannot observe Jump Reset state");
    }

    private static final class Oracle {
        int previousHurt=-1;
        long lastCommitted=-1;
        boolean initialized;
        void reset() {
            previousHurt=-1;lastCommitted=-1;initialized=false;
        }
        boolean sample(long tick,int hurt,boolean admitted) {
            if(!initialized) {
                initialized=true;previousHurt=hurt;return false;
            }
            boolean rising=hurt>previousHurt && hurt>=8;
            previousHurt=hurt;
            return admitted && rising &&
                (lastCommitted<0 || tick-lastCommitted>=12);
        }
        void commit(long tick) {lastCommitted=tick;}
    }

    private static void randomized() {
        Random rng=new Random(3782026L);
        JumpResetPolicy jump=new JumpResetPolicy();
        WTapPolicy wtap=new WTapPolicy();
        ModuleInteractionStateMachine arbiter=new ModuleInteractionStateMachine();
        Oracle oracle=new Oracle();
        long lastEpoch=-1;
        for(int i=0;i<90000;i++) {
            long epoch=1+i/947;
            long tick=i%947;
            if(lastEpoch!=epoch) {
                lastEpoch=epoch;
                jump.reset();
                wtap.reset();
                oracle.reset();
            }
            if(rng.nextInt(11)==0)
                wtap.attack(true,true,true,1+rng.nextInt(3),tick,epoch);
            final boolean reservedAtStart=wtap.pending();
            boolean windowActive=rng.nextInt(23)!=0;
            ModuleInteractionStateMachine.Scene scene=rng.nextInt(29)==0
                ? ModuleInteractionStateMachine.Scene.OTHER_GUI
                : ModuleInteractionStateMachine.Scene.GAME;
            ModuleInteractionStateMachine.Frame frame=arbiter.advance(
                epoch,tick,true,scene,windowActive,ENABLED,reservedAtStart);
            Denial denial=frame.denial(Resource.JUMP_RESET);
            ok((denial==Denial.WTAP_RESERVED)==
                (reservedAtStart && windowActive &&
                 scene==ModuleInteractionStateMachine.Scene.GAME),
                "90k priority denial corresponds to real reserved WTap "+i);
            if(denial==Denial.WTAP_RESERVED) {
                ok(!frame.jumpReset,"90k WTap never grants native jump "+i);
            }
            int hurt=rng.nextInt(6)==0 ? 10 : rng.nextInt(9);
            boolean verticalOwned=rng.nextInt(11)==0;
            boolean manualPermit=rng.nextInt(7)!=0;
            if(frame.jumpReset || denial==Denial.WTAP_RESERVED) {
                boolean admitted=frame.jumpReset &&
                    !verticalOwned && manualPermit;
                boolean expected=oracle.sample(tick,hurt,admitted);
                boolean got=jump.preview(epoch,tick,hurt,admitted,8,12);
                ok(got==expected,
                    "90k independent cooldown/hurt-continuity oracle "+i);
                if(got) {
                    // A genuine native veto never enters cooldown.
                    if(rng.nextInt(8)==0)jump.cancelPrepared();
                    else {
                        jump.committed(epoch,tick);
                        oracle.commit(tick);
                    }
                }
                ok(!jump.preview(epoch,tick,hurt,admitted,8,12),
                    "90k duplicate client-tick callback is read-only "+i);
            } else {
                jump.reset();
                oracle.reset();
                ok(!frame.jumpReset,
                    "GUI/focus denial never delegates a native jump "+i);
            }
            if(frame.wtap) {
                wtap.tick(true,rng.nextInt(17)!=0,tick,epoch);
            } else wtap.reset();
        }
        ok(assertions>180000,"90k randomized WTap/Jump Reset frames executed");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("WTAP_JUMP_CONTINUITY_PASS assertions="+assertions);
    }
}
