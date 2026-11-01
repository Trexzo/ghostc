package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import dev.ghostfusion.ui.HudWarningDrawGate;
import dev.ghostfusion.ui.HudWarningPlacement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.75 warning is readable despite right-anchored HUD, can retry a failed
 * renderer and is independent of native output, module lifecycle and
 * diagnostics export. All injected draw failures are local Java exceptions.
 */
public final class HudWarningRenderTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;
        if(!yes)throw new AssertionError(why);
    }
    private static void exact() throws Exception {
        ok(HudWarningPlacement.x(250,320,200)==0,
            "right anchored status cannot clip mandatory native warning");
        ok(HudWarningPlacement.x(25,320,200)==25,
            "normal left HUD anchor stays unchanged");
        ok(HudWarningPlacement.x(300,320,700)==0,
            "warning longer than viewport starts at left fallback");
        ok(HudWarningPlacement.x(-90,320,20)==0,
            "negative anchor never exposes offscreen warning");
        ok(HudWarningPlacement.x(Integer.MAX_VALUE,50,Integer.MAX_VALUE)==0,
            "overflow-safe warning fallback");
        ok(HudWarningPlacement.y(999,60,9)==50,
            "existing vertical safety clamp unchanged");
        HudWarningDrawGate guard=new HudWarningDrawGate();
        AtomicBoolean subsequentHud=new AtomicBoolean();
        ok(!guard.draw(new Runnable() {
            public void run() { throw new IllegalStateException("test-only render fault"); }
        }) && guard.failed(),"isolated draw failure recorded");
        subsequentHud.set(true);
        ok(subsequentHud.get() && guard.failed(),
            "non-warning HUD callbacks still proceed after failure");
        ok(guard.draw(new Runnable() {public void run() {}})
            && !guard.failed(),"next valid draw clears failed-painter status");
        ok(!guard.draw(new Runnable() {
            public void run() {throw new IllegalArgumentException("test-only");}
        }),"later independent failure is still isolated");
        guard.idle();
        ok(!guard.failed(),"absent warning resets stale draw-fault indicator");
        boolean denied=false;
        try {guard.draw(null);}catch(IllegalArgumentException expected){denied=true;}
        ok(denied,"null draw must fail explicitly");
        AtomicBoolean wrongThread=new AtomicBoolean();
        Thread worker=new Thread(new Runnable() {
            public void run() {
                try {guard.failed();}
                catch(IllegalStateException expected){wrongThread.set(true);}
            }
        });
        worker.start();worker.join();
        ok(wrongThread.get(),"warning status cannot cross owner-thread boundary");
        boolean serious=false;
        try {
            guard.draw(new Runnable() {
                public void run(){throw new AssertionError("JVM error");}
            });
        }catch(AssertionError expected){serious=true;}
        ok(serious,"JVM Error is not silently swallowed");
    }
    private static void reportAndLifecycle() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r375-export-");
        HostRuntime host=new HostRuntime(folder);
        host.worldPresence(true);
        host.modules().setEnabled("hud.diagnostics",true);
        host.nativePulseStatus(1);
        ok(host.nativePulseWarning().contains("ATTACK paused"),
            "uncertain native warning visible before exporting");
        host.diagnostics().record(Counter.COMBAT_WARNING_DRAW_FAILED);
        host.exportDiagnostics();
        Properties first=ConfigIntegrity.read(folder.resolve("diagnostics-last.properties"));
        ok("1".equals(first.getProperty("count.combat_pulse_attack_uncertain")) &&
           "1".equals(first.getProperty("count.combat_warning_draw_failed")) &&
           "counter-only-no-identity".equals(first.getProperty("scope")),
            "only opt-in aggregate fault edges exported");
        ok(host.nativePulseWarning().contains("ATTACK paused"),
            "report export cannot reset native output status");
        host.nativePulseStatus(3);
        host.diagnostics().record(Counter.COMBAT_WARNING_DRAW_RECOVERED);
        host.exportDiagnostics();
        Properties second=ConfigIntegrity.read(folder.resolve("diagnostics-last.properties"));
        Properties backup=ConfigIntegrity.read(folder.resolve("diagnostics-last.properties.bak"));
        ok("1".equals(second.getProperty("count.combat_pulse_use_uncertain")) &&
           "1".equals(second.getProperty("count.combat_warning_draw_recovered")) &&
           "0".equals(backup.getProperty("count.combat_pulse_use_uncertain")),
            "diagnostic backup rotation keeps earlier native warning evidence");
        ok(host.nativePulseWarning().contains("ATTACK + USE"),
            "saving and backup rotation never affect active safety warning");
        host.worldPresence(false);
        ok(host.nativePulseWarning().contains("ATTACK + USE"),
            "world-leave callback alone never clears warning");
        host.worldPresence(true);
        ok(host.nativePulseWarning().contains("ATTACK + USE"),
            "same host reenter never clears warning without native identity proof");
        host.nativePulseStatus(0);
        ok(host.nativePulseWarning().isEmpty(),
            "only a real observed recovery status clears warning");
        host.exportDiagnostics();
        Properties recovered=ConfigIntegrity.read(folder.resolve("diagnostics-last.properties"));
        ok("1".equals(recovered.getProperty("count.combat_pulse_recovered")),
            "recovery transition persisted as aggregate counter");
        host.close();
    }
    private static void randomized() {
        Random rng=new Random(3752026L);
        HudWarningDrawGate gate=new HudWarningDrawGate();
        boolean expectedFault=false;
        for(int i=0;i<90000;i++) {
            int viewport=1+rng.nextInt(2800);
            int desired=rng.nextInt(5000)-800;
            int message=rng.nextInt(4200);
            int shown=HudWarningPlacement.x(desired,viewport,message);
            long preferred=Math.max(0,desired);
            int oracle=preferred+2L+message<=viewport?(int)preferred:0;
            ok(shown==oracle,"90k independent offscreen-warning oracle "+i);
            ok(shown>=0 && shown<viewport,
                "warning start always within legal viewport "+i);
            final boolean fail=rng.nextInt(7)==0;
            boolean rendered=gate.draw(new Runnable() {
                public void run() {
                    if(fail)throw new IllegalStateException("injected draw");
                }
            });
            expectedFault=fail;
            ok(rendered!=fail && gate.failed()==expectedFault,
                "90k failed-draw state transitions remain bounded "+i);
        }
        ok(checks>=270000,"large deterministic warning-placement/render replay");
    }
    public static void main(String[] args) throws Exception {
        exact();reportAndLifecycle();randomized();
        System.out.println("HUD_WARNING_RENDER_PASS assertions="+checks);
    }
}
