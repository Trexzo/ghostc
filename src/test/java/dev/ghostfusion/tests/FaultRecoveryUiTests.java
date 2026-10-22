package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.UiActions;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.63 safe, two-action recovery from real module lifecycle faults. */
public final class FaultRecoveryUiTests {
    private static long checks;
    private static void ok(boolean good,String why){
        checks++;if(!good)throw new AssertionError(why);
    }
    private static void rejects(Runnable run,String why){
        boolean rejected=false;
        try{run.run();}catch(IllegalStateException expected){rejected=true;}
        ok(rejected,why);
    }
    private static final class Trouble implements GhostModule {
        final String id;
        boolean badEnable,badDisable,badEvent;
        int starts,stops,events;
        Runnable nested;
        Trouble(String id){this.id=id;}
        public String id(){return id;}
        public String displayName(){return "Recoverable test";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){
            starts++;
            if(nested!=null)nested.run();
            if(badEnable)throw new IllegalArgumentException("PRIVATE-CHAT-DATA");
        }
        public void onDisable(){
            stops++;
            if(badDisable)throw new IllegalStateException("PRIVATE-SERVER-NAME");
        }
        public void onEvent(GameEvent event){
            events++;
            if(badEvent)throw new IllegalArgumentException("PRIVATE-USERNAME");
        }
    }
    private static void faultedClickGuiReset(){
        ModuleRegistry registry=new ModuleRegistry();
        Trouble broken=new Trouble("fault.gui");
        Trouble healthy=new Trouble("fault.healthy");
        registry.register(broken,0);
        registry.register(healthy,0);
        final int[] saves={0};
        UiActions ui=new UiActions(registry,()->saves[0]++);
        broken.badEnable=true;
        ui.toggle(broken.id);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.FAULTED,
            "failed onEnable latches the fault");
        ok(broken.starts==1&&broken.stops==1,"onEnable cleanup callback exactly once");
        ok("IllegalArgumentException".equals(registry.get(broken.id).lastFault()),
            "only sanitized exception class appears to the GUI");
        ok(!registry.get(broken.id).lastFault().contains("PRIVATE"),
            "fault metadata cannot leak exception message");
        rejects(()->registry.setEnabled(broken.id,true),
            "core still rejects attempted auto retry without reset");
        ui.toggle(healthy.id);
        ok(registry.get(healthy.id).state()==ModuleRegistry.State.ENABLED,
            "unrelated healthy module works after fault");
        ui.toggle(broken.id); // F7 mouse or Enter -> RESET, never enable
        ok(registry.get(broken.id).state()==ModuleRegistry.State.DISABLED,
            "one explicit GUI RESET leaves module disabled");
        ok(registry.get(broken.id).lastFault().isEmpty(),
            "RESET clears old exception type");
        ok(broken.starts==1&&broken.stops==1,
            "RESET never triggers either lifecycle callback");
        broken.badEnable=false;
        ui.toggle(broken.id);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.ENABLED,
            "separate user action re-enables after recovery");
        ok(broken.starts==2,"exactly one controlled retry");
        ok(saves[0]==4,"GUI enable/fault/reset/retry + independent healthy action saves once each");
        registry.shutdown();
    }
    private static void keybindReset(){
        ModuleRegistry registry=new ModuleRegistry();
        Trouble broken=new Trouble("fault.key");
        registry.register(broken,30);
        broken.badEnable=true;
        registry.toggleKey(30);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.FAULTED &&
            broken.starts==1,"bound key enable can fail closed");
        for(int step=0;step<1600;step++){
            // Multiple unrelated keys may never mutate this latched fault.
            registry.toggleKey(31+step%25);
            ok(registry.get(broken.id).state()==ModuleRegistry.State.FAULTED,
                "unrelated keypress cannot auto-retry "+step);
        }
        registry.toggleKey(30);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.DISABLED,
            "next bound keypress resets fault instead of re-enabling");
        ok(broken.starts==1&&broken.stops==1,
            "keypress RESET invokes zero extra lifecycle callbacks");
        broken.badEnable=false;
        registry.toggleKey(30);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.ENABLED &&
            broken.starts==2,"subsequent keypress explicitly retries");
        registry.toggleKey(30);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.DISABLED &&
            broken.stops==2,"ordinary keypress still disables a healthy module");
        registry.shutdown();
    }
    private static void eventAndDisableFaults(){
        ModuleRegistry registry=new ModuleRegistry();
        Trouble broken=new Trouble("fault.event");
        registry.register(broken,0);
        final int[] saves={0};
        UiActions ui=new UiActions(registry,()->saves[0]++);
        broken.badEvent=true;
        ui.toggle(broken.id);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        ok(registry.get(broken.id).state()==ModuleRegistry.State.FAULTED,
            "event listener failure is quarantined");
        ok(broken.events==1&&broken.stops==1,
            "event failure cleanup invoked only once");
        ui.toggle(broken.id);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.DISABLED &&
            broken.stops==1,"event failure RESET cannot reenter cleanup");
        broken.badEvent=false;
        ui.toggle(broken.id);
        broken.badDisable=true;
        ui.toggle(broken.id);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.FAULTED,
            "throwing onDisable remains faulted");
        int stopped=broken.stops,started=broken.starts;
        ui.toggle(broken.id);
        ok(registry.get(broken.id).state()==ModuleRegistry.State.DISABLED &&
            broken.stops==stopped&&broken.starts==started,
            "disable failure RESET invokes no callbacks");
        ok(saves[0]==5,"each intentional GUI action persists exactly once");
        registry.shutdown();
    }
    private static void noReentrantRetry(){
        ModuleRegistry registry=new ModuleRegistry();
        Trouble t=new Trouble("fault.reentrant");
        registry.register(t,41);
        t.nested=()->registry.toggleKey(41);
        registry.toggleKey(41);
        ok(registry.get(t.id).state()==ModuleRegistry.State.FAULTED,
            "reentrant toggle is contained as module fault");
        ok(t.starts==1&&t.stops==1,"reentrant keybind never invokes repeated callback");
        t.nested=null;
        registry.toggleKey(41);
        ok(registry.get(t.id).state()==ModuleRegistry.State.DISABLED &&
            t.starts==1,"reset clears recursive fault without retrying");
        registry.toggleKey(41);
        ok(registry.get(t.id).state()==ModuleRegistry.State.ENABLED &&
            t.starts==2,"explicit post-reset retry works");
        registry.shutdown();
    }
    private static void ownershipAndStress(){
        ModuleRegistry registry=new ModuleRegistry();
        Trouble t=new Trouble("fault.stress");
        registry.register(t,51);
        final int[] saves={0};
        UiActions ui=new UiActions(registry,()->saves[0]++);
        AtomicBoolean denied=new AtomicBoolean();
        Thread wrong=new Thread(()->{
            try{ui.toggle(t.id);}catch(IllegalStateException expected){denied.set(true);}
        });
        wrong.start();
        try{wrong.join();}catch(InterruptedException ex){throw new AssertionError(ex);}
        ok(denied.get()&&saves[0]==0,"off-owner GUI toggle cannot mutate/persist");
        for(int round=0;round<5000;round++){
            t.badEnable=true;
            ui.toggle(t.id);
            ok(registry.get(t.id).state()==ModuleRegistry.State.FAULTED,
                "stress failed enable remains quarantined "+round);
            int before=t.starts;
            ui.toggle(t.id);
            ok(registry.get(t.id).state()==ModuleRegistry.State.DISABLED &&
               t.starts==before,"stress reset never auto retries "+round);
            t.badEnable=false;
            ui.toggle(t.id);
            ok(registry.get(t.id).state()==ModuleRegistry.State.ENABLED,
                "stress explicit re-enable succeeds "+round);
            ui.toggle(t.id);
            ok(registry.get(t.id).state()==ModuleRegistry.State.DISABLED,
                "stress normal disable remains functional "+round);
        }
        ok(saves[0]==20000,"one persist per real action across 5000 recoveries");
        registry.shutdown();
    }
    public static void main(String[] args){
        faultedClickGuiReset();keybindReset();eventAndDisableFaults();
        noReentrantRetry();ownershipAndStress();
        ok(checks>20000,"substantial fault reset and recovery regression");
        System.out.println("FAULT_RECOVERY_UI_PASS assertions="+checks);
    }
}
