package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.host.ModuleActionTelemetry.Lane;
import dev.ghostfusion.host.ModuleActionTelemetry.Event;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.54: TriggerBot locally queued native keys and actual Forge attack
 * callbacks are independent, anonymous observations, never inferred hits.
 * Pure-Java tests do not claim a real Minecraft session occurred.
 */
public final class CombatNativeEvidenceTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++; if(!yes)throw new AssertionError(why);
    }
    private static void hostOptInAndLegacyBackup() throws Exception {
        Path root=Files.createTempDirectory("ghostc-r354-native-");
        HostRuntime h=new HostRuntime(root);
        h.worldPresence(true);
        ok(!h.diagnosticsEnabled(),"combat evidence disabled until F7 opt-in");
        ok(!h.nativeAction(Lane.TRIGGER,Event.DISPATCHED) &&
           h.nativeActionCount(Lane.TRIGGER,Event.DISPATCHED)==0,
           "TriggerBot local clicks not collected before opt-in");
        ok(h.diagnostics().count(Counter.COMBAT_NATIVE_ATTACK_CALLBACK)==0,
           "native callback must not be invented from a queued key");
        h.uiActions().toggle("hud.diagnostics");
        ok(h.diagnosticsEnabled(),"explicit F7 diagnostics activates combat evidence");
        ok(h.nativeAction(Lane.TRIGGER,Event.REJECTED) &&
           h.nativeAction(Lane.TRIGGER,Event.DISPATCHED) &&
           h.nativeAction(Lane.CLICKER,Event.DISPATCHED),
           "independent local native dispatch and rejection receipt lanes");
        ok(h.nativeActionCount(Lane.TRIGGER,Event.REJECTED)==1 &&
           h.nativeActionCount(Lane.TRIGGER,Event.DISPATCHED)==1 &&
           h.nativeActionCount(Lane.CLICKER,Event.DISPATCHED)==1,
           "TriggerBot and AutoClicker counts remain disjoint");
        ok(h.diagnostics().count(Counter.COMBAT_NATIVE_ATTACK_CALLBACK)==0,
           "neither dispatched key proves an AttackEntityEvent");
        // Simulate only the COUNTER recording that the native Forge event
        // adapter does after exact-world object verification; not an event.
        h.diagnostics().record(Counter.COMBAT_NATIVE_ATTACK_CALLBACK);
        h.diagnostics().record(Counter.COMBAT_AIM_TRIGGER_YIELD);
        h.diagnostics().record(Counter.COMBAT_AIM_MOUSE_YIELD);
        ok(h.diagnostics().count(Counter.ACTION_TRIGGER_DISPATCHED)==1 &&
           h.diagnostics().count(Counter.ACTION_TRIGGER_REJECTED)==1 &&
           h.diagnostics().count(Counter.ACTION_CLICKER_DISPATCHED)==1 &&
           h.diagnostics().count(Counter.COMBAT_NATIVE_ATTACK_CALLBACK)==1,
           "three strictly different event classes persist");
        ok("Action: CLICKER DISPATCHED".equals(h.nativeActionLatest()),
           "HUD last action describes local KeyBinding invocation, never hit");
        h.exportDiagnostics();
        Path primary=root.resolve("diagnostics-last.properties");
        Properties current=ConfigIntegrity.read(primary);
        ok("1".equals(current.getProperty("count.action_trigger_dispatched")) &&
           "1".equals(current.getProperty("count.action_trigger_rejected")) &&
           "1".equals(current.getProperty("count.combat_native_attack_callback")) &&
           "1".equals(current.getProperty("count.combat_aim_trigger_yield")) &&
           "1".equals(current.getProperty("count.combat_aim_mouse_yield")),
           "new anonymous diagnostic keys written as nonnegative integers");
        ok("counter-only-no-identity".equals(current.getProperty("scope")) &&
           !current.containsKey("player") && !current.containsKey("target") &&
           !current.containsKey("server") && !current.containsKey("uuid"),
           "no identities, coordinates or attack packets persisted");
        // An old valid schema-1 report missing all new action / combat keys
        // must STILL be backed up. It cannot be treated as corrupted.
        Properties legacy=new Properties();
        legacy.putAll(current);
        for(String key:current.stringPropertyNames()) {
            if(key.startsWith("count.action_trigger_") ||
               key.startsWith("count.combat_"))legacy.remove(key);
        }
        try(OutputStream out=Files.newOutputStream(primary)) {
            legacy.store(out,"pre-R3.54 valid old report");
        }
        h.exportDiagnostics();
        Path backup=root.resolve("diagnostics-last.properties.bak");
        ok(Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS) &&
           ConfigIntegrity.read(backup).equals(legacy),
           "schema1 valid older backup rotation preserved without weakening validation");
        Properties latest=ConfigIntegrity.read(primary);
        ok("1".equals(latest.getProperty("count.action_trigger_dispatched")) &&
           "1".equals(latest.getProperty("count.combat_native_attack_callback")),
           "current export still includes new proof categories");
        h.close();
    }
    private static void randomIndependentLanes() {
        ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        RuntimeDiagnostics diagnostics=new RuntimeDiagnostics();
        Random rng=new Random(354189L);
        int clicked=0,triggered=0,rejected=0,callbacks=0;
        for(int t=0;t<50000;t++) {
            int kind=rng.nextInt(5);
            if(kind==0) {
                ok(ledger.record(Lane.CLICKER,Event.DISPATCHED),
                   "real clicker local key dispatch can be separately recorded "+t);
                clicked++;
            } else if(kind==1) {
                ok(ledger.record(Lane.TRIGGER,Event.DISPATCHED),
                   "real TriggerBot local key dispatch can be separately recorded "+t);
                triggered++;
            } else if(kind==2) {
                ok(ledger.record(Lane.TRIGGER,Event.REJECTED),
                   "candidate veto not counted as native output "+t);
                rejected++;
            } else if(kind==3) {
                diagnostics.record(Counter.COMBAT_NATIVE_ATTACK_CALLBACK);
                callbacks++;
            } else {
                // A camera handoff is not a hit, a click, or an event callback.
                diagnostics.record(Counter.COMBAT_AIM_TRIGGER_YIELD);
            }
            ok(ledger.count(Lane.CLICKER,Event.DISPATCHED)==clicked &&
               ledger.count(Lane.TRIGGER,Event.DISPATCHED)==triggered &&
               ledger.count(Lane.TRIGGER,Event.REJECTED)==rejected &&
               diagnostics.count(Counter.COMBAT_NATIVE_ATTACK_CALLBACK)==callbacks,
               "native observations do not forge a synthetic-to-event link "+t);
            ok(diagnostics.count(Counter.COMBAT_AIM_TRIGGER_YIELD)==
                   (long)(t+1-clicked-triggered-rejected-callbacks),
               "camera handoff cannot be mistaken for a click or callback "+t);
        }
        ok(clicked>5000 && triggered>5000 && rejected>5000 && callbacks>5000,
           "all distinct native receipt classes exercised");
        AtomicBoolean refused=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{ledger.record(Lane.TRIGGER,Event.DISPATCHED);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        foreign.start();try{foreign.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(refused.get(),"foreign Netty thread denied owner ledger writes");
    }
    public static void main(String[] args) throws Exception {
        hostOptInAndLegacyBackup();
        randomIndependentLanes();
        ok(checks>100000,"50k deterministic independent native event observations");
        System.out.println("COMBAT_NATIVE_EVIDENCE_PASS assertions="+checks);
    }
}
