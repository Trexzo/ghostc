package dev.ghostfusion.tests;

import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.HudPositions;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.UiState;
import java.nio.file.Files;
import java.nio.file.Path;

/** End-to-end profile/UI roundtrip for optional free-position Module List. */
public final class ModuleListEditorTests {
    private static int checks;
    private static void ok(boolean value,String why) {
        checks++;if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("ghostc-module-list-editor");
        HostRuntime first=new HostRuntime(dir);
        UiState ui=first.ui();
        ok("top_right".equals(first.moduleListPosition()),
            "legacy profiles retain the original top-right position preset");
        ok(ui.widgets().x(HudPositions.Widget.MODULE_LIST)==1.0 &&
           ui.widgets().y(HudPositions.Widget.MODULE_LIST)==0.0,
           "new widget defaults to top-right without migrating legacy anchors");
        // A free-position drag commits its UI coordinate and changes only
        // the Module List mode to custom through the validated settings gateway.
        ui.widgets().position(HudPositions.Widget.MODULE_LIST,0.37,0.62);
        first.persistUi();
        ok(first.uiActions().choiceEdge("hud.modulelist","position",true),
            "custom position selected as the last allowed Choice");
        ok("custom".equals(first.moduleListPosition()),"runtime selects custom mode");
        first.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok("custom".equals(loaded.moduleListPosition()),"custom mode profile roundtrip");
        ok(loaded.ui().widgets().x(HudPositions.Widget.MODULE_LIST)==0.37 &&
           loaded.ui().widgets().y(HudPositions.Widget.MODULE_LIST)==0.62,
           "normalized list anchor survives ui.properties persistence");
        ok(loaded.ui().widgets().x(HudPositions.Widget.TARGET)==0.5,
           "unrelated target anchor remains untouched");
        ok(!loaded.uiActions().choiceEdge("hud.modulelist","position",true),
           "selecting custom twice never triggers redundant persistence");
        int[] ws={1920,1280,640,320},hs={1080,720,360,240};
        for(int i=0;i<ws.length;i++){
            // A disabled/empty list returns an empty frame; admission stays safe.
            ModuleListLayout.Frame frame=ModuleListLayout.calculate(
                loaded.modules(),ws[i],hs[i],9,loaded.moduleListMaxRows(),
                loaded.moduleListShowRender(),loaded.moduleListPosition(),
                loaded.moduleListOrder(),String::length,
                loaded.moduleListCompact(),
                loaded.ui().widgets().x(HudPositions.Widget.MODULE_LIST),
                loaded.ui().widgets().y(HudPositions.Widget.MODULE_LIST));
            ok(frame.rows.isEmpty() ||
               (frame.x>=0&&frame.y>=0&&frame.x+frame.width<=ws[i]&&
                frame.y+frame.height<=hs[i]),
               "custom bounds for Minecraft viewport "+i);
        }
        ok(loaded.uiActions().reset("hud.modulelist","position"),
            "reset restores preset corner");
        loaded.ui().widgets().reset(HudPositions.Widget.MODULE_LIST);
        loaded.persistUi();
        loaded.close();
        HostRuntime reset=new HostRuntime(dir);
        ok("top_right".equals(reset.moduleListPosition()) &&
           reset.ui().widgets().x(HudPositions.Widget.MODULE_LIST)==1.0 &&
           reset.ui().widgets().y(HudPositions.Widget.MODULE_LIST)==0.0,
           "reset commits the old default without affecting other profiles");
        reset.close();
        System.out.println("MODULE_LIST_EDITOR_PASS checks="+checks);
    }
}
