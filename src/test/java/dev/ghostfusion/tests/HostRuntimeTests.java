package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.BoundedIngress;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the same Minecraft-independent runtime control path as ForgeEventAdapter. */
public final class HostRuntimeTests {
    private static int count;
    private static void ok(boolean passes, String message) {
        count++;
        if (!passes) throw new AssertionError(message);
    }
    /** R3.05: repeated unbound keyboard traffic is inert, rebinding is live,
     * and genuine on/off changes still survive a restarted host profile. */
    private static void testLiveHotkeyRebinding(Path config) throws Exception {
        HostRuntime host=new HostRuntime(config);
        int old=HostRuntime.DEFAULT_STATUS_KEY;
        host.modules().bind("hud.status",45);
        ok(host.modules().boundToKey(old)==null &&
           host.modules().boundToKey(45)==host.modules().get("hud.status"),
           "new binding immediately visible to cached lookup");
        ModuleRegistry.State before=host.modules().get("hud.status").state();
        for(int i=0;i<18000;i++) {
            host.key(30,true,false);
            host.key(30,false,false);
        }
        ok(host.modules().get("hud.status").state()==before,
            "18k unrelated key pairs cannot toggle status");
        host.key(old,true,false);host.key(old,false,false);
        ok(host.modules().get("hud.status").state()==before,
            "previous binding no longer toggles status");
        host.key(45,true,false);host.key(45,false,false);
        ok(host.modules().get("hud.status").state()!=before,
            "new binding toggles once on a fresh edge");
        host.close();
        HostRuntime resumed=new HostRuntime(config);
        ok(resumed.modules().get("hud.status").keybind()==45 &&
           resumed.modules().get("hud.status").state()!=before,
           "hotkey and enabled state persisted across restart");
        resumed.close();
    }
    private static void checkDefaultsAndWorld(Path config) {
        HostRuntime host = new HostRuntime(config);
        ok(host.modules().get("hud.status").state() == ModuleRegistry.State.ENABLED, "default HUD enabled");
        ok(!host.render2d(), "no render in main menu");
        ok(!host.worldPresent(), "initially no world");
        host.worldPresence(true);
        ok(host.worldPresent(), "join activates world");
        ok(host.render2d(), "render enabled overlay in world");
        host.worldPresence(true);
        ok(host.render2d(), "duplicate world load is idempotent");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, true);
        ok(host.render2d(), "chat typing must suppress F8");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false);
        ok(host.render2d(), "focus transition while held cannot toggle");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, false, false);
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false);
        ok(!host.render2d(), "first normal press disables overlay");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false);
        ok(!host.render2d(), "held key does not double toggle");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, false, false);
        ok(Files.exists(config.resolve("default.properties")), "toggle persisted");
        host.worldPresence(false);
        ok(!host.worldPresent() && !host.render2d(), "world leave suppresses HUD");
        host.worldPresence(true);
        ok(!host.render2d(), "world rejoin retained module setting");
        host.disconnect();
        ok(!host.worldPresent(), "disconnect clears world");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false);
        ok(host.modules().get("hud.status").state() == ModuleRegistry.State.ENABLED, "new key edge after disconnect");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, false, false);
        host.close();
        ok(host.isClosed(), "host closes");
        ok(!host.render2d(), "closed host never renders");
        ok(!host.enqueue(GameEvent.Kind.CLIENT_TICK), "closed host rejects producer events");
    }
    private static void testPersistence(Path config) {
        HostRuntime host = new HostRuntime(config);
        ok(host.modules().get("hud.status").state() == ModuleRegistry.State.ENABLED, "profile loaded enabled setting");
        host.worldPresence(true);
        ok(host.render2d(), "saved state controls next session rendering");
        host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false);
        host.key(HostRuntime.DEFAULT_STATUS_KEY, false, false);
        ok(!host.render2d(), "second session toggles off");
        host.close();
        HostRuntime another = new HostRuntime(config);
        ok(another.modules().get("hud.status").state() == ModuleRegistry.State.DISABLED, "second session profile off");
        another.worldPresence(true);
        ok(!another.render2d(), "profile stays disabled across restart");
        another.close();
    }
    /** R3.08: concurrent producers cannot publish past the terminal
     * ingress fence, including during module onDisable callbacks. */
    private static void testIngressShutdownAdmissionFence(Path config) throws Exception {
        final HostRuntime host=new HostRuntime(config.resolve("host"));
        final java.util.concurrent.atomic.AtomicBoolean rejectedDuringDisable=
            new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean observedClosing=
            new java.util.concurrent.atomic.AtomicBoolean();
        GhostModule probe=new GhostModule() {
            public String id(){return "test.shutdown_ingress";}
            public String displayName(){return "Shutdown Ingress";}
            public Category category(){return Category.UTILITY;}
            public void onEnable(){}
            public void onDisable(){
                observedClosing.set(!host.isClosed());
                rejectedDuringDisable.set(!host.enqueue(GameEvent.Kind.CLIENT_TICK));
            }
            public void onEvent(GameEvent event){}
        };
        host.modules().register(probe,0);
        host.modules().setEnabled(probe.id(),true);
        ok(host.enqueue(GameEvent.Kind.CLIENT_TICK),
            "producer event accepted before shutdown");
        int before=host.pendingEvents();
        host.close();
        ok(host.isClosed() && observedClosing.get() &&
           rejectedDuringDisable.get(),
           "shutdown callback cannot enqueue while closed flag is not yet set");
        ok(host.pendingEvents()==before,
           "terminal callback cannot append a late producer event");
        ok(!host.enqueue(GameEvent.Kind.RENDER_2D),
           "no producer event admitted after shutdown");

        final BoundedIngress q=new BoundedIngress(65536);
        final java.util.concurrent.atomic.AtomicLong next=
            new java.util.concurrent.atomic.AtomicLong();
        final java.util.concurrent.atomic.AtomicInteger accepted=
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger denied=
            new java.util.concurrent.atomic.AtomicInteger();
        for(int i=0;i<19;i++) {
            ok(q.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,
                next.incrementAndGet())),"before-seal enqueue "+i);
            accepted.incrementAndGet();
        }
        final java.util.concurrent.CountDownLatch start=
            new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch ready=
            new java.util.concurrent.CountDownLatch(12);
        final java.util.concurrent.atomic.AtomicBoolean workerFailed=
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread[] workers=new Thread[12];
        for(int i=0;i<workers.length;i++) {
            workers[i]=new Thread(()->{
                ready.countDown();
                try {
                    start.await();
                    for(int j=0;j<1200;j++) {
                        GameEvent e=new GameEvent(GameEvent.Kind.CLIENT_TICK,
                            next.incrementAndGet());
                        if(q.submit(e))accepted.incrementAndGet();
                        else denied.incrementAndGet();
                    }
                }catch(Exception unexpected){workerFailed.set(true);}
            },"r308-ingress-producer-"+i);
            workers[i].start();
        }
        ready.await();
        start.countDown();
        q.seal();
        q.seal();
        for(Thread worker:workers)worker.join();
        ok(!workerFailed.get() && accepted.get()+denied.get()==14419,
           "12 producer races accounted without lost reservations");
        ok(q.pending()==accepted.get(),
           "sealed queue contains exactly completed pre-seal publications");
        ok(!q.submit(new GameEvent(GameEvent.Kind.CLIENT_TICK,
            next.incrementAndGet())) && q.rejected()==denied.get()+1,
            "all post-seal calls reject consistently and are counted");
        ModuleRegistry registry=new ModuleRegistry();
        final java.util.concurrent.atomic.AtomicInteger consumed=
            new java.util.concurrent.atomic.AtomicInteger();
        GhostModule collector=new GhostModule() {
            public String id(){return "test.sealed_collector";}
            public String displayName(){return "Sealed Collector";}
            public Category category(){return Category.UTILITY;}
            public void onEnable(){}
            public void onDisable(){}
            public void onEvent(GameEvent event){consumed.incrementAndGet();}
        };
        registry.register(collector,0);registry.setEnabled(collector.id(),true);
        ok(q.drain(registry,65536)==accepted.get() &&
           q.pending()==0 && consumed.get()==accepted.get(),
           "pre-seal accepted events remain drainable with no ghost slot");
        registry.shutdown();
    }
    private static void testQueuedEventsAndThread(Path config) throws Exception {
        HostRuntime host = new HostRuntime(config);
        for (int i = 0; i < 256; i++) ok(host.enqueue(GameEvent.Kind.CLIENT_TICK), "queue slot " + i);
        ok(!host.enqueue(GameEvent.Kind.CLIENT_TICK), "queue capacity enforced");
        ok(host.droppedEvents() == 1, "rejected metric increments");
        ok(host.pendingEvents() == 256, "queue accepted 256");
        host.tick();
        ok(host.pendingEvents() == 128, "per-tick budget of 128");
        host.tick();
        ok(host.pendingEvents() == 0, "queue eventually drained");
        final AtomicBoolean wrongThreadRejected = new AtomicBoolean(false);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try { host.key(HostRuntime.DEFAULT_STATUS_KEY, true, false); }
                catch (IllegalStateException e) { wrongThreadRejected.set(true); }
            }
        });
        thread.start();
        thread.join();
        ok(wrongThreadRejected.get(), "mutations require owner thread");
        host.close();
    }
    /** Probe receives only externally submitted render events, not tick,
     * world transition or disconnect signals dispatched by HostRuntime. */
    private static final class IngressProbe implements GhostModule {
        final java.util.List<Long> received = new java.util.ArrayList<Long>();
        public String id() { return "test.ingressprobe"; }
        public String displayName() { return "Ingress Probe"; }
        public Category category() { return Category.UTILITY; }
        public void onEnable() {}
        public void onDisable() {}
        public void onEvent(GameEvent event) {
            if(event.kind()==GameEvent.Kind.RENDER_2D)received.add(event.sequence());
        }
    }
    private static void testIngressSessionFence(Path config) {
        HostRuntime host=new HostRuntime(config);
        IngressProbe probe=new IngressProbe();
        host.modules().register(probe,0);
        host.modules().setEnabled(probe.id(),true);
        ok(host.enqueue(GameEvent.Kind.RENDER_2D),"menu-scope producer event accepted");
        host.worldPresence(true);
        host.tick();
        ok(probe.received.isEmpty(),"pre-join producer event not replayed into world");
        ok(host.pendingEvents()==0,"stale join event physically released");
        ok(host.enqueue(GameEvent.Kind.RENDER_2D),"current world producer event accepted");
        host.tick();
        ok(probe.received.size()==1,"same-world producer event delivered");
        host.enqueue(GameEvent.Kind.RENDER_2D);
        host.worldPresence(false);
        host.worldPresence(true);
        host.enqueue(GameEvent.Kind.RENDER_2D);
        host.tick();
        ok(probe.received.size()==2,"world replacement filters previous session");
        host.worldPresence(false);
        host.enqueue(GameEvent.Kind.RENDER_2D);
        host.disconnect();
        host.worldPresence(true);
        host.enqueue(GameEvent.Kind.RENDER_2D);
        host.tick();
        ok(probe.received.size()==3,"disconnect after world leave still fences ingress");

        for(int i=0;i<256;i++)
            ok(host.enqueue(GameEvent.Kind.RENDER_2D),"bounded old event "+i);
        host.worldPresence(false);
        host.worldPresence(true);
        host.tick();
        ok(host.pendingEvents()==128,"first owner tick consumes at most 128 stale events");
        host.tick();
        ok(host.pendingEvents()==0,"second owner tick clears remaining stale events");
        ok(probe.received.size()==3,"none of 256 queued old-world events delivered");
        host.enqueue(GameEvent.Kind.RENDER_2D);
        host.tick();
        ok(probe.received.size()==4,"fresh producer works after stale backlog");
        host.close();

        // Simulate a producer reserving an old sequence, then publishing it
        // AFTER a new session event: FIFO alone cannot identify the epoch.
        BoundedIngress queue=new BoundedIngress(4);
        dev.ghostfusion.core.ModuleRegistry registry=new dev.ghostfusion.core.ModuleRegistry();
        IngressProbe direct=new IngressProbe();
        registry.register(direct,0);registry.setEnabled(direct.id(),true);
        queue.submit(new GameEvent(GameEvent.Kind.RENDER_2D,101));
        queue.submit(new GameEvent(GameEvent.Kind.RENDER_2D,99));
        queue.submit(new GameEvent(GameEvent.Kind.RENDER_2D,102));
        ok(queue.drainAfter(registry,2,100)==1,
            "bounded drain counts stale and fresh polls against budget");
        ok(direct.received.size()==1 && direct.received.get(0)==101,
            "late-published old event filtered after a newer event");
        ok(queue.pending()==1,"budget preserves the third queued event");
        ok(queue.drainAfter(registry,2,100)==1 && direct.received.size()==2 &&
            direct.received.get(1)==102,"new epoch event delivered on next drain");
        ok(queue.drainAfter(registry,2,100)==0,"empty queue returns zero");
        ok(queue.drain(registry,2)==0,"legacy unconditional drain preserved");
        registry.shutdown();
    }
    private static final class HostReentryProbe implements GhostModule {
        final HostRuntime host;
        final java.util.EnumMap<GameEvent.Kind,Integer> seen=
            new java.util.EnumMap<GameEvent.Kind,Integer>(GameEvent.Kind.class);
        int blocked;
        HostReentryProbe(HostRuntime host){this.host=host;}
        public String id(){return "test.hostreentry";}
        public String displayName(){return "Host Reentry";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){
            if(host.isClosed())throw new AssertionError("closed too soon");
        }
        public void onEvent(GameEvent event) {
            Integer old=seen.get(event.kind());
            seen.put(event.kind(),old==null?1:old+1);
            long before=host.services().generation();
            long tick=host.services().tick();
            boolean present=host.worldPresent();
            Runnable[] nested={
                ()->host.worldPresence(!present),
                ()->host.worldPresence(present),
                host::disconnect,
                host::tick,
                ()->host.render2d(),
                host::close
            };
            for(Runnable callback:nested) {
                boolean refused=false;
                try{callback.run();}
                catch(IllegalStateException expected){refused=true;}
                ok(refused,"module callback denied nested host lifecycle "+event.kind());
                if(refused)blocked++;
            }
            ok(before==host.services().generation() &&
               tick==host.services().tick() &&
               present==host.worldPresent() && !host.isClosed(),
               "callback cannot mutate active session epoch or tick");
        }
        int count(GameEvent.Kind kind){Integer n=seen.get(kind);return n==null?0:n;}
    }
    private static void testHostEventReentryFence(Path config) throws Exception {
        HostRuntime host=new HostRuntime(config);
        HostReentryProbe probe=new HostReentryProbe(host);
        host.modules().register(probe,0);
        host.modules().setEnabled(probe.id(),true);
        long originalGeneration=host.services().generation();
        host.worldPresence(true);
        ok(host.worldPresent() &&
           host.services().generation()==originalGeneration+1 &&
           probe.count(GameEvent.Kind.WORLD_CHANGE)==1,
           "initial join commits exactly one lifecycle transition");
        for(int i=0;i<2500;i++) {
            ok(host.enqueue(GameEvent.Kind.RENDER_2D),
                "same-session producer is accepted "+i);
            host.tick();
        }
        ok(probe.count(GameEvent.Kind.RENDER_2D)==2500 &&
           probe.count(GameEvent.Kind.CLIENT_TICK)==2500 &&
           host.pendingEvents()==0,
           "2,500 bounded ingress batches dispatch each event exactly once");
        ok(host.services().generation()==originalGeneration+1 &&
           host.worldPresent() &&
           host.modules().get(probe.id()).state()==ModuleRegistry.State.ENABLED,
           "denied callbacks cannot invalidate the live world");
        // A direct host render also has a protected callback window.
        host.render2d();
        ok(probe.count(GameEvent.Kind.RENDER_2D)==2501,
            "direct render dispatched once without recursive render");
        long beforeLeave=host.services().generation();
        host.disconnect();
        ok(!host.worldPresent() &&
           host.services().generation()==beforeLeave+2 &&
           probe.count(GameEvent.Kind.WORLD_CHANGE)==2 &&
           probe.count(GameEvent.Kind.SESSION_DISCONNECT)==1,
           "disconnect emits one world leave and one disconnect epoch");
        host.disconnect();
        ok(probe.count(GameEvent.Kind.SESSION_DISCONNECT)==1,
            "repeated disconnect remains idempotent");
        host.worldPresence(true);
        ok(host.worldPresent() &&
           probe.count(GameEvent.Kind.WORLD_CHANGE)==3,
           "legitimate reconnect works after all callback guards released");
        host.tick();
        ok(probe.count(GameEvent.Kind.CLIENT_TICK)==2501,
            "client tick still works after reconnect");
        ok(probe.blocked==(probe.count(GameEvent.Kind.WORLD_CHANGE)+
            probe.count(GameEvent.Kind.SESSION_DISCONNECT)+
            probe.count(GameEvent.Kind.CLIENT_TICK)+
            probe.count(GameEvent.Kind.RENDER_2D))*6,
            "every nested lifecycle attempt was refused before mutation");
        host.close();
        ok(host.isClosed(),"ordinary owner close still succeeds");

        HostRuntime recovery=new HostRuntime(Files.createTempDirectory("ghostc-reentry-throw"));
        GhostModule throwing=new GhostModule(){
            public String id(){return "test.throws";}
            public String displayName(){return "Throw";}
            public Category category(){return Category.UTILITY;}
            public void onEnable(){}
            public void onDisable(){}
            public void onEvent(GameEvent event){throw new IllegalStateException("private");}
        };
        recovery.modules().register(throwing,0);
        recovery.modules().setEnabled(throwing.id(),true);
        recovery.worldPresence(true);
        ok(recovery.modules().get(throwing.id()).state()==ModuleRegistry.State.FAULTED,
            "host guard cleans up after failing event callback");
        recovery.worldPresence(false);
        recovery.worldPresence(true);
        recovery.tick();
        recovery.close();
        ok(recovery.isClosed(),"faulted module cannot leave host fence latched");
    }
    /** External producers cannot forge lifecycle notifications without the
     * matching owner-thread world, queue and service-generation transition. */
    private static void testProducerLifecycleAuthority(Path config) throws Exception {
        final HostRuntime host=new HostRuntime(config);
        final int[] observed=new int[GameEvent.Kind.values().length];
        final long[] greatestSequence={0L};
        GhostModule probe=new GhostModule() {
            public String id(){return "test.lifecycleingress";}
            public String displayName(){return "Lifecycle Ingress";}
            public Category category(){return Category.UTILITY;}
            public void onEnable(){}
            public void onDisable(){}
            public void onEvent(GameEvent event) {
                observed[event.kind().ordinal()]++;
                greatestSequence[0]=Math.max(greatestSequence[0],event.sequence());
            }
        };
        host.modules().register(probe,0);
        host.modules().setEnabled(probe.id(),true);
        long initial=host.services().generation();
        host.worldPresence(true);
        ok(host.worldPresent() && host.services().generation()==initial+1 &&
           observed[GameEvent.Kind.WORLD_CHANGE.ordinal()]==1,
           "legitimate owner join emits one lifecycle event and rotates epoch");
        java.util.concurrent.atomic.AtomicInteger workerDenied=
            new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean workerFailure=
            new java.util.concurrent.atomic.AtomicBoolean();
        Thread producer=new Thread(()->{
            for(int i=0;i<8000;i++) {
                for(GameEvent.Kind forbidden:new GameEvent.Kind[]{
                        GameEvent.Kind.WORLD_CHANGE,GameEvent.Kind.SESSION_DISCONNECT}) {
                    try {host.enqueue(forbidden);workerFailure.set(true);}
                    catch(IllegalArgumentException expected){workerDenied.incrementAndGet();}
                }
                if(i%80==0) {
                    if(!host.enqueue(GameEvent.Kind.RENDER_2D) ||
                       !host.enqueue(GameEvent.Kind.CLIENT_TICK))
                        workerFailure.set(true);
                }
            }
        },"ghostc-lifecycle-producer");
        producer.start();
        producer.join();
        ok(!workerFailure.get() && workerDenied.get()==16000,
            "worker rejects 16,000 forged lifecycle notifications but accepts real producer events");
        ok(host.pendingEvents()==200 && host.droppedEvents()==0,
            "lifecycle denials consume no ingress capacity or overflow counters");
        for(int i=0;i<2000;i++) {
            for(GameEvent.Kind forbidden:new GameEvent.Kind[]{
                    GameEvent.Kind.WORLD_CHANGE,GameEvent.Kind.SESSION_DISCONNECT}) {
                boolean denied=false;
                try {host.enqueue(forbidden);}
                catch(IllegalArgumentException expected){denied=true;}
                ok(denied,"owner enqueue also cannot spoof lifecycle semantics "+i);
            }
        }
        boolean nullDenied=false;
        try {host.enqueue(null);}catch(IllegalArgumentException expected){nullDenied=true;}
        ok(nullDenied,"null producer event remains rejected");
        ok(host.pendingEvents()==200 &&
           host.services().generation()==initial+1 &&
           observed[GameEvent.Kind.SESSION_DISCONNECT.ordinal()]==0,
           "forged owner and worker lifecycle events change no epoch or callbacks");
        host.tick();
        ok(host.pendingEvents()==72,"bounded first tick drains 128 real producer events");
        host.tick();
        ok(host.pendingEvents()==0 &&
           observed[GameEvent.Kind.RENDER_2D.ordinal()]==100 &&
           observed[GameEvent.Kind.CLIENT_TICK.ordinal()]==102 &&
           observed[GameEvent.Kind.WORLD_CHANGE.ordinal()]==1 &&
           observed[GameEvent.Kind.SESSION_DISCONNECT.ordinal()]==0,
           "ordinary worker events still deliver but no spoofed lifecycle is dispatched");
        ok(greatestSequence[0]==203,
            "forged lifecycle events consume no event sequence IDs");
        long beforeLeave=host.services().generation();
        host.worldPresence(false);
        host.disconnect();
        ok(!host.worldPresent() &&
           host.services().generation()==beforeLeave+2 &&
           observed[GameEvent.Kind.WORLD_CHANGE.ordinal()]==2 &&
           observed[GameEvent.Kind.SESSION_DISCONNECT.ordinal()]==1,
           "real leave/disconnect still perform exact ordered epoch transitions");
        host.worldPresence(true);
        ok(host.worldPresent() &&
           observed[GameEvent.Kind.WORLD_CHANGE.ordinal()]==3,
           "real reconnect still emits one owner lifecycle notification");
        host.close();
        ok(host.isClosed() && !host.enqueue(GameEvent.Kind.CLIENT_TICK),
            "closed host still declines valid asynchronous producer events");
    }
    /** R2.92: producer contention at MAX_VALUE never admits a wrapped ID;
     * owner callbacks fail before partial ticks/world transitions, and a
     * disconnect reserves its leave/disconnect notifications as one unit. */
    private static void testEventSequenceOverflow(Path config) throws Exception {
        final HostRuntime producer=new HostRuntime(config.resolve("producer"));
        producer.worldPresence(true);
        java.lang.reflect.Field sequenceField=HostRuntime.class.getDeclaredField("sequence");
        sequenceField.setAccessible(true);
        final java.util.concurrent.atomic.AtomicLong sequence=
            (java.util.concurrent.atomic.AtomicLong)sequenceField.get(producer);
        sequence.set(Long.MAX_VALUE-2);
        final java.util.concurrent.atomic.AtomicInteger accepted=
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger overflow=
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger unexpected=
            new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.CountDownLatch start=
            new java.util.concurrent.CountDownLatch(1);
        Thread[] workers=new Thread[48];
        for(int i=0;i<workers.length;i++) {
            workers[i]=new Thread(()->{
                try {
                    start.await();
                    if(producer.enqueue(GameEvent.Kind.RENDER_2D))accepted.incrementAndGet();
                    else unexpected.incrementAndGet();
                } catch(IllegalStateException expected) {
                    overflow.incrementAndGet();
                } catch(Exception failure) {
                    unexpected.incrementAndGet();
                }
            },"ghostc-sequence-producer-"+i);
            workers[i].start();
        }
        start.countDown();
        for(Thread worker:workers)worker.join();
        ok(accepted.get()==2 && overflow.get()==46 && unexpected.get()==0,
            "only two final positive event IDs admitted under worker contention");
        ok(sequence.get()==Long.MAX_VALUE && producer.pendingEvents()==2 &&
           producer.droppedEvents()==0,
            "overflow denials preserve counter, accepted queue and capacity metrics");
        long beforeTick=producer.services().tick();
        long beforeGeneration=producer.services().generation();
        boolean tickDenied=false, leaveDenied=false, disconnectDenied=false, renderDenied=false;
        try{producer.tick();}catch(IllegalStateException expected){tickDenied=true;}
        try{producer.worldPresence(false);}catch(IllegalStateException expected){leaveDenied=true;}
        try{producer.disconnect();}catch(IllegalStateException expected){disconnectDenied=true;}
        try{producer.render2d();}catch(IllegalStateException expected){renderDenied=true;}
        ok(tickDenied && leaveDenied && disconnectDenied && renderDenied,
            "exhausted owner event paths deny without allocating a wrapped event");
        ok(producer.services().tick()==beforeTick &&
           producer.services().generation()==beforeGeneration &&
           producer.worldPresent() && producer.pendingEvents()==2 &&
           sequence.get()==Long.MAX_VALUE,
            "failed owner operations cannot advance ticks, alter world or drain ingress");
        producer.close();
        ok(producer.isClosed(),"host can close safely with an exhausted event counter");

        HostRuntime paired=new HostRuntime(config.resolve("paired"));
        paired.worldPresence(true);
        java.util.concurrent.atomic.AtomicLong pairedIds=
            (java.util.concurrent.atomic.AtomicLong)sequenceField.get(paired);
        long generation=paired.services().generation();
        pairedIds.set(Long.MAX_VALUE-1);
        boolean pairDenied=false;
        try{paired.disconnect();}catch(IllegalStateException expected){pairDenied=true;}
        ok(pairDenied && pairedIds.get()==Long.MAX_VALUE-1 &&
           paired.worldPresent() && paired.services().generation()==generation,
            "only one remaining ID refuses two-event disconnect transactionally");
        pairedIds.set(Long.MAX_VALUE-2);
        paired.disconnect();
        ok(pairedIds.get()==Long.MAX_VALUE && !paired.worldPresent() &&
           !paired.services().connected() &&
           paired.services().generation()==generation+2,
            "last two IDs permit exact ordered world-leave and disconnect");
        paired.close();

        HostRuntime single=new HostRuntime(config.resolve("single"));
        single.worldPresence(true);
        java.util.concurrent.atomic.AtomicLong singleIds=
            (java.util.concurrent.atomic.AtomicLong)sequenceField.get(single);
        singleIds.set(Long.MAX_VALUE-1);
        single.worldPresence(false);
        ok(singleIds.get()==Long.MAX_VALUE && !single.worldPresent(),
            "single owner world notification can use the final positive ID");
        single.close();
    }
    private static void testCorruptProfile(Path config) throws Exception {
        Path file = config.resolve("default.properties");
        Files.createDirectories(config);
        Files.write(file, "schema=1\nmodule.hud.status.enabled=true\nmodule.hud.status.key=999999\n".getBytes("UTF-8"));
        HostRuntime host = new HostRuntime(config);
        ok(!host.profileWarning().isEmpty(), "corruption made visible to host");
        ok(host.modules().get("hud.status").state() == ModuleRegistry.State.ENABLED, "fallback remains usable");
        host.close();
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ghostc-host-tests-");
        checkDefaultsAndWorld(dir.resolve("sessions"));
        testPersistence(dir.resolve("sessions"));
        testLiveHotkeyRebinding(dir.resolve("hotkey-rebind"));
        testQueuedEventsAndThread(dir.resolve("queues"));
        testIngressShutdownAdmissionFence(dir.resolve("shutdown-ingress"));
        testIngressSessionFence(dir.resolve("epoch"));
        testHostEventReentryFence(dir.resolve("host-reentry"));
        testProducerLifecycleAuthority(dir.resolve("producer-lifecycle"));
        testEventSequenceOverflow(dir.resolve("sequence-overflow"));
        testCorruptProfile(dir.resolve("corrupt"));
        ok(count > 280, "meaningful regression coverage");
        System.out.println("HOST_REGRESSION_PASS assertions=" + count);
    }
}
