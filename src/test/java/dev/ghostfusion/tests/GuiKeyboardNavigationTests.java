package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.GuiSelection;
import dev.ghostfusion.ui.UiState;
import java.util.List;
import java.util.Random;

/** R2.55 keyboard module navigation stays within filtered visible rows. */
public final class GuiKeyboardNavigationTests {
    private static long checks;
    private static void ok(boolean b,String reason) {
        checks++;
        if(!b)throw new AssertionError(reason);
    }
    private static void invalid(Runnable r,String reason) {
        boolean failed=false;
        try {r.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,reason);
    }
    private static final class Feature implements GhostModule {
        private final String id;
        private final Category category;
        Feature(int id) {
            this.id="gui.module."+id;
            this.category=Category.values()[id%Category.values().length];
        }
        public String id(){return id;}
        public String displayName(){return "Feature "+id;}
        public Category category(){return category;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent ignored){}
    }
    private static int index(String id,List<ModuleRegistry.Entry> entries) {
        for(int i=0;i<entries.size();i++)if(entries.get(i).module().id().equals(id))return i;
        return -1;
    }
    private static void basic() {
        ModuleRegistry registry=new ModuleRegistry();
        for(int i=0;i<35;i++)registry.register(new Feature(i),0);
        UiState ui=new UiState();
        List<ModuleRegistry.Entry> list=ui.filtered(registry);
        ok(list.size()==35,"complete registry");
        String first=list.get(0).module().id(),last=list.get(list.size()-1).module().id();
        ok(first.equals(GuiSelection.step(null,list,1)),"empty selection moves to first");
        ok(last.equals(GuiSelection.step("missing",list,-1)),"missing selection moves to last when upwards");
        ok(first.equals(GuiSelection.step(first,list,-1)),"up at first does not wrap");
        ok(last.equals(GuiSelection.step(last,list,1)),"down at last does not wrap");
        String selected=first;
        int start=0;
        for(int i=0;i<34;i++){
            selected=GuiSelection.step(selected,list,1);
            start=GuiSelection.reveal(selected,list,start,5);
            ok(index(selected,list)==i+1,"keyboard navigation advances exactly one module");
            ok(index(selected,list)>=start&&index(selected,list)<start+5,
                "keyboard selected row remains visible");
            ok(start>=0&&start<=30,"viewport scroll bounded");
        }
        ok(selected.equals(last)&&start==30,"last row reveal scrolls to final page");
        for(int i=33;i>=0;i--){
            selected=GuiSelection.step(selected,list,-1);
            start=GuiSelection.reveal(selected,list,start,5);
            ok(index(selected,list)==i,"up reverses selection");
            ok(index(selected,list)>=start&&index(selected,list)<start+5,
                "upward selection stays within visible window");
        }
        ok(start==0&&selected.equals(first),"return to first page");
        ok(GuiSelection.reveal(list.get(6).module().id(),list,5,5)==5,
            "already-visible selection does not disrupt wheel scrolling");
        ok(GuiSelection.reveal(list.get(34).module().id(),list,Integer.MAX_VALUE,5)==30,
            "oversized scroll is clamped");
        ok(GuiSelection.reveal(null,list,-10,5)==0,"negative scroll clamped");
        invalid(()->GuiSelection.step(first,list,0),"zero navigation step rejected");
        invalid(()->GuiSelection.step(first,list,3),"large navigation step rejected");
        invalid(()->GuiSelection.reveal(first,list,0,0),"no visible rows rejected");
        invalid(()->GuiSelection.step(first,null,1),"null filtered list rejected");
        invalid(()->GuiSelection.reveal(first,null,0,5),"null reveal list rejected");
        ui.query("gui.module.17");
        List<ModuleRegistry.Entry> one=ui.filtered(registry);
        ok(one.size()==1,"typed filter has one result");
        String only=one.get(0).module().id();
        ok(only.equals(GuiSelection.step(first,one,1))&&
           only.equals(GuiSelection.step(first,one,-1)),
           "hidden previous selection can never be navigated to");
        ok(GuiSelection.reveal(only,one,25,5)==0,"one-result list remains on first page");
        ui.query("no matches ever");
        List<ModuleRegistry.Entry> empty=ui.filtered(registry);
        ok(empty.isEmpty()&&GuiSelection.step(last,empty,1)==null,
            "empty results have no selectable target");
        ok(GuiSelection.reveal(null,empty,99,5)==0,
            "empty list has bounded zero scroll");
        registry.shutdown();
    }
    private static void filteredReplay() {
        ModuleRegistry registry=new ModuleRegistry();
        for(int i=0;i<90;i++)registry.register(new Feature(i),0);
        UiState ui=new UiState();
        Random random=new Random(2552026L);
        String selected=null;
        int scroll=0;
        for(int n=0;n<40000;n++){
            if(n%67==0)ui.query(random.nextInt(4)==0?"unmatched":random.nextBoolean()?"Feature 1":"");
            if(n%109==0)ui.category(random.nextInt(4)==0?null:
                GhostModule.Category.values()[random.nextInt(GhostModule.Category.values().length)]);
            List<ModuleRegistry.Entry> entries=ui.filtered(registry);
            selected=GuiSelection.reconcile(selected,entries);
            selected=GuiSelection.step(selected,entries,random.nextBoolean()?1:-1);
            int rows=1+random.nextInt(12);
            scroll=GuiSelection.reveal(selected,entries,scroll,rows);
            int at=index(selected==null?"":selected,entries);
            ok(scroll>=0&&scroll<=Math.max(0,entries.size()-rows),
                "bounded filtered scroll "+n);
            ok(entries.isEmpty()?selected==null:at>=scroll&&at<scroll+rows,
                "selection always belongs to visible filtered window "+n);
            if(!entries.isEmpty())
                ok(selected.equals(entries.get(at).module().id()),
                    "stable identity survives index and filter churn "+n);
        }
        registry.shutdown();
    }
    public static void main(String[] args) {
        basic();filteredReplay();
        ok(checks>90000,"substantial keyboard navigation coverage");
        System.out.println("GUI_KEYBOARD_NAVIGATION_PASS checks="+checks);
    }
}
