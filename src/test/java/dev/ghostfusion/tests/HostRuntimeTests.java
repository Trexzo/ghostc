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
        testQueuedEventsAndThread(dir.resolve("queues"));
        testIngressSessionFence(dir.resolve("epoch"));
        testHostEventReentryFence(dir.resolve("host-reentry"));
        testCorruptProfile(dir.resolve("corrupt"));
        ok(count > 280, "meaningful regression coverage");
        System.out.println("HOST_REGRESSION_PASS assertions=" + count);
    }
}
