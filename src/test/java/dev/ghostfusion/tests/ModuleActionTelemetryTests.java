package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.host.ModuleActionTelemetry.Event;
import dev.ghostfusion.host.ModuleActionTelemetry.Lane;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** 60k deterministic mixed-module pending outcomes, no double completion,
 * opt-in/identity-free persisted reporting and old schema-1 migration.
 * No actual server ACK or Minecraft attack success is asserted here. */
public final class ModuleActionTelemetryTests {
    private static int checks;
    private static void ok(boolean result,String why) {
        checks++;
        if(!result)throw new AssertionError(why);
    }
    private static void elementary() {
        ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        for(Lane lane:Lane.values()) {
            ok(!ledger.pending(lane) && ledger.count(lane,Event.ATTEMPT)==0,
                "new ledger begins with no borrowed native action "+lane);
            ok(!ledger.record(lane,Event.LOCAL_CHANGE),
                "cannot infer successful local change without an attempt");
            ok(!ledger.record(lane,Event.TIMEOUT),
                "cannot infer timeout without an attempt");
            ok(!ledger.record(lane,Event.CANCELLED),
                "cannot infer cancelled operation without an attempt");
            ok(!ledger.record(lane,Event.LOCAL_COMPLETE),
                "cannot infer completed WTap without an attack attempt");
            ok(ledger.record(lane,Event.ATTEMPT) && ledger.pending(lane),
                "one admitted attempt begins pending lane");
            ok(!ledger.record(lane,Event.ATTEMPT),
                "duplicate native attempt never overwrites pending state");
            ok(ledger.record(lane,Event.LOCAL_CHANGE) && !ledger.pending(lane),
                "locally observed inventory change closes the matching lane");
            ok(!ledger.record(lane,Event.LOCAL_CHANGE) &&
               !ledger.record(lane,Event.TIMEOUT) &&
               !ledger.record(lane,Event.CANCELLED),
                "one attempt cannot be observed AND timed out AND cancelled");
            ok(ledger.record(lane,Event.ATTEMPT),
                "next independent native action can start after prior finish");
            ok(ledger.record(lane,Event.TIMEOUT),
                "unconfirmed source/target change eventually times out");
            ok(ledger.record(lane,Event.ATTEMPT) &&
               ledger.record(lane,Event.CANCELLED),
                "GUI/focus/world/Panic revokes pending native work");
            ok(ledger.record(lane,Event.REJECTED),
                "native preflight rejection explicitly counted");
            ok(ledger.record(lane,Event.DISPATCHED),
                "vanilla key/sprint API invocation explicitly counted");
            Map<Event,Long> snapshot=ledger.totals(lane);
            ok(snapshot.get(Event.ATTEMPT)==3 &&
               snapshot.get(Event.LOCAL_CHANGE)==1 &&
               snapshot.get(Event.TIMEOUT)==1 &&
               snapshot.get(Event.CANCELLED)==1 &&
               snapshot.get(Event.REJECTED)==1 &&
               snapshot.get(Event.DISPATCHED)==1,
               "distinct outcomes cannot be confused with server success");
            boolean immutable=false;
            try{snapshot.put(Event.ATTEMPT,9000L);}
            catch(UnsupportedOperationException expected){immutable=true;}
            ok(immutable,"action telemetry snapshot is readonly");
        }
        ModuleActionTelemetry w=new ModuleActionTelemetry();
        ok(w.record(Lane.WTAP,Event.ATTEMPT) &&
           w.record(Lane.WTAP,Event.DISPATCHED) &&
           w.record(Lane.WTAP,Event.DISPATCHED) &&
           w.record(Lane.WTAP,Event.LOCAL_COMPLETE),
           "WTap sprint OFF and ON are API dispatches; local lifecycle completion closes attempt");
        ok(!w.record(Lane.WTAP,Event.LOCAL_COMPLETE),
           "WTap RESTORE completion cannot be replayed twice");
        ok(w.latest().equals("Action: WTAP LOCAL_COMPLETE") &&
           w.latest().length()<72 && !w.latest().contains("@"),
           "last readout fixed vocabulary, never contains names/IP/packets");
        ok("Action: no observed native activity".equals(
           new ModuleActionTelemetry().latest()),
           "empty diagnostics never invent a successful action");
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try{w.record(Lane.CHEST,Event.ATTEMPT);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        foreign.start();
        try{foreign.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(refused.get(),"Netty/foreign threads cannot mutate owner telemetry");
        w.record(Lane.ARMOR,Event.ATTEMPT);
        w.cancelAll();
        ok(!w.pending(Lane.ARMOR) &&
           w.count(Lane.ARMOR,Event.CANCELLED)==1 &&
           w.count(Lane.WTAP,Event.CANCELLED)==0,
           "Panic cancellation affects only outstanding lane");
        w.cancelAll();
        ok(w.count(Lane.ARMOR,Event.CANCELLED)==1,
           "repeated world/GUI resets cannot double-count cancellation");
    }
    private static void randomized() {
        final ModuleActionTelemetry ledger=new ModuleActionTelemetry();
        final Random rng=new Random(3452027L);
        final EnumMap<Lane,Boolean> live=new EnumMap<Lane,Boolean>(Lane.class);
        final EnumMap<Lane,EnumMap<Event,Long>> expected=
            new EnumMap<Lane,EnumMap<Event,Long>>(Lane.class);
        for(Lane lane:Lane.values()) {
            live.put(lane,false);
            EnumMap<Event,Long> counts=new EnumMap<Event,Long>(Event.class);
            for(Event event:Event.values())counts.put(event,0L);
            expected.put(lane,counts);
        }
        for(int i=0;i<60000;i++) {
            Lane lane=Lane.values()[rng.nextInt(Lane.values().length)];
            Event event=Event.values()[rng.nextInt(Event.values().length)];
            boolean active=live.get(lane);
            boolean accepted=event==Event.ATTEMPT?!active:
                event==Event.LOCAL_CHANGE||event==Event.LOCAL_COMPLETE||
                event==Event.TIMEOUT||event==Event.CANCELLED?active:true;
            boolean recorded=ledger.record(lane,event);
            ok(recorded==accepted,"60k oracle: valid pending transition only");
            if(recorded) {
                if(event==Event.ATTEMPT)live.put(lane,true);
                else if(event==Event.LOCAL_CHANGE ||
                    event==Event.LOCAL_COMPLETE||event==Event.TIMEOUT||
                    event==Event.CANCELLED||event==Event.REJECTED)
                    live.put(lane,false);
                expected.get(lane).put(event,expected.get(lane).get(event)+1);
            }
            ok(ledger.pending(lane)==live.get(lane),
               "60k oracle: no leaked or double-ended lane ownership");
        }
        for(Lane lane:Lane.values()) {
            for(Event event:Event.values())
                ok(ledger.count(lane,event)==expected.get(lane).get(event),
                   "all outcomes counted once per resource lane");
        }
        ledger.cancelAll();
        for(Lane lane:Lane.values())
            ok(!ledger.pending(lane),"end session has zero pending native actions");
    }
    private static void hostOptInAndMigration() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r345-actions-");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        ok(!host.diagnosticsEnabled(),"no native telemetry collection without opt-in");
        ok(!host.nativeAction(Lane.CHEST,Event.ATTEMPT) &&
           host.nativeActionCount(Lane.CHEST,Event.ATTEMPT)==0,
           "disabled diagnostics cannot quietly capture actions");
        host.uiActions().toggle("hud.diagnostics");
        ok(host.diagnosticsEnabled(),"existing F7 Runtime Diagnostics enables telemetry");
        ok(host.nativeAction(Lane.CHEST,Event.ATTEMPT) &&
           !host.nativeAction(Lane.CHEST,Event.ATTEMPT) &&
           host.nativeAction(Lane.CHEST,Event.LOCAL_CHANGE),
           "one native source change per admitted transfer");
        ok(host.nativeAction(Lane.ARMOR,Event.ATTEMPT) &&
           host.nativeAction(Lane.ARMOR,Event.CANCELLED) &&
           host.nativeAction(Lane.WTAP,Event.ATTEMPT) &&
           host.nativeAction(Lane.WTAP,Event.DISPATCHED) &&
           host.nativeAction(Lane.WTAP,Event.LOCAL_COMPLETE),
           "armor cancellation and WTap state transition remain disjoint");
        ok(host.nativeAction(Lane.CLICKER,Event.DISPATCHED) &&
           host.nativeAction(Lane.BLOCKHIT,Event.DISPATCHED),
           "actual click/key dispatch is not interpreted as server hit");
        ok(host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_CHEST_ATTEMPT)==1 &&
           host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_CHEST_LOCAL_CHANGE)==1 &&
           host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_ARMOR_CANCELLED)==1 &&
           host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_WTAP_LOCAL_COMPLETE)==1 &&
           host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_CLICKER_DISPATCHED)==1 &&
           host.diagnostics().count(RuntimeDiagnostics.Counter.ACTION_BLOCKHIT_DISPATCHED)==1,
           "fixed anonymous RuntimeDiagnostics counters reflect only accepted events");
        ok(host.nativeActionLatest().equals("Action: BLOCKHIT DISPATCHED"),
           "HUD text uses only last real native API event classification");
        host.exportDiagnostics();
        Properties current=ConfigIntegrity.read(dir.resolve("diagnostics-last.properties"));
        ok("1".equals(current.getProperty("count.action_chest_attempt")) &&
           "1".equals(current.getProperty("count.action_wtap_local_complete")) &&
           "counter-only-no-identity".equals(current.getProperty("scope")),
           "anonymous persistent report stores accurate classified outcomes");
        for(String k:current.stringPropertyNames())
            if(k.startsWith("count.action_"))
                ok(current.getProperty(k).matches("[0-9]+"),
                   "action report contains only bounded numeric fields");
        // Emulate R3.44 report before Action_* counters existed.
        for(RuntimeDiagnostics.Counter counter:RuntimeDiagnostics.Counter.values())
            if(counter.name().startsWith("ACTION_"))
                current.remove("count."+counter.name().toLowerCase(java.util.Locale.ROOT));
        try(OutputStream out=Files.newOutputStream(dir.resolve("diagnostics-last.properties"))) {
            current.store(out,"legacy R3.44 report");
        }
        host.exportDiagnostics();
        Properties rotated=ConfigIntegrity.read(dir.resolve("diagnostics-last.properties.bak"));
        ok(rotated.equals(current),
           "pre-R3.45 schema1 diagnostics remains valid for safe backup rotation");
        host.close();
    }
    public static void main(String[] args) throws Exception {
        elementary();
        randomized();
        hostOptInAndMigration();
        ok(checks>120000,"60k random action events with per-lane model oracles");
        System.out.println("MODULE_ACTION_TELEMETRY_PASS assertions="+checks);
    }
}
