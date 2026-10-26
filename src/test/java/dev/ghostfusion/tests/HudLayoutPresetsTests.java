package dev.ghostfusion.tests;
import dev.ghostfusion.ui.HudLayoutPresets;
import dev.ghostfusion.ui.HudLayoutPresets.Preset;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Default profile compatibility, presets, exact persistence and hit zones. */
public final class HudLayoutPresetsTests {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void eq(double a,double b,String why) {
        ok(Double.compare(a,b)==0,why);
    }
    private static void snapshot(UiState ui,double[] dst) {
        dst[0]=ui.hudX();dst[1]=ui.hudY();
        for(HudPositions.Widget w:HudPositions.Widget.values()) {
            dst[2+2*w.ordinal()]=ui.widgets().x(w);
            dst[3+2*w.ordinal()]=ui.widgets().y(w);
        }
    }
    public static void main(String[] args)throws Exception {
        UiState ui=new UiState();
        ok(!HudLayoutPresets.apply(ui,Preset.DEFAULT),
            "legacy coordinates already match Default without writes");
        ok(HudLayoutPresets.matches(ui,Preset.DEFAULT,"top_right"),
            "default list stays in original exact top-right preset mode");
        ok(!HudLayoutPresets.matches(ui,Preset.DEFAULT,"custom"),
            "custom mode must never masquerade as original corner preset");
        ui.theme(UiState.Theme.NEBULA);
        ui.layout(UiState.Layout.COMPACT);
        ui.query("HUD stable search");
        ok(HudLayoutPresets.apply(ui,Preset.PVP),"PvP layout changes positions");
        ok(HudLayoutPresets.matches(ui,Preset.PVP,"custom"),"PvP matches five anchors");
        ok(!HudLayoutPresets.apply(ui,Preset.PVP),"same layout is idempotent");
        ok(ui.theme()==UiState.Theme.NEBULA &&
           ui.layout()==UiState.Layout.COMPACT &&
           "HUD stable search".equals(ui.query()),
           "preset never touches current visual theme, GUI layout or search");
        eq(ui.widgets().x(HudPositions.Widget.CPS),.025,"PvP CPS placement");
        ok(HudLayoutPresets.apply(ui,Preset.STREAM),"stream moves widgets");
        ok(HudLayoutPresets.matches(ui,Preset.STREAM,"custom"),
           "stream layout matches complete anchors");
        eq(ui.widgets().x(HudPositions.Widget.CPS),.42,"stream CPS placement");
        double[] original=new double[2+2*HudPositions.Widget.values().length];
        snapshot(ui,original);
        boolean rejected=false;
        try{HudLayoutPresets.apply(ui,null);}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"invalid preset rejected");
        double[] after=new double[original.length];
        snapshot(ui,after);
        for(int i=0;i<original.length;i++)eq(original[i],after[i],"null preset atomic");
        rejected=false;
        try{HudLayoutPresets.matches(ui,Preset.DEFAULT,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"null module list position rejected");
        ok(HudLayoutPresets.apply(ui,Preset.DEFAULT),"restore original saved anchors");
        HudPositions defaults=new HudPositions();
        eq(ui.hudX(),UiState.DEFAULT_HUD_X,"default status x");
        eq(ui.hudY(),UiState.DEFAULT_HUD_Y,"default status y");
        for(HudPositions.Widget w:HudPositions.Widget.values()){
            eq(ui.widgets().x(w),defaults.x(w),"original widget x "+w);
            eq(ui.widgets().y(w),defaults.y(w),"original widget y "+w);
        }
        for(Preset preset:Preset.values()) {
            int top=HudLayoutPresets.buttonY(40,430,preset);
            ok(HudLayoutPresets.buttonAt(10,118,40,430,17,top)==preset,
                "left top inclusive button "+preset);
            ok(HudLayoutPresets.buttonAt(10,118,40,430,121,top+17)==preset,
                "bottom right inclusive button "+preset);
            ok(HudLayoutPresets.buttonAt(10,118,40,430,121,top+18)!=preset,
                "bottom edge exclusive "+preset);
        }
        ok(HudLayoutPresets.buttonAt(10,93,40,310,17,400)==null,
            "short UI disables buttons to avoid filter overlap");
        Random r=new Random(261026L);
        for(int i=0;i<20000;i++) {
            Preset preset=Preset.values()[r.nextInt(Preset.values().length)];
            // arbitrary prior normalized state / partial manual edit.
            ui.position(r.nextDouble(),r.nextDouble());
            ui.widgets().position(HudPositions.Widget.TARGET,r.nextDouble(),r.nextDouble());
            ok(HudLayoutPresets.apply(ui,preset),"randomized preset changes prior layout "+i);
            ok(HudLayoutPresets.matches(ui,preset,preset.listPosition),
                "randomized exact match "+i);
            ok(!HudLayoutPresets.apply(ui,preset),"no-op repeat "+i);
        }
        Path dir=Files.createTempDirectory("ghostc-layout-presets");
        HostRuntime host=new HostRuntime(dir);
        HudLayoutPresets.apply(host.ui(),Preset.PVP);
        ok(host.uiActions().choiceEdge("hud.modulelist","position",true),
            "typed module choice selects custom");
        host.persistUi();
        host.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok(HudLayoutPresets.matches(loaded.ui(),Preset.PVP,
           loaded.moduleListPosition()),"PvP layout and custom setting persist");
        ok(!HudLayoutPresets.apply(loaded.ui(),Preset.PVP),
            "reopened layout needs no rewrites");
        HudLayoutPresets.apply(loaded.ui(),Preset.DEFAULT);
        ok(loaded.uiActions().reset("hud.modulelist","position"),
            "restore original module position");
        loaded.persistUi();loaded.close();
        HostRuntime restored=new HostRuntime(dir);
        ok(HudLayoutPresets.matches(restored.ui(),Preset.DEFAULT,
            restored.moduleListPosition()),"original layout survives full roundtrip");
        restored.close();
        System.out.println("HUD_LAYOUT_PRESETS_PASS checks="+checks);
    }
}
