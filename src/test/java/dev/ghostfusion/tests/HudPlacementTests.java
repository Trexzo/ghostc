package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.ui.UiSettingsStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;

/** R1.9: independent draggable HUD anchors and schema-1 migration/atomicity. */
public final class HudPlacementTests {
    private static int checks;
    private static void ok(boolean yes,String detail) {
        checks++;
        if (!yes) throw new AssertionError(detail);
    }
    private static void rejects(Runnable action,String detail) {
        boolean refused=false;
        try {action.run();} catch(RuntimeException expected){refused=true;}
        ok(refused,detail);
    }
    private static void anchors() {
        UiState state=new UiState();
        HudPositions anchors=state.widgets();
        ok(state.hudX()==0.025 && state.hudY()==0.055,"legacy status HUD untouched");
        HudPositions.Widget[] names=HudPositions.Widget.values();
        ok(names.length==4,"four independent added widgets including Module List");
        for(HudPositions.Widget name:names) {
            ok(anchors.x(name)>=0&&anchors.x(name)<=1&&
                anchors.y(name)>=0&&anchors.y(name)<=1,"default anchor valid");
            ok(name.width>0&&name.height>0,"positive dimensions");
            ok(HudPositions.property(name,true).startsWith("hud."),"stable property name");
        }
        anchors.position(HudPositions.Widget.KEYSTROKES,0.14,0.21);
        anchors.position(HudPositions.Widget.CPS,0.72,0.31);
        anchors.position(HudPositions.Widget.TARGET,0.45,0.81);
        ok(anchors.x(HudPositions.Widget.KEYSTROKES)==0.14,"keys independently movable");
        ok(anchors.x(HudPositions.Widget.CPS)==0.72,"CPS independently movable");
        ok(anchors.y(HudPositions.Widget.TARGET)==0.81,"target independently movable");
        ok(state.hudX()==0.025,"status not changed by other drags");
        rejects(()->anchors.position(HudPositions.Widget.CPS,Double.NaN,0.8),
            "nonfinite x rejected");
        ok(anchors.x(HudPositions.Widget.CPS)==0.72 &&
            anchors.y(HudPositions.Widget.CPS)==0.31,
            "invalid x does not mutate any value");
        rejects(()->anchors.position(HudPositions.Widget.CPS,0.3,Double.POSITIVE_INFINITY),
            "nonfinite y rejected");
        ok(anchors.x(HudPositions.Widget.CPS)==0.72 &&
            anchors.y(HudPositions.Widget.CPS)==0.31,
            "invalid y atomic");
        rejects(()->anchors.positionPixels(HudPositions.Widget.CPS,0,0,0,20,100,15),
            "zero viewport rejected");
        rejects(()->anchors.pixelX(null,300,100),"null widget rejected");
        Random seeded=new Random(19192026L);
        for(int i=0;i<12000;i++) {
            int w=1+seeded.nextInt(2700),h=1+seeded.nextInt(1600);
            for(HudPositions.Widget widget:names) {
                int x=seeded.nextInt(w+1000)-500,y=seeded.nextInt(h+1000)-500;
                anchors.positionPixels(widget,x,y,w,h,widget.width,widget.height);
                int px=anchors.pixelX(widget,w,widget.width);
                int py=anchors.pixelY(widget,h,widget.height);
                ok(px>=0&&px<=Math.max(0,w-widget.width),"pixel x clamped "+i);
                ok(py>=0&&py<=Math.max(0,h-widget.height),"pixel y clamped "+i);
                ok(anchors.x(widget)>=0&&anchors.x(widget)<=1 &&
                    anchors.y(widget)>=0&&anchors.y(widget)<=1,
                    "normalized position bounded "+i);
            }
        }
    }
    private static Properties read(Path file) throws Exception {
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(file)){p.load(in);}
        return p;
    }
    private static void write(Path file,Properties p) throws Exception {
        try(OutputStream out=Files.newOutputStream(file)){p.store(out,"R1.9 test");}
    }
    private static void persistence() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-multi-hud-");
        UiState state=new UiState();
        UiSettingsStore store=new UiSettingsStore(folder,state);
        state.layout(UiState.Layout.COMPACT);
        state.theme(UiState.Theme.GRAPHITE);
        state.position(0.3,0.4);
        for(HudPositions.Widget widget:HudPositions.Widget.values())
            state.widgets().position(widget,0.15+widget.ordinal()*0.1,
                0.25+widget.ordinal()*0.1);
        store.save();
        Properties properties=read(folder.resolve("ui.properties"));
        ok("1".equals(properties.getProperty("schema")),"schema preserved");
        ok(properties.containsKey("hud.x")&&properties.containsKey("hud.y"),
            "legacy status properties preserved");
        for(HudPositions.Widget widget:HudPositions.Widget.values())
            ok(properties.containsKey(HudPositions.property(widget,true)) &&
                properties.containsKey(HudPositions.property(widget,false)),
                "widget persisted "+widget);
        UiState restored=new UiState();
        new UiSettingsStore(folder,restored).load();
        ok(restored.layout()==UiState.Layout.COMPACT &&
            restored.theme()==UiState.Theme.GRAPHITE,"style persists");
        ok(restored.hudX()==0.3&&restored.hudY()==0.4,"status anchor persists");
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            ok(restored.widgets().x(widget)==state.widgets().x(widget) &&
                restored.widgets().y(widget)==state.widgets().y(widget),
                "per-widget roundtrip "+widget);
        }
        // Legacy schema-1 file: missing new keys must not break existing installs.
        Properties legacy=new Properties();
        legacy.setProperty("schema","1");
        legacy.setProperty("layout","WIDE");
        legacy.setProperty("theme","INDIGO");
        legacy.setProperty("hud.x","0.7");
        legacy.setProperty("hud.y","0.8");
        write(folder.resolve("ui.properties"),legacy);
        UiState upgraded=new UiState();
        new UiSettingsStore(folder,upgraded).load();
        ok(upgraded.hudX()==0.7 && upgraded.hudY()==0.8 &&
            upgraded.layout()==UiState.Layout.WIDE,"legacy load compatible");
        for(HudPositions.Widget widget:HudPositions.Widget.values()) {
            ok(upgraded.widgets().x(widget)>=0&&upgraded.widgets().x(widget)<=1,
                "legacy default widget position "+widget);
        }
        // Invalid new field must not partially load valid theme/status changes.
        legacy.setProperty("layout","COMPACT");
        legacy.setProperty("hud.x","0.99");
        legacy.setProperty("hud.target.x","NaN");
        write(folder.resolve("ui.properties"),legacy);
        boolean failed=false;
        try{new UiSettingsStore(folder,upgraded).load();}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed,"bad target position rejected");
        ok(upgraded.layout()==UiState.Layout.WIDE &&
            upgraded.hudX()==0.7 && upgraded.hudY()==0.8,
            "corrupt new coordinates cannot partially mutate old settings");
        legacy.setProperty("hud.target.x","0.25");
        legacy.setProperty("hud.cps.y","Infinity");
        write(folder.resolve("ui.properties"),legacy);
        failed=false;
        try{new UiSettingsStore(folder,upgraded).load();}
        catch(IllegalArgumentException expected){failed=true;}
        ok(failed,"bad second widget position rejected");
        ok(upgraded.hudX()==0.7 && upgraded.layout()==UiState.Layout.WIDE,
            "no partial mutation from later invalid field");
    }
    public static void main(String[] args) throws Exception {
        anchors();
        persistence();
        ok(checks>100000,"deterministic HUD placement replay");
        System.out.println("HUD_PLACEMENT_PASS assertions="+checks);
    }
}