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
    /** Registry preflight can be asked while a lifecycle callback is active
     * without changing shutdown authority or committing any state. */
    private static void explicitShutdownPreflight() {
        ModuleRegistry r=new ModuleRegistry();
        Module m=new Module("preflight.callbacks");
        r.register(m,0);
        final AtomicInteger refused=new AtomicInteger();
        m.enable=()->{
            try{r.assertShutdownAllowed();}
            catch(IllegalStateException expected){refused.incrementAndGet();}
        };
        m.disable=()->{
            try{r.assertShutdownAllowed();}
            catch(IllegalStateException expected){refused.incrementAndGet();}
        };
        r.setEnabled(m.id(),true);
        ok(refused.get()==1 &&
           r.get(m.id()).state()==ModuleRegistry.State.ENABLED,
           "onEnable preflight refusal does not fault or seal registry");
        r.setEnabled(m.id(),false);
        ok(refused.get()==2 &&
           r.get(m.id()).state()==ModuleRegistry.State.DISABLED,
           "onDisable preflight refusal leaves registry usable");
        r.assertShutdownAllowed();
        r.shutdown();
        r.shutdown();
        ok(refused.get()==2,"ordinary shutdown remains idempotent");
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
    private static void stableEventSnapshot() {
        ModuleRegistry registry=new ModuleRegistry();
        final int[] calls={0,0,0};
        Module late=new Module("event.late") {
            public void onEvent(GameEvent event){calls[2]++;}
        };
        Module early=new Module("event.early") {
            private boolean added;
            public void onEvent(GameEvent event) {
                calls[0]++;
                if(!added) {
                    registry.register(late,0);
                    registry.setEnabled(late.id(),true);
                    added=true;
                }
            }
        };
        Module peer=new Module("event.peer") {
            public void onEvent(GameEvent event){calls[1]++;}
        };
        registry.register(early,0);registry.register(peer,0);
        registry.setEnabled(early.id(),true);registry.setEnabled(peer.id(),true);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        ok(calls[0]==1 && calls[1]==1 && calls[2]==0,
            "mid-dispatch registration is deferred until the next event");
        ok(registry.get(late.id()).state()==ModuleRegistry.State.ENABLED,
            "new module is fully enabled without invalidating active iterator");
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,2));
        ok(calls[0]==2 && calls[1]==2 && calls[2]==1,
            "new snapshot delivers future events in registration order");
        for(int i=0;i<20000;i++)
            registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,3+i));
        ok(calls[0]==20002 && calls[1]==20002 && calls[2]==20001,
            "20,000 events keep exact stable delivery with no map mutation");
        registry.shutdown();
    }
    private static void peerMutationAndShutdown() {
        ModuleRegistry registry=new ModuleRegistry();
        final int[] calls={0,0};
        Module peer=new Module("event.skip.peer") {
            public void onEvent(GameEvent event){calls[1]++;}
        };
        Module first=new Module("event.skip.first") {
            public void onEvent(GameEvent event) {
                calls[0]++;
                registry.setEnabled(peer.id(),false);
            }
        };
        registry.register(first,0);registry.register(peer,0);
        registry.setEnabled(first.id(),true);registry.setEnabled(peer.id(),true);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,3));
        ok(calls[0]==1 && calls[1]==0,
            "disabled peer must not receive event from captured snapshot");
        registry.shutdown();

        ModuleRegistry closing=new ModuleRegistry();
        final int[] after={0};
        Module shutdown=new Module("event.close") {
            public void onEvent(GameEvent event){closing.shutdown();}
        };
        Module never=new Module("event.after.close") {
            public void onEvent(GameEvent event){after[0]++;}
        };
        closing.register(shutdown,0);closing.register(never,0);
        closing.setEnabled(shutdown.id(),true);closing.setEnabled(never.id(),true);
        closing.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,4));
        ok(after[0]==0 && shutdown.stops==1 && never.stops==1,
            "shutdown inside callback seals registry before remaining delivery");
        refused(()->closing.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,5)),
            "shutdown remains final after a completed dispatch");
    }
    private static void recursiveEventIsolation() {
        ModuleRegistry registry=new ModuleRegistry();
        final int[] healthyCalls={0};
        Module recursive=new Module("event.recursive") {
            public void onEvent(GameEvent event) {
                registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,
                    event.sequence()+1));
            }
        };
        Module healthy=new Module("event.healthy") {
            public void onEvent(GameEvent event){healthyCalls[0]++;}
        };
        registry.register(recursive,0);registry.register(healthy,0);
        registry.setEnabled(recursive.id(),true);
        registry.setEnabled(healthy.id(),true);
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,10));
        ok(registry.get(recursive.id()).state()==ModuleRegistry.State.FAULTED &&
           recursive.stops==1 && recursive.starts==1,
            "nested dispatch is fault-contained without stack recursion");
        ok("IllegalStateException".equals(registry.get(recursive.id()).lastFault()),
            "recursive dispatch records only exception class");
        ok(healthyCalls[0]==1,
            "healthy peer receives current event despite recursive offender");
        registry.dispatch(new GameEvent(GameEvent.Kind.CLIENT_TICK,11));
        ok(healthyCalls[0]==2,
            "dispatching guard releases after contained recursive failure");
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
        explicitShutdownPreflight();
        recursiveTransitions();crossModuleCycle();shutdownSealedAndNested();
        shutdownDuringEnable();eventFailureCleanupReentry();
        stableEventSnapshot();peerMutationAndShutdown();recursiveEventIsolation();
        longSession();
        ok(checks>10000,"substantial lifecycle reentry regression");
        System.out.println("MODULE_LIFECYCLE_REENTRY_PASS assertions="+checks);
    }
}
