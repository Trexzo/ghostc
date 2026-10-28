package dev.ghostfusion.tests;

import dev.ghostfusion.features.JumpResetPolicy;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.core.ConfigIntegrity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real owner-thread Jump Reset policy; not launched Minecraft acceptance. */
public final class JumpResetTests {
    private static long count;
    private static void ok(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
    private static void rejects(Runnable r,String why){
        boolean denied=false;try{r.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static void exact() throws Exception {
        JumpResetPolicy p=new JumpResetPolicy();
        long e=10;
        ok(!p.preview(e,0,10,true,8,12),"join with existing damage cannot jump");
        ok(!p.preview(e,1,9,true,8,12),"decaying hurt cannot jump");
        ok(!p.preview(e,2,0,true,8,12),"return to idle");
        ok(p.preview(e,3,10,true,8,12),"fresh qualifying damage edge prepares jump");
        ok(!p.preview(e,3,10,true,8,12),"duplicate game tick cannot double jump");
        rejects(()->p.committed(e,4),"must commit only exact current native tick");
        p.cancelPrepared();
        rejects(()->p.committed(e,3),"native rejection cannot commit cooldown");
        ok(!p.preview(e,4,9,true,8,12),"previous canceled event never replays");
        ok(!p.preview(e,5,0,true,8,12),"reset decayed");
        ok(p.preview(e,6,10,true,8,12),"next true damage edge can prepare");
        p.committed(e,6);
        rejects(()->p.committed(e,6),"one native jump per prepared frame");
        ok(!p.preview(e,7,9,true,8,12),"cooldown");
        ok(!p.preview(e,8,0,true,8,12),"hurt baseline");
        ok(!p.preview(e,9,10,true,8,12),"new damage inside cooldown denied");
        ok(!p.preview(e,10,9,true,8,12),"denied damage cannot queue future jump");
        ok(!p.preview(e,11,0,true,8,12),"recover hurt");
        ok(p.preview(e,18,10,true,8,12),"cooldown exact tick permits new damage");
        p.committed(e,18);
        ok(!p.preview(e,19,0,true,8,12),"idle again");
        ok(!p.preview(e,20,10,false,8,12),
            "manual/GUI/WTap denial consumes damage edge without action");
        ok(!p.preview(e,21,9,true,8,12),
            "reenabling cannot borrow an earlier suppressed edge");
        p.reset();
        ok(!p.preview(11,0,10,true,8,12),
            "new world with pending hurt seeds baseline");
        ok(!p.preview(11,1,0,true,8,12),"new world baseline recovery");
        ok(p.preview(11,2,10,true,8,12),"new genuine damage edge");
        p.cancelPrepared();
        rejects(()->p.preview(11,1,9,true,8,12),"backward tick refused");
        rejects(()->p.preview(11,3,21,true,8,12),"impossible hurt value refused");
        rejects(()->p.preview(11,3,9,true,0,12),"invalid threshold refused");
        rejects(()->p.preview(11,3,9,true,8,1),"unbounded cadence refused");
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try{p.preview(11,4,10,true,8,12);}
            catch(IllegalStateException expected){denied.set(true);}
        });other.start();other.join();
        ok(denied.get(),"Netty/foreign thread denied movement ownership");
    }
    private static void arbiter() {
        ModuleInteractionStateMachine m=new ModuleInteractionStateMachine();
        ModuleInteractionStateMachine.Enabled e=
            new ModuleInteractionStateMachine.Enabled(true,true,true,true,true,
                true,true,true,true);
        ModuleInteractionStateMachine.Frame f=m.advance(1,0,true,
            ModuleInteractionStateMachine.Scene.GAME,true,e,false);
        ok(f.jumpReset && f.wtap && f.combat && f.velocity && f.bridge,
           "normal gameplay grants jump independently of attack and velocity");
        ok(f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
           ModuleInteractionStateMachine.Denial.NONE,
           "explicit Jump Reset gameplay resource admits normal frame");
        f=m.advance(1,1,true,ModuleInteractionStateMachine.Scene.GAME,true,e,true);
        ok(!f.jumpReset && f.wtap && !f.autoSprint &&
           f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
               ModuleInteractionStateMachine.Denial.WTAP_RESERVED,
           "WTap reserves sprint window and suppresses synthetic jump");
        f=m.advance(1,2,true,ModuleInteractionStateMachine.Scene.OTHER_GUI,true,e,false);
        ok(!f.jumpReset && f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
           ModuleInteractionStateMachine.Denial.WRONG_SCREEN,
           "GUI blocks Jump Reset without affecting inventory");
        f=m.advance(1,3,true,ModuleInteractionStateMachine.Scene.GAME,false,e,false);
        ok(!f.jumpReset && f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
           ModuleInteractionStateMachine.Denial.WINDOW_INACTIVE,
           "focus loss denies Jump Reset");
        f=m.advance(2,0,false,ModuleInteractionStateMachine.Scene.NO_WORLD,false,e,false);
        ok(!f.jumpReset && f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
           ModuleInteractionStateMachine.Denial.WORLD_UNAVAILABLE,
           "world switch cannot borrow old movement grant");
        // Legacy 8-argument constructor must remain disabled by default.
        ModuleInteractionStateMachine.Enabled legacy=
            new ModuleInteractionStateMachine.Enabled(true,true,true,true,true,
                true,true,true);
        f=m.advance(2,1,true,ModuleInteractionStateMachine.Scene.GAME,true,legacy,false);
        ok(!f.jumpReset && f.denial(ModuleInteractionStateMachine.Resource.JUMP_RESET)==
           ModuleInteractionStateMachine.Denial.DISABLED,
           "existing clients/tests do not silently opt in Jump Reset");
    }
    private static void random() {
        JumpResetPolicy p=new JumpResetPolicy();
        Random r=new Random(355189L);
        long ep=1,accepted=-1;
        int prior=-1,oldMin=-1,oldCooldown=-1;
        int jumps=0;
        for(int tick=0;tick<50000;tick++) {
            if(tick>0&&tick%1511==0){
                ep++;prior=oldMin=oldCooldown=-1;accepted=-1;
            }
            int h=r.nextInt(29)==0?10:r.nextInt(8);
            boolean active=r.nextInt(6)!=0;
            int minimum=8+(tick/739)%3;
            int cooldown=8+(tick/1013)%8;
            boolean expected;
            if(oldMin!=minimum||oldCooldown!=cooldown){
                prior=h;oldMin=minimum;oldCooldown=cooldown;expected=false;
            }else if(prior<0){prior=h;expected=false;}
            else {
                boolean rise=h>prior && h>=minimum;
                prior=h;
                expected=active && rise && (accepted<0||tick-accepted>=cooldown);
            }
            boolean result=p.preview(ep,tick,h,active,minimum,cooldown);
            ok(result==expected,"50k independent damage-edge oracle "+tick);
            if(result){
                if(r.nextInt(4)==0) {
                    p.cancelPrepared(); // genuine native veto
                }else{
                    p.committed(ep,tick);
                    accepted=tick;jumps++;
                }
            }
        }
        ok(jumps>100,"seeded replay emits genuine qualifying jumps");
    }
    private static void persistedHost() throws Exception {
        Path root=Files.createTempDirectory("ghostc-r355-jumpreset-");
        HostRuntime h=new HostRuntime(root);
        ok(!h.featureEnabled("combat.jumpreset"),"new module defaults off");
        ok(h.jumpResetMinHurtTime()==8 && h.jumpResetCooldownTicks()==12 &&
           h.jumpResetRequireAttackHeld(),"conservative initial settings");
        h.worldPresence(true);
        ok(h.modules().get("combat.jumpreset").settings().containsKey("min_hurt_time") &&
           h.modules().get("combat.jumpreset").settings().containsKey("cooldown_ticks") &&
           h.modules().get("combat.jumpreset").settings().containsKey("require_attack_held"),
           "real F7 module exposes typed settings");
        h.uiActions().fraction("combat.jumpreset","min_hurt_time",1.0);
        h.uiActions().fraction("combat.jumpreset","cooldown_ticks",.5);
        h.uiActions().bool("combat.jumpreset","require_attack_held");
        ok(h.jumpResetMinHurtTime()==10 && h.jumpResetCooldownTicks()==21 &&
           !h.jumpResetRequireAttackHeld(),"typed F7 settings update live module");
        h.uiActions().toggle("combat.jumpreset");
        ok(h.featureEnabled("combat.jumpreset"),"explicit user action enables Jump Reset");
        h.uiActions().toggle("hud.diagnostics");
        ok(h.nativeAction(ModuleActionTelemetry.Lane.JUMPRESET,
            ModuleActionTelemetry.Event.DISPATCHED),
           "only opt-in actual local native jump is an action receipt");
        h.exportDiagnostics();
        Properties props=ConfigIntegrity.read(root.resolve("diagnostics-last.properties"));
        ok("1".equals(props.getProperty("count.action_jumpreset_dispatched")) &&
           "counter-only-no-identity".equals(props.getProperty("scope")),
           "anonymized Jump Reset dispatch is not labeled a hit");
        PanicController.disableAll(h);
        ok(!h.featureEnabled("combat.jumpreset"),"F6 Panic disables jump immediately");
        h.close();
        HostRuntime reloaded=new HostRuntime(root);
        ok(!reloaded.featureEnabled("combat.jumpreset") &&
           reloaded.jumpResetMinHurtTime()==10 &&
           reloaded.jumpResetCooldownTicks()==21 &&
           !reloaded.jumpResetRequireAttackHeld(),
           "Panic persisted OFF state but not defaulted user settings");
        reloaded.close();
    }
    public static void main(String[] args) throws Exception {
        exact();arbiter();random();persistedHost();
        ok(count>50000,"Jump Reset strict native-commit replay and integration coverage");
        System.out.println("JUMP_RESET_PASS assertions="+count);
    }
}
