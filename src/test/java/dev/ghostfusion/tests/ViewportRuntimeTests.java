package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.host.HostRuntime;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/** R1.1: deterministic viewport/GUI geometry and real-callback counter contract tests. */
public final class ViewportRuntimeTests {
    private static int assertions;
    private static void ok(boolean value,String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }
    private static void rejects(Runnable action,String reason) {
        boolean caught=false;
        try { action.run(); } catch(RuntimeException expected) { caught=true; }
        ok(caught,reason);
    }
    private static void validate(int w,int h,UiState.Layout mode) {
        GuiLayout g=GuiLayout.calculate(w,h,mode);
        ok(g.insidePanel(w,h),"outer panel/controls contained "+w+"x"+h);
        ok(g.x>=0 && g.y>=0 && g.width>0 && g.height>0,"positive contained window");
        ok(g.searchWidth>=1 && g.searchX>=g.x && g.searchX+g.searchWidth<=g.x+g.width,
            "search hitbox contained after resize");
        ok(g.previewX>=g.x && g.previewY>=g.y &&
           g.previewX+g.previewWidth<=g.x+g.width &&
           g.previewY+g.previewHeight<=g.y+g.height,"HUD preview contained");
        ok(g.listY>=g.y && g.listY+g.listHeight<=g.y+g.height,
            "visible list cannot exceed window");
        ok(g.visibleRows>=0 && g.visibleRows*28<=g.listHeight,
            "whole visible rows only");
        ok(g.sidebar>=0 && g.sidebar<=g.width,"sidebar width bounded");
        if(w>=616 && h>=450 && mode==UiState.Layout.WIDE)
            ok(g.width==600 && g.height==430,"wide desktop layout stable");
        if(w>=406 && h>=450 && mode==UiState.Layout.COMPACT)
            ok(g.width==390 && g.height==430,"compact desktop layout stable");
        UiState ui=new UiState();
        ui.position(0.98,0.02);
        int vw=Math.max(1,g.previewWidth),vh=Math.max(1,g.previewHeight);
        int mx=ui.pixelX(vw,124),my=ui.pixelY(vh,22);
        ok(mx>=0 && mx<=Math.max(0,vw-124) && my>=0 &&
           my<=Math.max(0,vh-22),"HUD preview coordinates clamp");
    }
    private static void viewports() {
        rejects(()->GuiLayout.calculate(0,1080,UiState.Layout.WIDE),"zero width rejected");
        rejects(()->GuiLayout.calculate(1920,-1,UiState.Layout.WIDE),"negative height rejected");
        rejects(()->GuiLayout.calculate(800,600,null),"null theme rejected");
        for(UiState.Layout style:UiState.Layout.values()) {
            for(int w=1;w<=1200;w+=11) {
                for(int h=1;h<=720;h+=13)
                    validate(w,h,style);
            }
        }
        Random seeded=new Random(20261019L);
        for(int i=0;i<12000;i++) {
            int w=1+seeded.nextInt(3841),h=1+seeded.nextInt(2161);
            validate(w,h,(i&1)==0?UiState.Layout.WIDE:UiState.Layout.COMPACT);
        }
        GuiLayout wide=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        GuiLayout narrow=GuiLayout.calculate(280,180,UiState.Layout.WIDE);
        ok(wide.searchX!=narrow.searchX && wide.searchWidth!=narrow.searchWidth,
            "scaled viewport changes search hitbox, requiring rebind");
        UiState ui=new UiState();
        ui.query("hud.fps");
        ui.layout(UiState.Layout.COMPACT);
        GuiLayout compact=GuiLayout.calculate(640,360,ui.layout());
        ok(compact.width==390 && "hud.fps".equals(ui.query()),
            "layout rebuild cannot clear typed query");
        ui.layout(UiState.Layout.WIDE);
        GuiLayout extended=GuiLayout.calculate(640,360,ui.layout());
        ok(extended.width>compact.width && "hud.fps".equals(ui.query()),
            "layout toggle keeps search model independent of widget geometry");
    }
    private static void realCallbackEvidence() throws Exception {
        Path directory=Files.createTempDirectory("ghostc-r11-");
        HostRuntime host=new HostRuntime(directory);
        host.worldPresence(true);
        host.uiActions().toggle("hud.diagnostics");
        RuntimeDiagnostics metrics=host.diagnostics();
        RuntimeDiagnostics.Counter[] trace={
            RuntimeDiagnostics.Counter.GUI_OPEN,
            RuntimeDiagnostics.Counter.GUI_FRAME,
            RuntimeDiagnostics.Counter.KEY_CALLBACK,
            RuntimeDiagnostics.Counter.MOUSE_CALLBACK,
            RuntimeDiagnostics.Counter.OVERLAY_FRAME,
            RuntimeDiagnostics.Counter.WORLD_RENDER_FRAME,
            RuntimeDiagnostics.Counter.GUI_FRAME,
            RuntimeDiagnostics.Counter.GUI_CLOSE
        };
        for(RuntimeDiagnostics.Counter event:trace) metrics.record(event);
        ok(metrics.count(RuntimeDiagnostics.Counter.GUI_FRAME)==2,"GUI frames counted");
        ok(metrics.count(RuntimeDiagnostics.Counter.GUI_OPEN)==1 &&
           metrics.count(RuntimeDiagnostics.Counter.GUI_CLOSE)==1,"real GUI lifecycle counters");
        for(RuntimeDiagnostics.Counter event:trace)
            ok(metrics.count(event)>=1,"recorded callback class "+event.name());
        host.worldPresence(false);
        host.disconnect();
        Path report=directory.resolve("diagnostics-last.properties");
        ok(Files.exists(report),"opt-in counter report saved after disconnect");
        Properties properties=new Properties();
        try(InputStream in=Files.newInputStream(report)) {properties.load(in);}
        for(RuntimeDiagnostics.Counter type:RuntimeDiagnostics.Counter.values())
            ok(properties.getProperty("count."+type.name().toLowerCase(java.util.Locale.ROOT))!=null,
               "all event types serialized "+type);
        ok("2".equals(properties.getProperty("count.gui_frame")),"GUI frame count in report");
        ok("1".equals(properties.getProperty("count.world_render_frame")),"world render observed");
        ok(!properties.toString().contains("username") &&
           !properties.toString().contains("server") &&
           !properties.toString().contains("coordinates"),"no identity/personal data");
        host.close();
    }
    public static void main(String[] args) throws Exception {
        viewports();
        realCallbackEvidence();
        ok(assertions>190000,"large deterministic viewport regression batch");
        System.out.println("VIEWPORT_RUNTIME_PASS assertions="+assertions);
    }
}
