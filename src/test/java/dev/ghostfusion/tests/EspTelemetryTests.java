package dev.ghostfusion.tests;

import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/** R2.8 acceptance counters: opt-in persistence, privacy and owner fencing. */
public final class EspTelemetryTests {
    private static int assertions;
    private static void ok(boolean pass,String message) {
        assertions++;
        if(!pass)throw new AssertionError(message);
    }
    private static void tests() throws Exception {
        RuntimeDiagnostics diag=new RuntimeDiagnostics();
        ok(diag.count(Counter.ESP_RENDER_FRAME)==0,"new render frame count zero");
        ok(diag.count(Counter.ESP_DRAWN_PLAYER)==0,"new player count zero");
        ok(diag.count(Counter.ESP_DRAWN_SKELETON)==0,"new skeleton count zero");
        ok(diag.count(Counter.ESP_INVALID_GEOMETRY)==0,"new rejection count zero");
        diag.record(Counter.ESP_RENDER_FRAME);
        diag.record(Counter.ESP_DRAWN_PLAYER);
        diag.record(Counter.ESP_DRAWN_PLAYER);
        diag.record(Counter.ESP_DRAWN_SKELETON);
        diag.record(Counter.ESP_INVALID_GEOMETRY);
        ok(diag.count(Counter.ESP_RENDER_FRAME)==1,"frame counts draw submissions");
        ok(diag.count(Counter.ESP_DRAWN_PLAYER)==2,"player draw count increments");
        ok(diag.count(Counter.ESP_DRAWN_SKELETON)==1,"skeleton draw count increments");
        ok(diag.count(Counter.ESP_INVALID_GEOMETRY)==1,"rejects counted separately");
        Map<Counter,Long> snapshot=diag.snapshot();
        diag.record(Counter.ESP_RENDER_FRAME);
        ok(snapshot.get(Counter.ESP_RENDER_FRAME)==1L,"snapshot immutable historical values");
        boolean blocked=false;
        try{snapshot.put(Counter.ESP_RENDER_FRAME,999L);}
        catch(UnsupportedOperationException e){blocked=true;}
        ok(blocked,"snapshot returned unmodifiable");
        java.util.concurrent.atomic.AtomicBoolean offThreadBlocked=
            new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread t=new Thread(new Runnable(){
            public void run() {
                try{diag.record(Counter.ESP_RENDER_FRAME);}
                catch(IllegalStateException expected){offThreadBlocked.set(true);}
            }
        });
        t.start();t.join();
        ok(offThreadBlocked.get(),"render counters are owner thread fenced");
        Path folder=Files.createTempDirectory("ghostc-r28-esp-metrics");
        ok(!Files.exists(folder.resolve("diagnostics-last.properties")),
            "diagnostics not written until explicit save");
        diag.save(folder);
        Path properties=folder.resolve("diagnostics-last.properties");
        ok(Files.exists(properties),"opt-in save persisted counters");
        Properties values=new Properties();
        try(java.io.InputStream stream=Files.newInputStream(properties)) {values.load(stream);}
        ok("counter-only-no-identity".equals(values.getProperty("scope")),
            "saved telemetry discloses no session identity");
        ok("2".equals(values.getProperty("count.esp_render_frame")),
            "saved number of ESP frame submissions");
        ok("2".equals(values.getProperty("count.esp_drawn_player")),
            "saved number of player draw submissions");
        ok("1".equals(values.getProperty("count.esp_drawn_skeleton")),
            "saved number of skeleton draw submissions");
        ok("1".equals(values.getProperty("count.esp_invalid_geometry")),
            "saved number of invalid render geometry skips");
        for(String name:values.stringPropertyNames()) {
            ok("schema".equals(name)||"scope".equals(name)||name.startsWith("count."),
                "only schema/scope/counters are recorded");
            ok(!name.contains("playername")&&!name.contains("server")&&!name.contains("address"),
                "no account/network identifiers in keys");
        }
        diag.save(folder);
        ok(Files.exists(folder.resolve("diagnostics-last.properties.bak")),
            "second optional save preserves earlier counters");
    }
    public static void main(String[] ignored) throws Exception {
        tests();
        ok(assertions>=60,"complete ESP acceptance counter regression");
        System.out.println("ESP_TELEMETRY_PASS assertions="+assertions);
    }
}
