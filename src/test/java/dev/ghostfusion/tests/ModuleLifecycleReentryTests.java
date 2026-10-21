package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.concurrent.atomic.AtomicInteger;

/** R2.54 owner-thread lifecycle re-entrancy and sealed shutdown regression. */
public final class ModuleLifecycleReentryTests {
    private static int checks;
    private static void ok(boolean valid,String description) {
        checks++;
        if(!valid)throw new AssertionError(description);
    }
    private static void refused(Runnable callback,String description) {
        boolean rejected=false;
        try { callback.run(); } catch(IllegalStateException expected){rejected=true;}
        ok(rejected,description);
    }
    private static class Module implements GhostModule {
        final String name;
        Runnable enable, disable;
        int starts,stops;
        Module(String name){this.name=name;}
        public String id(){return name;}
        public String displayName(){return name;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){starts++;if(enable!=null)enable.run();}
        public void onDisable(){stops++;if(disable!=null)disable.run();}
        public void onEvent(GameEvent event){}
    }
    private static void recursiveTransitions() {
        ModuleRegistry registry=new ModuleRegistry();
        Module self=new Module("recursive.enable");
        registry.register(self,0);
        self.enable=()->registry.setEnabled(self.id(),true);
        registry.setEnabled(self.id(),true);
        ok(registry.get(self.id()).state()==ModuleRegistry.State.FAULTED,
            "self-enabling callback fails closed");
        ok(self.starts==1 && self.stops==1,"recursive enable never re-enters or loops");
        ok("IllegalStateException".equals(registry.get(self.id()).lastFault()),
            "fault reason only includes exception type");
        refused(()->registry.setEnabled(self.id(),true),"faulted callback cannot immediately reenable");
        registry.setEnabled(self.id(),false);
        self.enable=null;
        registry.setEnabled(self.id(),true);
        ok(registry.get(self.id()).state()==ModuleRegistry.State.ENABLED,
            "fault reset restores legitimate enable path");
        self.disable=()->registry.setEnabled(self.id(),false);
        registry.setEnabled(self.id(),false);
        ok(registry.get(self.id()).state()==ModuleRegistry.State.FAULTED,
            "self-disabling callback fails closed");
        ok(self.stops==2,"failing onDisable is not invoked a second time");
        registry.setEnabled(self.id(),false);
        self.disable=null;
        registry.setEnabled(self.id(),true);
        registry.setEnabled(self.id(),false);
        ok(registry.get(self.id()).state()==ModuleRegistry.State.DISABLED,
            "lifecycle guard is released for a normal toggle");
        registry.shutdown();
    }
    private static void crossModuleCycle() {
        ModuleRegistry registry=new ModuleRegistry();
        Module a=new Module("cycle.a"),b=new Module("cycle.b");
        registry.register(a,0);registry.register(b,0);
        a.enable=()->registry.setEnabled(b.id(),true);
        b.enable=()->registry.setEnabled(a.id(),true);
        registry.setEnabled(a.id(),true);
        ok(a.starts==1 && b.starts==1,"cross-module cycle bounded at first revisit");
        ok(registry.get(b.id()).state()==ModuleRegistry.State.FAULTED,
            "nested peer that revisits active parent is quarantined");
        ok(registry.get(a.id()).state()==ModuleRegistry.State.ENABLED,
            "independent parent remains enabled after peer failure");
        registry.shutdown();
        ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED,
            "shutdown finishes an otherwise healthy parent");
    }
    private static void shutdownSealedAndNested() {
        ModuleRegistry registry=new ModuleRegistry();
        Module a=new Module("shutdown.a"),b=new Module("shutdown.b");
        registry.register(a,0);registry.register(b,0);
        registry.setEnabled(a.id(),true);registry.setEnabled(b.id(),true);
        AtomicInteger blocked=new AtomicInteger();
        a.disable=()->{
            try{registry.setEnabled(b.id(),true);}catch(IllegalStateException ex){blocked.incrementAndGet();}
            try{registry.bind(b.id(),42);}catch(IllegalStateException ex){blocked.incrementAndGet();}
            try{registry.register(new Module("shutdown.late"),0);}catch(IllegalStateException ex){blocked.incrementAndGet();}
            registry.shutdown(); // already closing: explicitly idempotent
        };
        registry.shutdown();
        ok(blocked.get()==3,"shutdown callbacks cannot mutate module state/keybind/registry");
        ok(a.stops==1 && b.stops==1,
            "each enabled module receives exactly one disable callback");
        ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED &&
           registry.get(b.id()).state()==ModuleRegistry.State.DISABLED,
            "sealed shutdown leaves no enabled module");
        registry.shutdown();
        refused(()->registry.setEnabled(a.id(),true),"shutdown remains terminal");
        refused(()->registry.register(new Module("shutdown.new"),0),"late module registration rejected");
        refused(()->registry.bind(b.id(),3),"late rebinding rejected");
    }
    private static void shutdownDuringEnable() {
        ModuleRegistry registry=new ModuleRegistry();
        Module bad=new Module("shutdown.inside.enable");
        Module good=new Module("shutdown.healthy");
        registry.register(bad,0);registry.register(good,0);
        bad.enable=registry::shutdown;
        registry.setEnabled(bad.id(),true);
        ok(registry.get(bad.id()).state()==ModuleRegistry.State.FAULTED &&
           bad.starts==1 && bad.stops==1,
            "shutdown during uncommitted enable is rejected and contained");
        registry.setEnabled(good.id(),true);
        ok(registry.get(good.id()).state()==ModuleRegistry.State.ENABLED,
            "failed nested shutdown leaves registry usable");
        registry.shutdown();
        ok(registry.get(good.id()).state()==ModuleRegistry.State.DISABLED,
            "later legitimate shutdown finalizes registry");
    }
    private static void eventFailureCleanupReentry() {
        ModuleRegistry registry=new ModuleRegistry();
        Module bad=new Module("event.fault");
        registry.register(bad,0);
        bad.disable=()->registry.setEnabled(bad.id(),false);
        registry.setEnabled(bad.id(),true);
        // Event failure cleanup used to recurse through an enabled entry.
        // Exercise it through a dedicated module with a failing onEvent.
        Module failing=new Module("event.throw") {
            public void onEvent(GameEvent event){throw new IllegalStateException("PRIVATE EVENT");}
        };
        failing.disable=()->registry.setEnabled(failing.id(),false);
        registry.register(failing,0);
        registry.setEnabled(failing.id(),true);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        ok(registry.get(failing.id()).state()==ModuleRegistry.State.FAULTED &&
           failing.stops==1,"event fault cleanup cannot recursively disable itself");
        ok("IllegalStateException".equals(registry.get(failing.id()).lastFault()),
            "private callback messages never appear in fault metadata");
        registry.shutdown();
    }
    private static void longSession() {
        ModuleRegistry registry=new ModuleRegistry();
        Module module=new Module("session.loop");
        registry.register(module,0);
        for(int i=0;i<5000;i++){
            registry.setEnabled(module.id(),true);
            ok(registry.get(module.id()).state()==ModuleRegistry.State.ENABLED,
                "enable succeeds after previous lifecycle ends "+i);
            registry.setEnabled(module.id(),false);
            ok(registry.get(module.id()).state()==ModuleRegistry.State.DISABLED,
                "disable releases lifecycle marker "+i);
        }
        ok(module.starts==5000 && module.stops==5000,
            "5,000 enable/disable callbacks exactly once each");
        registry.shutdown();
    }
    public static void main(String[] args) {
        recursiveTransitions();crossModuleCycle();shutdownSealedAndNested();
        shutdownDuringEnable();eventFailureCleanupReentry();longSession();
        ok(checks>10000,"substantial lifecycle reentry regression");
        System.out.println("MODULE_LIFECYCLE_REENTRY_PASS assertions="+checks);
    }
}
