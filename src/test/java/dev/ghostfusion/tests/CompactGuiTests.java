package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.GuiChrome;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.GuiSelection;
import dev.ghostfusion.ui.UiState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** R1.7: compact layout remains usable and selection never points off-filter. */
public final class CompactGuiTests {
    private static int checks;
    private static void ok(boolean condition,String message) {
        checks++;
        if(!condition) throw new AssertionError(message);
    }
    private static final class Fixture implements GhostModule {
        final String id;
        final Category cat;
        Fixture(String id,Category cat) {this.id=id;this.cat=cat;}
        public String id() {return id;}
        public String displayName() {return "Module "+id;}
        public Category category() {return cat;}
        public void onEnable() {}
        public void onDisable() {}
        public void onEvent(GameEvent e) {}
    }
    private static ModuleRegistry registry() {
        ModuleRegistry r=new ModuleRegistry();
        GhostModule.Category[] types=GhostModule.Category.values();
        for(int i=0;i<30;i++)
            r.register(new Fixture("module."+i,types[i%types.length]),0);
        return r;
    }
    private static void compactRecovery() {
        GuiLayout desktop=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        GuiLayout compact=GuiLayout.calculate(1920,1080,UiState.Layout.COMPACT);
        ok(GuiChrome.interactive(desktop),"wide usable");
        ok(compact.width==390 && GuiChrome.interactive(compact),
           "390px compact usable, unlike the old 445px gate");
        ok(GuiChrome.headerAt(compact,compact.x+compact.width-98,compact.y+12)
                ==GuiChrome.Header.LAYOUT,"compact layout toggle available");
        ok(GuiChrome.headerAt(compact,compact.x+compact.width-54,compact.y+12)
                ==GuiChrome.Header.THEME,"compact theme toggle available");
        ok(GuiChrome.headerAt(compact,compact.x+compact.sidebar+85,compact.y+12)
                ==GuiChrome.Header.HUD,"compact HUD page available");
        ok(GuiChrome.categoryIndex(compact,compact.x+5,compact.y+62)==0,
           "compact ALL category available");
        GuiLayout back=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        ok(GuiChrome.interactive(back),"can switch from Compact back to Wide");
        GuiLayout mini=GuiLayout.calculate(160,120,UiState.Layout.COMPACT);
        ok(!GuiChrome.interactive(mini),"truly undersized viewport still protected");
        ok(GuiChrome.headerAt(mini,mini.x+30,mini.y+12)==GuiChrome.Header.NONE,
           "undersized header rejects inputs");
        for(UiState.Layout mode:UiState.Layout.values()) {
            for(int w=1;w<=900;w+=5) {
                for(int h=1;h<=580;h+=13) {
                    GuiLayout g=GuiLayout.calculate(w,h,mode);
                    boolean allowed=g.sidebar>=70 && g.width>=g.sidebar+230 && g.height>=250;
                    ok(GuiChrome.interactive(g)==allowed,"responsive gate matches chrome geometry");
                    if(allowed) {
                        int left=g.x+g.sidebar+84+47;
                        int right=g.x+g.width-99;
                        ok(left<=right,"HUD and layout controls never overlap");
                        ok(GuiChrome.headerAt(g,g.x+g.sidebar+85,g.y+12)
                            ==GuiChrome.Header.HUD,"HUD remains clickable "+mode);
                        ok(GuiChrome.headerAt(g,g.x+g.width-98,g.y+12)
                            ==GuiChrome.Header.LAYOUT,"layout remains clickable "+mode);
                    }
                }
            }
        }
    }
    private static void filteredSelection() {
        ModuleRegistry registry=registry();
        UiState ui=new UiState();
        List<ModuleRegistry.Entry> all=ui.filtered(registry);
        ok(all.size()==30,"all entries");
        ok("module.0".equals(GuiSelection.reconcile("missing",all)),"stale ID goes to first");
        ok("module.17".equals(GuiSelection.reconcile("module.17",all)),"visible selection preserved");
        ui.query("no results xyz");
        ok(ui.filtered(registry).isEmpty(),"search can be empty");
        ok(GuiSelection.reconcile("module.17",ui.filtered(registry))==null,
           "empty search removes hidden selection");
        ui.query("");
        ui.category(GhostModule.Category.COMBAT);
        List<ModuleRegistry.Entry> combat=ui.filtered(registry);
        ok(!combat.isEmpty(),"combat category");
        String first=combat.get(0).module().id();
        ok(first.equals(GuiSelection.reconcile("module.0",combat)) ||
            "module.0".equals(GuiSelection.reconcile("module.0",combat)),
            "category reconciliation returns a member");
        boolean rejects=false;
        try{GuiSelection.reconcile(null,null);}catch(IllegalArgumentException expected){rejects=true;}
        ok(rejects,"null entries rejected");
        Random rng=new Random(20261019L);
        GhostModule.Category[] types=GhostModule.Category.values();
        String current="module.7";
        for(int i=0;i<25000;i++) {
            ui.category(rng.nextInt(5)==0?null:types[rng.nextInt(types.length)]);
            int mode=rng.nextInt(5);
            ui.query(mode==0?"unmatched token":mode==1?"module.2":mode==2?"Module":"");
            List<ModuleRegistry.Entry> visible=ui.filtered(registry);
            String next=GuiSelection.reconcile(current,visible);
            if(visible.isEmpty()) {
                ok(next==null,"no phantom setting selection "+i);
            } else {
                boolean found=false;
                for(ModuleRegistry.Entry entry:visible)
                    if(entry.module().id().equals(next)) found=true;
                ok(found,"selection belongs to filtered list "+i);
                if(current==null || !current.equals(next))
                    ok(next.equals(visible.get(0).module().id()),
                        "off-filter selection replaced by first visible "+i);
            }
            ok(next==null || next.startsWith("module."),
               "selection has stable module ID "+i);
            current=next;
        }
        registry.shutdown();
    }
    public static void main(String[] args) {
        compactRecovery();
        filteredSelection();
        ok(checks>80000,"substantial compact/filter regression coverage");
        System.out.println("COMPACT_GUI_PASS assertions="+checks);
    }
}
