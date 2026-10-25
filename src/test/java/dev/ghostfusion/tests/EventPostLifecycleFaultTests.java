package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;

/** R3.27: onEvent must not double-clean a module already disabled by itself or shutdown. */
public final class EventPostLifecycleFaultTests {
    private static int checks;
    private static void ok(boolean valid,String reason) {
        checks++;
        if(!valid)throw new AssertionError(reason);
    }
    private static final class Probe implements GhostModule {
        final String name;
        Runnable event,disable;
        int events,disables,enables;
        Probe(String name){this.name=name;}
        public String id(){return name;}
        public String displayName(){return name;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){enables++;}
        public void onDisable(){disables++;if(disable!=null)disable.run();}
        public void onEvent(GameEvent e){events++;if(event!=null)event.run();}
    }
    private static GameEvent tick(long sequence){
        return new GameEvent(GameEvent.Kind.CLIENT_TICK,sequence);
    }
    private static void selfDisableThenThrow() {
        for(int i=0;i<1024;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Probe a=new Probe("event.self_disable"),peer=new Probe("event.healthy");
            registry.register(a,0);registry.register(peer,0);
            registry.setEnabled(a.id(),true);registry.setEnabled(peer.id(),true);
            a.event=()->{
                registry.setEnabled(a.id(),false);
                throw new IllegalArgumentException("PRIVATE TOKEN SHOULD NOT BE STORED");
            };
            registry.dispatch(tick(1));
            ok(a.events==1 && a.disables==1 &&
               registry.get(a.id()).state()==ModuleRegistry.State.DISABLED,
               "self-disable completes once before event throws "+i);
            ok(registry.get(a.id()).lastFault().isEmpty() &&
               registry.get(peer.id()).state()==ModuleRegistry.State.ENABLED &&
               peer.events==1,
               "completed self-disable is not retroactively faulted "+i);
            registry.dispatch(tick(2));
            ok(a.events==1 && a.disables==1 && peer.events==2,
               "later owner events skip disabled module "+i);
            registry.shutdown();
            ok(a.disables==1 && peer.disables==1,
               "shutdown cannot repeat already completed cleanup "+i);
        }
    }
    private static void shutdownThenThrow() {
        for(int i=0;i<1024;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Probe a=new Probe("event.close_then_fail"),peer=new Probe("event.peer");
            registry.register(a,0);registry.register(peer,0);
            registry.setEnabled(a.id(),true);registry.setEnabled(peer.id(),true);
            a.event=()->{
                registry.shutdown();
                throw new IllegalStateException("PRIVATE SHUTDOWN EXCEPTION");
            };
            registry.dispatch(tick(1));
            ok(a.disables==1 && peer.disables==1 &&
               a.events==1 && peer.events==0,
               "shutdown from active callback disables every module once "+i);
            ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED &&
               registry.get(peer.id()).state()==ModuleRegistry.State.DISABLED &&
               registry.get(a.id()).lastFault().isEmpty(),
               "late callback exception cannot re-fault already closed registry "+i);
            registry.shutdown();
            ok(a.disables==1 && peer.disables==1,
               "terminal shutdown remains idempotent "+i);
        }
    }
    private static void disableFaultThenThrow() {
        for(int i=0;i<512;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Probe a=new Probe("event.disable_fails"),peer=new Probe("event.peer");
            registry.register(a,0);registry.register(peer,0);
            registry.setEnabled(a.id(),true);registry.setEnabled(peer.id(),true);
            a.disable=()->{throw new IllegalStateException("SECRET CLEANUP MESSAGE");};
            a.event=()->{
                registry.setEnabled(a.id(),false);
                throw new IllegalArgumentException("SECRET LATE EVENT MESSAGE");
            };
            registry.dispatch(tick(1));
            ok(a.disables==1 && a.events==1 && peer.events==1,
               "failed disable never retried by outer event catcher "+i);
            ok(registry.get(a.id()).state()==ModuleRegistry.State.FAULTED &&
               "IllegalStateException".equals(registry.get(a.id()).lastFault()),
               "original cleanup failure remains authoritative "+i);
            registry.setEnabled(a.id(),false);
            ok(registry.get(a.id()).state()==ModuleRegistry.State.DISABLED &&
               registry.get(a.id()).lastFault().isEmpty(),
               "fault reset clears old error without second cleanup "+i);
            a.event=null;a.disable=null;
            registry.setEnabled(a.id(),true);
            registry.dispatch(tick(2));
            ok(a.events==2 && a.disables==1 && peer.events==2,
               "explicit retry works without double-cleanup "+i);
            registry.shutdown();
            ok(a.disables==2 && peer.disables==1,
               "healthy retry still receives normal shutdown cleanup "+i);
        }
    }
    private static void ordinaryEventFaultStillQuarantines() {
        for(int i=0;i<512;i++) {
            ModuleRegistry registry=new ModuleRegistry();
            Probe a=new Probe("event.normal_fault"),peer=new Probe("event.peer");
            registry.register(a,0);registry.register(peer,0);
            registry.setEnabled(a.id(),true);registry.setEnabled(peer.id(),true);
            a.event=()->{throw new IllegalArgumentException("SENSITIVE PLAYER DATA");};
            registry.dispatch(tick(1));
            ok(a.disables==1 && peer.disables==0 &&
               registry.get(a.id()).state()==ModuleRegistry.State.FAULTED,
               "still-enabled module is quarantined with exactly one cleanup "+i);
            ok("IllegalArgumentException".equals(registry.get(a.id()).lastFault()) &&
               peer.events==1,
               "healthy peer receives event; fault message sanitized "+i);
            registry.shutdown();
            ok(a.disables==1 && peer.disables==1,
               "shutdown only stops still-enabled healthy peer "+i);
        }
        // JVM Errors thrown after explicit disable are also not grounds
        // for a second onDisable; ordinary enabled-state Errors still fault.
        ModuleRegistry registry=new ModuleRegistry();
        Probe a=new Probe("event.error_after_disable");
        registry.register(a,0);registry.setEnabled(a.id(),true);
        a.event=()->{
            registry.setEnabled(a.id(),false);
            throw new AssertionError("PRIVATE ERROR");
        };
        registry.dispatch(tick(1));
        ok(a.disables==1 &&
           registry.get(a.id()).state()==ModuleRegistry.State.DISABLED,
           "post-disable JVM Error does not duplicate cleanup");
        registry.shutdown();
    }
    public static void main(String[] args) {
        selfDisableThenThrow();
        shutdownThenThrow();
        disableFaultThenThrow();
        ordinaryEventFaultStillQuarantines();
        ok(checks>10000,"substantial lifecycle post-fault replay");
        System.out.println("EVENT_POST_LIFECYCLE_FAULT_PASS assertions="+checks);
    }
}
