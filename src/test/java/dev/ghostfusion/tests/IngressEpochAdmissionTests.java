package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

/** R3.31: no cross-thread event from an incomplete world epoch can be admitted. */
public final class IngressEpochAdmissionTests {
    private static int checks;
    private static void ok(boolean success,String reason) {
        checks++;
        if(!success)throw new AssertionError(reason);
    }
    private static final class Probe implements GhostModule {
        final HostRuntime host;
        final AtomicInteger transitionAttempts=new AtomicInteger();
        final AtomicInteger transitionAccepted=new AtomicInteger();
        volatile Throwable callbackFailure;
        int worldCallbacks, disconnectCallbacks, ticks;
        Probe(HostRuntime host){this.host=host;}
        public String id(){return "test.epoch_probe";}
        public String displayName(){return "Epoch probe";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event) {
            if(event.kind()==GameEvent.Kind.CLIENT_TICK) { ticks++; return; }
            if(event.kind()!=GameEvent.Kind.WORLD_CHANGE &&
               event.kind()!=GameEvent.Kind.SESSION_DISCONNECT)return;
            if(event.kind()==GameEvent.Kind.WORLD_CHANGE)worldCallbacks++;
            else disconnectCallbacks++;
            // Work starts from a separate producer while the owner is inside
            // the lifecycle callback. The owner does NOT hold the admission
            // lock while callbacks run, so joining must complete normally.
            Thread[] producers=new Thread[6];
            for(int i=0;i<producers.length;i++) {
                final GameEvent.Kind kind=(i%2==0)?
                    GameEvent.Kind.CLIENT_TICK:GameEvent.Kind.RENDER_2D;
                producers[i]=new Thread(()->{
                    try {
                        transitionAttempts.incrementAndGet();
                        if(host.enqueue(kind))transitionAccepted.incrementAndGet();
                    } catch(Throwable failure) {
                        callbackFailure=failure;
                    }
                },"r331-producer-"+i);
                producers[i].start();
            }
            for(Thread producer:producers) {
                try {
                    producer.join(3000);
                    if(producer.isAlive())
                        throw new AssertionError("Producer deadlocked behind lifecycle callback");
                } catch(Throwable failure) {
                    callbackFailure=failure;
                }
            }
        }
    }
    private static void transitionAdmission() throws Exception {
        for(int i=0;i<96;i++) {
            HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-r331-epoch"));
            Probe probe=new Probe(host);
            host.modules().register(probe,0);
            host.modules().setEnabled(probe.id(),true);
            host.worldPresence(true);
            ok(probe.worldCallbacks==1 && probe.transitionAttempts.get()==6,
                "join callbacks exercised six concurrent producers "+i);
            ok(probe.callbackFailure==null && probe.transitionAccepted.get()==0,
                "no publication accepted during world-enter callback "+i);
            for(int old=0;old<3;old++)
                ok(host.enqueue(GameEvent.Kind.CLIENT_TICK),
                    "live producer admission before disconnect "+i+"/"+old);
            host.disconnect();
            ok(probe.worldCallbacks==2 && probe.disconnectCallbacks==1,
                "leave and disconnect each dispatch exactly once "+i);
            ok(probe.transitionAttempts.get()==18 &&
               probe.transitionAccepted.get()==0 && probe.callbackFailure==null,
               "both disconnect lifecycle callbacks deny producer admission "+i);
            host.worldPresence(true);
            ok(probe.worldCallbacks==3 && probe.transitionAttempts.get()==24 &&
               probe.transitionAccepted.get()==0 && probe.callbackFailure==null,
               "re-enter callback remains fenced and nonblocking "+i);
            ok(host.enqueue(GameEvent.Kind.CLIENT_TICK),
                "new generation admits producer after lifecycle completion "+i);
            host.tick();
            ok(probe.ticks==2,
                "old queued ticks expire; only new producer and direct tick run "+i);
            ok(host.pendingEvents()==0,"bounded drain leaves no pending events "+i);
            host.close();
            ok(host.isClosed() && !host.enqueue(GameEvent.Kind.CLIENT_TICK),
                "close permanently refuses producer admission "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        transitionAdmission();
        ok(checks>1000,"substantial deterministic epoch admission assertions");
        System.out.println("INGRESS_EPOCH_ADMISSION_PASS assertions="+checks);
    }
}
