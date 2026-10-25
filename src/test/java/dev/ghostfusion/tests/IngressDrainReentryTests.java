package dev.ghostfusion.tests;

import dev.ghostfusion.core.BoundedIngress;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** R3.25: no recursive/concurrent ingress consumer may poll another event. */
public final class IngressDrainReentryTests {
    private static int checks;
    private static void ok(boolean valid,String why) {
        checks++;if(!valid)throw new AssertionError(why);
    }
    private abstract static class Probe implements GhostModule {
        final String stableId;
        Probe(String id){stableId=id;}
        public String id(){return stableId;}
        public String displayName(){return "Ingress probe";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
    }
    private static void recursiveDrainKeepsExactFifo() {
        for(int round=0;round<2048;round++) {
            final boolean legacy=(round&1)==0;
            final BoundedIngress queue=new BoundedIngress(4);
            final ModuleRegistry registry=new ModuleRegistry();
            final List<Long> observed=new ArrayList<Long>();
            final boolean[] blocked={false},countUnchanged={false};
            final int[] callbacks={0};
            Probe reentrant=new Probe("test.drain_recursive") {
                public void onEvent(GameEvent event) {
                    callbacks[0]++;
                    if(callbacks[0]!=1)return;
                    int before=queue.pending();
                    try {
                        if(legacy)queue.drain(registry,1);
                        else queue.drainAfter(registry,1,-1L);
                    } catch(IllegalStateException expected) {
                        blocked[0]=true;
                    }
                    countUnchanged[0]=queue.pending()==before;
                }
            };
            Probe healthy=new Probe("test.drain_healthy") {
                public void onEvent(GameEvent event){observed.add(event.sequence());}
            };
            registry.register(reentrant,0);
            registry.register(healthy,0);
            registry.setEnabled(reentrant.id(),true);
            registry.setEnabled(healthy.id(),true);
            for(int i=1;i<=3;i++)
                ok(queue.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,i)),
                   "fixture event admitted "+round+"/"+i);
            int delivered=queue.drainAfter(registry,3,0);
            ok(blocked[0] && countUnchanged[0],
               "recursive alias rejected before stealing event "+round);
            ok(delivered==3 && queue.pending()==0 && callbacks[0]==3 &&
               observed.size()==3,
               "outer drain delivers all three events despite nested attempt "+round);
            ok(observed.get(0)==1 && observed.get(1)==2 && observed.get(2)==3,
               "original FIFO and exact sequence preserved "+round);
            ok(registry.get(reentrant.id()).state()==ModuleRegistry.State.ENABLED &&
               registry.get(healthy.id()).state()==ModuleRegistry.State.ENABLED,
               "reentry does not fault either registered module "+round);
            ok(queue.rejected()==0 &&
               queue.drainAfter(registry,1,0)==0,
               "no spurious rejection or stranded reentry latch "+round);
            registry.shutdown();
        }
    }
    /** An independent consumer cannot steal a slot even if it has its own
     * registry and valid owner thread. No sleep-based race assumptions. */
    private static void concurrentConsumersCannotSteal() throws Exception {
        BoundedIngress queue=new BoundedIngress(3);
        CountDownLatch entered=new CountDownLatch(1), finished=new CountDownLatch(1);
        AtomicReference<Throwable> workerError=new AtomicReference<Throwable>();
        AtomicBoolean refused=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try {
                if(!entered.await(10,TimeUnit.SECONDS))
                    throw new AssertionError("owner callback did not start");
                ModuleRegistry other=new ModuleRegistry();
                try {
                    queue.drain(other,1);
                } catch(IllegalStateException expected) {
                    refused.set(true);
                }
                other.shutdown();
            } catch(Throwable ex) {workerError.set(ex);}
            finally {finished.countDown();}
        },"ingress-second-owner");
        ModuleRegistry registry=new ModuleRegistry();
        List<Long> received=new ArrayList<Long>();
        Probe blocker=new Probe("test.ingress_blocker") {
            public void onEvent(GameEvent e) {
                received.add(e.sequence());
                if(e.sequence()==1) {
                    entered.countDown();
                    try {
                        if(!finished.await(10,TimeUnit.SECONDS))
                            throw new AssertionError("second owner failed to finish");
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                }
            }
        };
        registry.register(blocker,0);
        registry.setEnabled(blocker.id(),true);
        queue.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        queue.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,2));
        worker.start();
        int delivered=queue.drain(registry,2);
        worker.join();
        ok(workerError.get()==null && refused.get(),
           "second registry owner refused before dequeue");
        ok(delivered==2 && queue.pending()==0 && received.size()==2 &&
           received.get(0)==1 && received.get(1)==2,
           "cross-thread contender cannot steal original FIFO event");
        ok(queue.rejected()==0,"consumer contention is not producer rejection");
        registry.shutdown();
    }
    /** A failed dispatch must release the drain guard. Pending later
     * events remain recoverable by another healthy owner registry. */
    private static void exceptionalDispatchReleasesGate() {
        BoundedIngress queue=new BoundedIngress(3);
        ModuleRegistry shutdown=new ModuleRegistry();
        shutdown.shutdown();
        queue.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,1));
        queue.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,2));
        boolean refused=false;
        try {queue.drain(shutdown,1);}
        catch(IllegalStateException expected){refused=true;}
        ok(refused && queue.pending()==1,
           "failed dispatch consumes only its attempted event");
        ModuleRegistry fresh=new ModuleRegistry();
        List<Long> events=new ArrayList<Long>();
        Probe witness=new Probe("test.ingress_recovered") {
            public void onEvent(GameEvent e){events.add(e.sequence());}
        };
        fresh.register(witness,0);
        fresh.setEnabled(witness.id(),true);
        ok(queue.drain(fresh,3)==1 && queue.pending()==0 &&
           events.size()==1 && events.get(0)==2,
           "finally released gate and preserved subsequent queued event");
        fresh.shutdown();
    }
    public static void main(String[] args) throws Exception {
        recursiveDrainKeepsExactFifo();
        concurrentConsumersCannotSteal();
        exceptionalDispatchReleasesGate();
        ok(checks>11000,"bounded queue reentry stress coverage");
        System.out.println("INGRESS_DRAIN_REENTRY_PASS assertions="+checks);
    }
}
