package dev.ghostfusion.tests;

import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.features.WTapPolicy.Decision;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.76: real WTap policy and module arbiter. No synthetic Minecraft input,
 * game launch or server-side attack acceptance is inferred from these tests.
 */
public final class WTapNativeCadenceTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    private static void exact() throws Exception {
        WTapPolicy p=new WTapPolicy();
        ok(p.tick(true,true,0,1)==Decision.NONE,"seed tick is passive");
        ok(p.attack(true,true,true,2,0,1),"only a genuine sprinting attack arms");
        ok(p.tick(true,true,0,1)==Decision.NONE,
            "attack callback cannot retroactively force sprint off same tick");
        ok(p.tick(true,true,1,1)==Decision.RESET,"next tick resets sprint");
        ok(p.tick(true,true,1,1)==Decision.NONE,
            "duplicate next-tick callback cannot write sprint off twice");
        ok(p.pending(),"duplicate reset did not lose sprint ownership");
        ok(p.tick(true,true,2,1)==Decision.HOLD,"second native hold tick");
        ok(p.tick(true,true,2,1)==Decision.NONE,
            "duplicate hold tick cannot override another native sprint write");
        ok(p.tick(true,true,3,1)==Decision.RESTORE,"restore on original cadence");
        ok(p.tick(true,true,3,1)==Decision.NONE,
            "one successful native sprint restore per tick");
        ok(!p.pending(),"AutoSprint may reacquire after restoration");
        ok(p.attack(true,true,true,3,4,1),"later event may arm again");
        ok(p.tick(true,true,5,1)==Decision.RESET,"next native reset");
        ok(p.tick(true,false,5,1)==Decision.NONE,
            "invalid movement cancels even after sprint write earlier same tick");
        ok(!p.pending(),"manual movement change releases WTap reservation");
        ok(p.tick(true,true,5,1)==Decision.NONE,
            "same-tick eligibility recovery cannot resume old reservation");
        ok(p.tick(true,true,6,1)==Decision.NONE,
            "future tick cannot restore invalidated sprint");
        ok(p.attack(true,true,true,1,7,1),"fresh attack can rearm");
        ok(p.tick(false,true,8,1)==Decision.NONE,"disabled module drops reset");
        ok(p.tick(true,true,8,1)==Decision.NONE,
            "same-tick module reenable cannot reconstruct canceled output");
        ok(!p.pending(),"no delayed sprint after disabling WTap");
        ok(p.attack(true,true,true,1,9,1),"new attack after reenable");
        ok(p.tick(true,true,0,2)==Decision.NONE,
            "world generation transition drops pending sprint");
        ok(!p.pending(),"world swap cannot inherit sprint ownership");

        ModuleInteractionStateMachine state=new ModuleInteractionStateMachine();
        ModuleInteractionStateMachine.Enabled features=
            new ModuleInteractionStateMachine.Enabled(true,true,true,true,true,
                false,false,false);
        p.reset();
        ok(p.attack(true,true,true,1,0,3),"new session attack arms WTap");
        ModuleInteractionStateMachine.Frame frame=state.advance(3,0,true,
            ModuleInteractionStateMachine.Scene.GAME,true,features,p.pending());
        ok(frame.wtap && !frame.autoSprint &&
           frame.sprintOwner==ModuleInteractionStateMachine.SprintOwner.WTAP,
            "WTap reservation preempts AutoSprint on genuinely pending attack");
        ok(p.tick(true,true,1,3)==Decision.RESET,"native sprint reset approved");
        frame=state.advance(3,1,true,ModuleInteractionStateMachine.Scene.GAME,
            true,features,p.pending());
        ok(!frame.autoSprint,"AutoSprint remains denied across reset window");
        ok(p.tick(true,true,2,3)==Decision.RESTORE,"native sprint restore approved");
        frame=state.advance(3,2,true,ModuleInteractionStateMachine.Scene.GAME,
            true,features,p.pending());
        ok(frame.autoSprint &&
           frame.sprintOwner==ModuleInteractionStateMachine.SprintOwner.VANILLA_OR_AUTO_SPRINT,
            "AutoSprint resumes only after WTap terminates its reservation");
        AtomicBoolean foreignDenied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{p.tick(true,true,3,3);}
            catch(IllegalStateException expected){foreignDenied.set(true);}
        });foreign.start();foreign.join();
        ok(foreignDenied.get(),"foreign network thread cannot mutate WTap state");
    }
    private static void randomized() {
        WTapPolicy policy=new WTapPolicy();
        Random r=new Random(3762026L);
        long generation=-1,resetAt=-1,restoreAt=-1,lastAttack=-1;
        for(int i=0;i<80000;i++) {
            long epoch=1+i/991,tick=i%991;
            if(generation!=epoch) {
                generation=epoch;
                resetAt=restoreAt=lastAttack=-1;
            }
            boolean active=r.nextInt(12)!=0;
            boolean eligible=r.nextInt(10)!=0;
            Decision expected;
            if(!active||!eligible) {
                resetAt=restoreAt=-1;expected=Decision.NONE;
            } else if(resetAt>=0) {
                if(tick<resetAt) expected=Decision.NONE;
                else if(tick>resetAt) {
                    resetAt=restoreAt=-1;expected=Decision.NONE;
                } else {
                    resetAt=-1;expected=Decision.RESET;
                }
            } else if(restoreAt<0) expected=Decision.NONE;
            else if(tick<restoreAt) expected=Decision.HOLD;
            else {
                restoreAt=-1;expected=Decision.RESTORE;
            }
            Decision got=policy.tick(active,eligible,tick,epoch);
            ok(got==expected,"80k WTap independent cadence oracle "+i);
            ok(policy.tick(active,eligible,tick,epoch)==Decision.NONE,
                "80k duplicate END callback never repeats a sprint mutation "+i);
            ok(policy.pending()==(resetAt>=0||restoreAt>=0),
                "80k reservation agrees with independent model "+i);
            if(r.nextInt(13)==0) {
                resetAt=restoreAt=-1;
                ok(policy.tick(active,false,tick,epoch)==Decision.NONE &&
                    !policy.pending(),
                    "same-tick manual override immediately cancels reservation "+i);
            }
            int delay=1+r.nextInt(3);
            boolean sprinting=r.nextBoolean();
            boolean event=r.nextInt(3)==0;
            if(event) {
                boolean accept=active&&eligible&&sprinting&&restoreAt<0 &&
                    lastAttack!=tick;
                boolean actual=policy.attack(active,eligible,sprinting,
                    delay,tick,epoch);
                // A same-tick manual override above is authoritative. The
                // native event is admitted only if movement is still valid.
                ok(actual==accept,"80k gated native attack oracle "+i);
                if(actual) {
                    resetAt=tick+1;restoreAt=resetAt+delay;lastAttack=tick;
                    ok(policy.tick(active,eligible,tick,epoch)==Decision.NONE,
                        "post-attack tick cannot write sprint early "+i);
                }
            }
        }
        ok(checks>=240000,"80k independent native cadence samples executed");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("WTAP_NATIVE_CADENCE_PASS assertions="+checks);
    }
}
