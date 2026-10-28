package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoRodPolicy.Action;
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

/** Native AutoRod policy tests, NOT a launched original-game test. */
public final class AutoRodTests {
    private static long count;
    private static void ok(boolean yes,String why) {
        count++;if(!yes)throw new AssertionError(why);
    }
    private static void reject(Runnable action,String why) {
        boolean bad=false;
        try{action.run();}catch(RuntimeException expected){bad=true;}
        ok(bad,why);
    }
    private static Action decide(AutoRodPolicy p,long e,long t,boolean held,
                                 boolean active,int current,int rod,int cooldown) {
        return p.decide(e,t,held,active,current,rod,cooldown);
    }
    private static void exact() throws Exception {
        AutoRodPolicy p=new AutoRodPolicy();
        ok(decide(p,1,0,false,true,2,6,30)==Action.NONE,
            "world entry seeds physical attack state");
        ok(decide(p,1,1,true,true,2,6,30)==Action.SELECT,
            "fresh physical press qualifies sword->rod selection");
        ok(p.ownsHotbar()&&p.originalSlot()==2&&p.ownedRodSlot()==6,
            "exact original and rod slots owned");
        ok(decide(p,1,1,true,true,2,6,30)==Action.NONE,
            "duplicate owner END tick never selects twice");
        reject(()->p.committed(Action.USE,1,1),"wrong native operation cannot commit");
        p.committed(Action.SELECT,1,1);
        ok(decide(p,1,2,true,true,6,6,30)==Action.USE,
            "next tick native rod right-click ready");
        p.committed(Action.USE,1,2);
        ok(p.castObserved(),"local native use successful receipt");
        ok(decide(p,1,3,false,true,6,6,30)==Action.RESTORE,
            "restore is a separate owner tick after actual rod use");
        p.committed(Action.RESTORE,1,3);
        ok(!p.ownsHotbar()&&!p.castObserved(),"native restore releases ownership");
        ok(decide(p,1,4,true,true,2,6,30)==Action.NONE,
            "repeated physical press does not bypass shared cooldown");
        ok(decide(p,1,5,false,true,2,6,30)==Action.NONE,"physical release arms next attempt");
        ok(decide(p,1,6,true,true,2,6,30)==Action.NONE,"cooldown rejects early attack");
        ok(decide(p,1,31,false,true,2,6,30)==Action.NONE,"cooldown expired, idle release");
        ok(decide(p,1,32,true,true,2,6,30)==Action.SELECT,
            "exact cooldown boundary permits a fresh rod cast");
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
            "new generation with attack already held cannot auto-rod");
        ok(decide(p,2,1,false,true,2,6,30)==Action.NONE,"must observe fresh release");
        ok(decide(p,2,2,true,true,2,6,30)==Action.SELECT,"fresh second-world press");
        p.committed(Action.SELECT,2,2);
        ok(decide(p,2,3,true,true,6,6,30)==Action.USE,"second world rod use");
        p.cancelPrepared();
        ok(decide(p,2,4,false,false,6,6,30)==Action.RESTORE,
            "native use rejection returns rod safely instead of retrying");
        ok(!p.castObserved(),"failed native use not counted as cast");
        p.committed(Action.RESTORE,2,4);
        ok(!p.ownsHotbar(),"safe restore on rejected native use");
        ok(decide(p,2,5,true,true,2,-1,30)==Action.NONE,
            "absent fishing rod never consumes hotbar owner");
        reject(()->p.decide(2,4,false,true,2,6,30),"backward tick is refused");
        reject(()->p.decide(2,6,true,true,2,9,30),"invalid rod slot refused");
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
        AutoRodPolicy p=new AutoRodPolicy();
        decide(p,1,0,false,true,1,5,30);
        ok(decide(p,1,1,true,true,1,5,30)==Action.SELECT,"prepare first");
        p.committed(Action.SELECT,1,1);
        ok(p.relinquish(3,true,false)==-1 && !p.ownsHotbar(),
            "manual scroll away from rod forbids restoring stale original slot");
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
            "same-player owned rod safely restores original selected slot");
    }
    private static void arbitration() {
        ModuleInteractionStateMachine state=new ModuleInteractionStateMachine();
        ModuleInteractionStateMachine.Enabled enabled=
            new ModuleInteractionStateMachine.Enabled(
                true,true,true,true,true,true,true,true,true,true);
        ModuleInteractionStateMachine.Frame frame=state.advance(1,0,true,
            ModuleInteractionStateMachine.Scene.GAME,true,enabled,false);
        ok(frame.autoRod && frame.combat && frame.jumpReset && frame.wtap,
            "AutoRod can coexist with registered combat and movement modules");
        frame=state.advance(1,1,true,ModuleInteractionStateMachine.Scene.GAME,
                            true,enabled,true);
        ok(!frame.autoRod && !frame.jumpReset &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_ROD)==
                ModuleInteractionStateMachine.Denial.WTAP_RESERVED,
            "genuine native WTap reservation preempts AutoRod");
        frame=state.advance(1,2,true,ModuleInteractionStateMachine.Scene.CHEST,
                            true,enabled,false);
        ok(!frame.autoRod &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_ROD)==
               ModuleInteractionStateMachine.Denial.WRONG_SCREEN,
            "chest transfer/GUI cannot operate rod");
        frame=state.advance(1,3,true,ModuleInteractionStateMachine.Scene.GAME,
                            false,enabled,false);
        ok(!frame.autoRod &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_ROD)==
               ModuleInteractionStateMachine.Denial.WINDOW_INACTIVE,
            "lost physical focus denies AutoRod");
        ModuleInteractionStateMachine.Enabled legacy=
            new ModuleInteractionStateMachine.Enabled(
                true,true,true,true,true,true,true,true,true);
        frame=state.advance(1,4,true,ModuleInteractionStateMachine.Scene.GAME,
                            true,legacy,false);
        ok(!frame.autoRod &&
           frame.denial(ModuleInteractionStateMachine.Resource.AUTO_ROD)==
               ModuleInteractionStateMachine.Denial.DISABLED,
            "old 9-argument Enabled never accidentally opts into AutoRod");
    }
    private static void seeded() {
        AutoRodPolicy p=new AutoRodPolicy();
        Random rng=new Random(356189L);
        long ep=1,tick=0;
        int cast=0,selected=0,restored=0,denied=0;
        for(int i=0;i<50000;i++) {
            if(i>0 && i%1399==0) {ep++;tick=0;p.reset();}
            // Randomly exercise single-owner transitions; model state is
            // tested at every committed operation and after manual preemption.
            int current=p.ownsHotbar()?p.ownedRodSlot():2;
            if(p.ownsHotbar()&&rng.nextInt(17)==0)current=4;
            boolean held=rng.nextInt(3)!=0;
            boolean active=rng.nextInt(11)!=0;
            int rod=rng.nextInt(23)==0?-1:6;
            Action action=p.decide(ep,tick++,held,active,current,rod,20);
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
               p.ownedRodSlot()>=-1&&p.ownedRodSlot()<=8,
               "50k no invalid owned inventory slots");
            if(p.ownsHotbar())
                ok(p.originalSlot()>=0&&p.ownedRodSlot()>=0,
                   "50k hotbar owned only as a complete transaction");
        }
        ok(selected>20&&cast>10&&restored>10&&denied>5,
           "50k deterministic mixed rod transactions exercised");
    }
    private static void hostAndPanic() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r356-autorod");
        HostRuntime host=new HostRuntime(dir);
        ok(!host.featureEnabled("combat.autorod"),"new F7 AutoRod defaults OFF");
        ok(host.rodCooldownTicks()==30&&host.rodRange()==3.25&&
           host.rodRequireSword()&&host.rodIgnoreTeammates(),
           "conservative AutoRod defaults");
        host.worldPresence(true);
        host.uiActions().fraction("combat.autorod","cooldown_ticks",.5);
        host.uiActions().fraction("combat.autorod","range",.8);
        host.uiActions().bool("combat.autorod","require_sword");
        host.uiActions().bool("combat.autorod","ignore_teammates");
        int cadence=host.rodCooldownTicks();
        double range=host.rodRange();
        ok(cadence>30 && range>3.25 && !host.rodRequireSword() &&
           !host.rodIgnoreTeammates(),"all real F7 controls editable");
        host.uiActions().toggle("combat.autorod");
        ok(host.featureEnabled("combat.autorod"),"explicit F7 enable");
        host.uiActions().toggle("hud.diagnostics");
        ok(host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                ModuleActionTelemetry.Event.ATTEMPT) &&
           host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                ModuleActionTelemetry.Event.DISPATCHED) &&
           host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                ModuleActionTelemetry.Event.LOCAL_COMPLETE),
           "local selected-use-restored receipts are separate from server hits");
        host.exportDiagnostics();
        Properties diagnostic=ConfigIntegrity.read(
            dir.resolve("diagnostics-last.properties"));
        ok("1".equals(diagnostic.getProperty("count.action_autorod_dispatched")) &&
           "1".equals(diagnostic.getProperty("count.action_autorod_local_complete")),
           "bounded anonymous native AutoRod action records");
        PanicController.disableAll(host);
        ok(!host.featureEnabled("combat.autorod"),"F6 Panic always disables AutoRod");
        host.close();
        HostRuntime next=new HostRuntime(dir);
        ok(!next.featureEnabled("combat.autorod") &&
           next.rodCooldownTicks()==cadence && next.rodRange()==range &&
           !next.rodRequireSword()&&!next.rodIgnoreTeammates(),
           "profile restart preserves settings but never re-arms rod");
        next.close();
    }
    public static void main(String[] args) throws Exception {
        exact();restoreSafety();arbitration();seeded();hostAndPanic();
        ok(count>100000,"AutoRod has substantial independent native owner replay");
        System.out.println("AUTOROD_PASS assertions="+count);
    }
}
