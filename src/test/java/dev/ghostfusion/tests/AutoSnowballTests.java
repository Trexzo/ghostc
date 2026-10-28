package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy.Action;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.core.ConfigIntegrity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native AutoSnowball policy tests, NOT a launched original-game test. */
public final class AutoSnowballTests {
    private static long count;
    private static void ok(boolean yes,String why) {
        count++;if(!yes)throw new AssertionError(why);
    }
    private static void reject(Runnable action,String why) {
        boolean bad=false;
        try{action.run();}catch(RuntimeException expected){bad=true;}
        ok(bad,why);
    }
    private static Action decide(AutoSnowballPolicy p,long e,long t,boolean held,
                                 boolean active,int current,int snowball,int cooldown) {
        return p.decide(e,t,held,active,current,snowball,cooldown);
    }
    private static void exact() throws Exception {
        AutoSnowballPolicy p=new AutoSnowballPolicy();
        ok(decide(p,1,0,false,true,2,6,30)==Action.NONE,
            "world entry seeds physical attack state");
        ok(decide(p,1,1,true,true,2,6,30)==Action.SELECT,
            "fresh physical press qualifies sword->snowball selection");
        ok(p.ownsHotbar()&&p.originalSlot()==2&&p.ownedSnowballSlot()==6,
            "exact original and snowball slots owned");
        ok(decide(p,1,1,true,true,2,6,30)==Action.NONE,
            "duplicate owner END tick never selects twice");
        reject(()->p.committed(Action.USE,1,1),"wrong native operation cannot commit");
        p.committed(Action.SELECT,1,1);
        ok(decide(p,1,2,true,true,6,6,30)==Action.USE,
            "next tick native snowball right-click ready");
        p.committed(Action.USE,1,2);
        ok(p.castObserved(),"local native use successful receipt");
        ok(decide(p,1,3,false,true,6,6,30)==Action.RESTORE,
            "restore is a separate owner tick after actual snowball use");
        p.committed(Action.RESTORE,1,3);
        ok(!p.ownsHotbar()&&!p.castObserved(),"native restore releases ownership");
        ok(decide(p,1,4,true,true,2,6,30)==Action.NONE,
            "repeated physical press does not bypass shared cooldown");
        ok(decide(p,1,5,false,true,2,6,30)==Action.NONE,"physical release arms next attempt");
        ok(decide(p,1,6,true,true,2,6,30)==Action.NONE,"cooldown rejects early attack");
        ok(decide(p,1,31,false,true,2,6,30)==Action.NONE,"cooldown expired, idle release");
        ok(decide(p,1,32,true,true,2,6,30)==Action.SELECT,
            "exact cooldown boundary permits a fresh snowball cast");
        p.committed(Action.SELECT,1,32);
        ok(decide(p,1,33,true,true,4,6,30)==Action.NONE,
            "manual hotbar override cancels ownership and never restores");
        ok(!p.ownsHotbar(),"user manual selection outranks synthetic hotbar");
        ok(decide(p,1,34,false,true,4,6,30)==Action.NONE,"manual slot remains authoritative");
        ok(decide(p,1,35,true,true,4,6,30)==Action.SELECT,
            "aborted selection does not burn a synthetic-use cooldown");
        p.cancelPrepared();
        ok(!p.ownsHotbar(),"native selection denied without taking ownership");
        ok(decide(p,2,0,true,true,2,6,30)==Action.NONE,
            "new generation with attack already held cannot auto-snowball");
        ok(decide(p,2,1,false,true,2,6,30)==Action.NONE,"must observe fresh release");
        ok(decide(p,2,2,true,true,2,6,30)==Action.SELECT,"fresh second-world press");
        p.committed(Action.SELECT,2,2);
        ok(decide(p,2,3,true,true,6,6,30)==Action.USE,"second world snowball use");
        p.cancelPrepared();
        ok(decide(p,2,4,false,false,6,6,30)==Action.RESTORE,
            "native use rejection returns snowball safely instead of retrying");
        ok(!p.castObserved(),"failed native use not counted as cast");
        p.committed(Action.RESTORE,2,4);
        ok(!p.ownsHotbar(),"safe restore on rejected native use");
        ok(decide(p,2,5,true,true,2,-1,30)==Action.NONE,
            "absent fishing snowball never consumes hotbar owner");
        reject(()->p.decide(2,4,false,true,2,6,30),"backward tick is refused");
        reject(()->p.decide(2,6,true,true,2,9,30),"invalid snowball slot refused");
        reject(()->p.decide(2,6,true,true,2,6,9),"unbounded cadence refused");
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try{p.decide(2,6,true,true,2,6,30);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        other.start();other.join();
        ok(rejected.get(),"Netty/foreign owner cannot move hotbar");
    }
    private static void restoreSafety() {
        AutoSnowballPolicy p=new AutoSnowballPolicy();
        decide(p,1,0,false,true,1,5,30);
        ok(decide(p,1,1,true,true,1,5,30)==Action.SELECT,"prepare first");
        p.committed(Action.SELECT,1,1);
        ok(p.relinquish(3,true,false)==-1 && !p.ownsHotbar(),
            "manual scroll away from snowball forbids restoring stale original slot");
        p.reset();
        decide(p,2,0,false,true,1,5,30);
        decide(p,2,1,true,true,1,5,30);
        p.committed(Action.SELECT,2,1);
        ok(p.relinquish(5,false,false)==-1,
            "world change cannot write into new world inventory");
        p.reset();
        decide(p,3,0,false,true,1,5,30);
        decide(p,3,1,true,true,1,5,30);
        p.committed(Action.SELECT,3,1);
        ok(p.relinquish(5,true,true)==-1,
            "physical hotbar number key wins over rollback");
        p.reset();
        decide(p,4,0,false,true,1,5,30);
        decide(p,4,1,true,true,1,5,30);
        p.committed(Action.SELECT,4,1);
        ok(p.relinquish(5,true,false)==1,
            "same-player owned snowball safely restores original selected slot");
    }
    private static void arbitration() {
        ModuleInteractionStateMachine state=new ModuleInteractionStateMachine();
        ModuleInteractionStateMachine.Enabled enabled=
            new ModuleInteractionStateMachine.Enabled(
                true,true,true,true,true,true,true,true,true,true,true);
        ModuleInteractionStateMachine.Frame frame=state.advance(1,0,true,
            ModuleInteractionStateMachine.Scene.GAME,true,enabled,false);
        ok(frame.autoSnowball && frame.combat && frame.jumpReset && frame.wtap,
            "AutoSnowball can coexist with registered combat and movement modules");
        frame=state.advance(1,1,true,ModuleInteractionStateMachine.Scene.GAME,
                            true,enabled,true);
        ok(!frame.autoSnowball && !frame.jumpReset &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_SNOWBALL)==
                ModuleInteractionStateMachine.Denial.WTAP_RESERVED,
            "genuine native WTap reservation preempts AutoSnowball");
        frame=state.advance(1,2,true,ModuleInteractionStateMachine.Scene.CHEST,
                            true,enabled,false);
        ok(!frame.autoSnowball &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_SNOWBALL)==
               ModuleInteractionStateMachine.Denial.WRONG_SCREEN,
            "chest transfer/GUI cannot operate snowball");
        frame=state.advance(1,3,true,ModuleInteractionStateMachine.Scene.GAME,
                            false,enabled,false);
        ok(!frame.autoSnowball &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_SNOWBALL)==
               ModuleInteractionStateMachine.Denial.WINDOW_INACTIVE,
            "lost physical focus denies AutoSnowball");
        ModuleInteractionStateMachine.Enabled legacy=
            new ModuleInteractionStateMachine.Enabled(
                true,true,true,true,true,true,true,true,true);
        frame=state.advance(1,4,true,ModuleInteractionStateMachine.Scene.GAME,
                            true,legacy,false);
        ok(!frame.autoSnowball &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_SNOWBALL)==
               ModuleInteractionStateMachine.Denial.DISABLED,
            "old 9-argument Enabled never accidentally opts into AutoSnowball");
    }
    private static void seeded() {
        AutoSnowballPolicy p=new AutoSnowballPolicy();
        Random rng=new Random(356189L);
        long ep=1,tick=0;
        int cast=0,selected=0,restored=0,denied=0;
        for(int i=0;i<50000;i++) {
            if(i>0 && i%1399==0) {ep++;tick=0;p.reset();}
            // Randomly exercise single-owner transitions; model state is
            // tested at every committed operation and after manual preemption.
            int current=p.ownsHotbar()?p.ownedSnowballSlot():2;
            if(p.ownsHotbar()&&rng.nextInt(17)==0)current=4;
            boolean held=rng.nextInt(3)!=0;
            boolean active=rng.nextInt(11)!=0;
            int snowball=rng.nextInt(23)==0?-1:6;
            Action action=p.decide(ep,tick++,held,active,current,snowball,20);
            ok(action!=Action.SELECT||(!p.castObserved()&&p.ownsHotbar()),
               "new selection has no phantom cast");
            if(action!=Action.NONE) {
                if(rng.nextInt(8)==0) {
                    p.cancelPrepared();denied++;
                }else {
                    if(action==Action.SELECT)selected++;
                    if(action==Action.USE)cast++;
                    if(action==Action.RESTORE)restored++;
                    p.committed(action,ep,tick-1);
                }
            }
            ok(p.originalSlot()>=-1&&p.originalSlot()<=8 &&
               p.ownedSnowballSlot()>=-1&&p.ownedSnowballSlot()<=8,
               "50k no invalid owned inventory slots");
            if(p.ownsHotbar())
                ok(p.originalSlot()>=0&&p.ownedSnowballSlot()>=0,
                   "50k hotbar owned only as a complete transaction");
        }
        ok(selected>20&&cast>10&&restored>10&&denied>5,
           "50k deterministic mixed snowball transactions exercised");
    }
    private static void hostAndPanic() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r356-autosnowball");
        HostRuntime host=new HostRuntime(dir);
        ok(!host.featureEnabled("combat.autosnowball"),"new F7 AutoSnowball defaults OFF");
        ok(host.snowballCooldownTicks()==20&&host.snowballRange()==3.25&&
           host.snowballRequireSword()&&host.snowballIgnoreTeammates(),
           "conservative AutoSnowball defaults");
        host.worldPresence(true);
        host.uiActions().fraction("combat.autosnowball","cooldown_ticks",.5);
        host.uiActions().fraction("combat.autosnowball","range",.8);
        host.uiActions().bool("combat.autosnowball","require_sword");
        host.uiActions().bool("combat.autosnowball","ignore_teammates");
        int cadence=host.snowballCooldownTicks();
        double range=host.snowballRange();
        ok(cadence>20 && range>3.25 && !host.snowballRequireSword() &&
           !host.snowballIgnoreTeammates(),"all real F7 controls editable");
        host.uiActions().toggle("combat.autosnowball");
        ok(host.featureEnabled("combat.autosnowball"),"explicit F7 enable");
        host.uiActions().toggle("hud.diagnostics");
        ok(host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.ATTEMPT) &&
           host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.DISPATCHED) &&
           host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.LOCAL_COMPLETE),
           "local selected-use-restored receipts are separate from server hits");
        host.exportDiagnostics();
        Properties diagnostic=ConfigIntegrity.read(
            dir.resolve("diagnostics-last.properties"));
        ok("1".equals(diagnostic.getProperty("count.action_autosnowball_dispatched")) &&
           "1".equals(diagnostic.getProperty("count.action_autosnowball_local_complete")),
           "bounded anonymous native AutoSnowball action records");
        PanicController.disableAll(host);
        ok(!host.featureEnabled("combat.autosnowball"),"F6 Panic always disables AutoSnowball");
        host.close();
        HostRuntime next=new HostRuntime(dir);
        ok(!next.featureEnabled("combat.autosnowball") &&
           next.snowballCooldownTicks()==cadence && next.snowballRange()==range &&
           !next.snowballRequireSword()&&!next.snowballIgnoreTeammates(),
           "profile restart preserves settings but never re-arms snowball");
        next.close();
    }
    public static void main(String[] args) throws Exception {
        exact();restoreSafety();arbitration();seeded();hostAndPanic();
        ok(count>100000,"AutoSnowball has substantial independent native owner replay");
        System.out.println("AUTOSNOWBALL_PASS assertions="+count);
    }
}
