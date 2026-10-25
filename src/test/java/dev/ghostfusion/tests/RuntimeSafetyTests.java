package dev.ghostfusion.tests;

import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.io.InputStream;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic R0.8 physical-input, world-lifecycle and diagnostics checks. */
public final class RuntimeSafetyTests {
    private static int checks;
    private static void ok(boolean pass,String reason) {
        checks++;
        if (!pass) throw new AssertionError(reason);
    }
    private static void rejects(Runnable code,String why) {
        boolean refused=false;
        try { code.run(); } catch(RuntimeException expected) { refused=true; }
        ok(refused,why);
    }
    private static void input() throws Exception {
        PhysicalInputGate g=new PhysicalInputGate();
        // R0.8 first requires seeing the user release in a ready game.
        ok(!g.allowed(1,true,true,false,true,-100,-99),"initial held mouse never armed");
        ok(!g.allowed(1,true,true,false,false,-100,-99),"release unlocks but doesn't pulse");
        ok(g.allowed(1,true,true,false,true,-100,-99),"physical held left mouse now admitted");
        ok(!g.allowed(1,true,true,true,true,-100,-99),"GUI opening disarms");
        ok(!g.allowed(1,true,true,false,true,-100,-99),"closing GUI while held not rearmed");
        ok(!g.allowed(1,true,true,false,false,-100,-99),"fresh release after GUI");
        ok(g.allowed(1,true,true,false,true,-100,-99),"fresh click accepted");
        ok(!g.allowed(1,true,false,false,true,-100,-99),"alt-tab disarms");
        ok(!g.allowed(1,true,true,false,true,-100,-99),"alt-tab stale hold suppressed");
        ok(!g.allowed(1,true,true,false,false,-100,-99),"alt-tab requires fresh release");
        ok(g.allowed(1,true,true,false,true,-100,-99),"alt-tab recovered");
        ok(!g.allowed(2,true,true,false,true,-100,-99),"generation change revokes hold");
        ok(!g.allowed(2,true,true,false,false,-100,-99),"reconnect release");
        ok(g.allowed(2,true,true,false,true,-100,-99),"reconnect fresh press");
        ok(!g.allowed(2,true,true,false,true,0,-99),"unbound attack key blocked");
        ok(!g.allowed(2,true,true,false,true,65,-99),"reserved F7 blocked");
        ok(!g.allowed(2,true,true,false,true,-100,-100),"conflicting attack/use binding blocked");
        ok(!g.allowed(2,true,true,false,true,30,-99),
           "new keyboard attack binding requires release first");
        ok(!g.allowed(2,true,true,false,false,30,-99),
           "keyboard remap observes release in ready game");
        ok(g.allowed(2,true,true,false,true,30,-99),
           "keyboard attack bind supported after fresh press");
        ok(!g.allowed(2,false,true,false,true,-100,-99),"no player/world invalidates");
        ok(!g.allowed(2,true,true,false,true,-100,-99),"stale held after load blocked");
        g.reset();
        ok(!g.allowed(2,true,true,false,true,-100,-99),"explicit reset disarms");
        rejects(()->g.allowed(-1,true,true,false,true,-100,-99),"invalid generation");
        AtomicBoolean wrong=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try { g.allowed(2,true,true,false,true,-100,-99); }
            catch(IllegalStateException expected) { wrong.set(true); }
        });
        other.start(); other.join();
        ok(wrong.get(),"physical gate thread confinement");
        Random rnd=new Random(7182);
        for(int epoch=0;epoch<90;epoch++) {
            g.reset();
            boolean locked=true, held=false;
            for(int step=0;step<95;step++) {
                boolean ready=rnd.nextInt(10)!=0;
                boolean focus=rnd.nextInt(10)!=0;
                boolean gui=rnd.nextInt(8)==0;
                if(rnd.nextInt(5)==0)held=!held;
                if(!ready || !focus || gui) locked=true;
                else if(!held) locked=false;
                boolean expected=ready && focus && !gui && held && !locked;
                boolean actual=g.allowed(epoch,ready,focus,gui,held,-100,-99);
                ok(actual==expected,"random input replay "+epoch+"/"+step);
            }
        }
    }
    private static void diagnostics() throws Exception {
        RuntimeDiagnostics d=new RuntimeDiagnostics();
        for(RuntimeDiagnostics.Counter c:RuntimeDiagnostics.Counter.values())
            ok(d.count(c)==0,"counter initialized");
        d.record(RuntimeDiagnostics.Counter.FOCUS_LOST);
        d.record(RuntimeDiagnostics.Counter.CLIENT_TICK);
        d.record(RuntimeDiagnostics.Counter.CLIENT_TICK);
        ok(d.count(RuntimeDiagnostics.Counter.FOCUS_LOST)==1,"focus event");
        ok(d.count(RuntimeDiagnostics.Counter.CLIENT_TICK)==2,"tick events");
        Map<RuntimeDiagnostics.Counter,Long> snap=d.snapshot();
        rejects(()->snap.clear(),"immutable counter snapshot");
        d.record(RuntimeDiagnostics.Counter.FOCUS_LOST);
        ok(snap.get(RuntimeDiagnostics.Counter.FOCUS_LOST)==1,"snapshot does not change");
        Path dir=Files.createTempDirectory("ghostc-runtime-metrics");
        d.save(dir);
        Path report=dir.resolve("diagnostics-last.properties");
        ok(Files.exists(report),"report persisted");
        Properties props=new Properties();
        try(InputStream input=Files.newInputStream(report)) { props.load(input); }
        ok("1".equals(props.getProperty("schema")),"schema pinned");
        ok("counter-only-no-identity".equals(props.getProperty("scope")),"privacy scope pinned");
        ok("2".equals(props.getProperty("count.focus_lost")),"counts serialized");
        ok(props.size()==RuntimeDiagnostics.Counter.values().length+2,"no hidden identity fields");
        for(String key:props.stringPropertyNames()) {
            ok(key.equals("schema") || key.equals("scope") || key.startsWith("count."),
                "only safe telemetry field");
        }
        d.save(dir);
        ok(Files.exists(dir.resolve("diagnostics-last.properties.bak")),"report backup");
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread outsider=new Thread(()->{
            try {d.record(RuntimeDiagnostics.Counter.CLIENT_TICK);}
            catch(IllegalStateException expected) { refused.set(true); }
        });
        outsider.start(); outsider.join();
        ok(refused.get(),"diagnostics only on owner thread");
        rejects(()->d.record(null),"null counter rejected");
        rejects(()->d.count(null),"null counter read rejected");
    }
    private static void hostTransitions() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r08-host");
        HostRuntime h=new HostRuntime(dir);
        ok(!h.diagnosticsEnabled(),"diagnostics strictly opt-in");
        h.tick();
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.CLIENT_TICK)==1,"menu tick captured");
        h.disconnect();
        ok(h.services().generation()==0,"disconnect before first join idempotent");
        h.worldPresence(true);
        ok(h.services().generation()==1,"first join epoch");
        h.inputFocus(true);
        h.inputFocus(true);
        h.inputFocus(false);
        h.inputFocus(false);
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FOCUS_LOST)==1,"focus loss one-shot");
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.FOCUS_GAINED)==1,"focus gain one-shot");
        h.tick();
        h.worldPresence(false);
        ok(h.services().generation()==2,"world leave invalidates epoch");
        h.disconnect();
        ok(h.services().generation()==3,"network callback finishes disconnect once");
        h.disconnect();
        ok(h.services().generation()==3,"repeated callback ignored");
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.DISCONNECT)==1,"one terminal disconnect");
        ok(!Files.exists(dir.resolve("diagnostics-last.properties")),"no file without opt-in");
        h.worldPresence(true);
        h.uiActions().toggle("hud.diagnostics");
        ok(h.diagnosticsEnabled(),"diagnostic module enabled");
        h.tick();
        h.diagnostics().record(RuntimeDiagnostics.Counter.AIM_APPLIED);
        h.diagnostics().record(RuntimeDiagnostics.Counter.EXTRA_ATTACK);
        h.worldPresence(false);
        h.disconnect();
        ok(h.diagnostics().count(RuntimeDiagnostics.Counter.DISCONNECT)==2,"second session reported");
        ok(Files.exists(dir.resolve("diagnostics-last.properties")),"opt-in disconnect saved summary");
        Properties file=new Properties();
        try(InputStream in=Files.newInputStream(dir.resolve("diagnostics-last.properties"))) {
            file.load(in);
        }
        ok("2".equals(file.getProperty("count.disconnect")),"exported disconnect count");
        ok("1".equals(file.getProperty("count.extra_attack")),"exported supplemental attack count");
        h.close();
        ok(h.isClosed(),"host shutdown");
        ok(Files.exists(dir.resolve("diagnostics-last.properties.bak")),"shutdown report backup");
        HostRuntime resumed=new HostRuntime(dir);
        ok(resumed.diagnosticsEnabled(),"opt-in persistent");
        resumed.close();
    }
    public static void main(String[] args) throws Exception {
        input();diagnostics();hostTransitions();
        ok(checks>8500,"high-coverage seeded runtime input replay");
        System.out.println("RUNTIME_SAFETY_PASS assertions="+checks);
    }
}
