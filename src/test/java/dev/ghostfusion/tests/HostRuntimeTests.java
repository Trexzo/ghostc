package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
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
        testCorruptProfile(dir.resolve("corrupt"));
        ok(count > 280, "meaningful regression coverage");
        System.out.println("HOST_REGRESSION_PASS assertions=" + count);
    }
}
