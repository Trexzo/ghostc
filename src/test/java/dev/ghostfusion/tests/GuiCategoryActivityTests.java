package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.GuiCategoryActivity;
import java.util.Random;

public final class GuiCategoryActivityTests {
    private static long checks;
    private static void ok(boolean value,String message) {
        checks++;if(!value)throw new AssertionError(message);
    }
    private static final class Module implements GhostModule {
        final String name;final Category category;
        Module(String name,Category category){this.name=name;this.category=category;}
        public String id(){return name;}
        public String displayName(){return name;}
        public Category category(){return category;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent e){}
    }
    public static void main(String[] args) {
        ModuleRegistry registry=new ModuleRegistry();
        GhostModule.Category[] values=GhostModule.Category.values();
        int[] counts=new int[GuiCategoryActivity.COUNT];
        for(int i=0;i<30;i++)
            registry.register(new Module("module."+i,values[i%values.length]),0);
        GuiCategoryActivity.tally(registry,counts);
        ok(counts[0]==0,"disabled modules are not advertised");
        registry.setEnabled("module.0",true);
        registry.setEnabled("module.7",true);
        GuiCategoryActivity.tally(registry,counts);
        ok(counts[0]==2,"total counts only enabled");
        ok(counts[1]==1 && counts[3]==1,"category mapping matches navigation");
        registry.setEnabled("module.7",false);
        GuiCategoryActivity.tally(registry,counts);
        ok(counts[0]==1 && counts[3]==0,"disabled module disappears");
        ok(GuiCategoryActivity.label(0).isEmpty(),"zero hidden");
        ok(GuiCategoryActivity.label(2).equals("2"),"small count shown");
        ok(GuiCategoryActivity.label(105).equals("99+"),"large count abbreviated");
        boolean refused=false;
        try {GuiCategoryActivity.tally(registry,new int[1]);}
        catch(IllegalArgumentException expected){refused=true;}
        ok(refused,"incorrect buffer refused");
        Random random=new Random(28102026L);
        for(int frame=0;frame<10000;frame++) {
            int chosen=random.nextInt(30);
            registry.setEnabled("module."+chosen,random.nextBoolean());
            GuiCategoryActivity.tally(registry,counts);
            int total=0;
            for(int category=1;category<counts.length;category++) {
                ok(counts[category]>=0 && counts[category]<=6,"category bounds");
                total+=counts[category];
            }
            ok(total==counts[0],"sum of categories equals visible total");
            ok(!GuiCategoryActivity.label(counts[0]).contains("-"),
                "no negative badge");
        }
        registry.shutdown();
        System.out.println("GUI_CATEGORY_ACTIVITY_PASS checks="+checks);
    }
}
