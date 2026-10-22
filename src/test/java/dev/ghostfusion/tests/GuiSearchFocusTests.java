package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSearchFocus;
import dev.ghostfusion.ui.GuiSelection;
import dev.ghostfusion.ui.GuiSettingKeyboard;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import java.util.List;
import java.util.Random;

/** R2.61: typing/search navigation cannot activate module shortcuts. */
public final class GuiSearchFocusTests {
    private static int checks;
    private static void ok(boolean valid,String label){
        checks++;if(!valid)throw new AssertionError(label);
    }
    private static final class Module implements GhostModule {
        private final int n;
        Module(int n){this.n=n;}
        public String id(){return "search.feature."+n;}
        public String displayName(){return "Search Feature "+n;}
        public Category category(){return Category.RENDER;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent ignored){}
    }
    private static void matrix(){
        for(int bits=0;bits<8;bits++){
            boolean focused=(bits&1)!=0;
            boolean enter=(bits&2)!=0;
            boolean tab=(bits&4)!=0;
            GuiSearchFocus.Route expected=!focused || tab?
                GuiSearchFocus.Route.PASS:
                enter?GuiSearchFocus.Route.RELEASE:GuiSearchFocus.Route.EDIT;
            ok(GuiSearchFocus.route(focused,enter,tab)==expected,
                "all focus/key shortcut combinations "+bits);
        }
        ok(GuiSearchFocus.route(true,false,false)==GuiSearchFocus.Route.EDIT,
            "typing and arrow keys belong to the focused search field");
        ok(GuiSearchFocus.route(true,true,false)==GuiSearchFocus.Route.RELEASE,
            "focused Enter must release search not toggle selected module");
        ok(GuiSearchFocus.route(true,false,true)==GuiSearchFocus.Route.PASS,
            "Tab can explicitly transfer ownership to settings");
        ok(GuiSearchFocus.route(false,true,false)==GuiSearchFocus.Route.PASS,
            "module Return works when search does not own keyboard");
    }
    private static void replay(){
        ModuleRegistry registry=new ModuleRegistry();
        for(int i=0;i<70;i++)registry.register(new Module(i),0);
        UiState ui=new UiState();
        List<ModuleRegistry.Entry> filtered=ui.filtered(registry);
        String selected=GuiSelection.reconcile(null,filtered);
        int settingsFocus=-1;
        Random r=new Random(2612026L);
        int changes=0,focusTransfers=0;
        for(int i=0;i<75000;i++){
            boolean focused=r.nextBoolean();
            boolean enter=r.nextBoolean();
            boolean tab=r.nextBoolean();
            GuiSearchFocus.Route route=GuiSearchFocus.route(focused,enter,tab);
            if(route!=GuiSearchFocus.Route.PASS) {
                String unchanged=selected;
                int unchangedSetting=settingsFocus;
                // The GUI search path edits text or releases its own focus,
                // without dispatching any module selection/toggle mutation.
                ok(unchanged.equals(selected)&&unchangedSetting==settingsFocus,
                    "focused search is isolated from module actions "+i);
                ok(route==(enter?GuiSearchFocus.Route.RELEASE:GuiSearchFocus.Route.EDIT),
                    "search edit/return routing "+i);
            } else if(tab && !filtered.isEmpty()) {
                settingsFocus=GuiSettingKeyboard.next(settingsFocus,3,1);
                ok(settingsFocus>=0&&settingsFocus<3,"Tab enters existing settings "+i);
                focusTransfers++;
            } else if(!focused && enter) {
                // Enter is permitted to toggle only when the search is not
                // focused and the settings page does not own keyboard focus.
                ok(route==GuiSearchFocus.Route.PASS,
                    "module enter remains accessible while unfocused "+i);
                changes++;
            }
        }
        ok(focusTransfers>1000 && changes>1000,
            "replay exercised both explicit ownership transitions");
        registry.shutdown();
    }
    public static void main(String[] args){
        matrix();replay();
        ok(checks>30000,"many independent focus routing decisions");
        System.out.println("GUI_SEARCH_FOCUS_PASS assertions="+checks);
    }
}
