package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.ModuleListTextGeometry;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Pure-Java visual geometry: no Forge client or OpenGL needed. */
public final class ModuleListTextGeometryTests {
    private static long checks;
    private static final ToIntFunction<String> FONT=s->s.length()*6;
    private static void ok(boolean b,String description) {
        checks++;
        if(!b)throw new AssertionError(description);
    }
    private static void rejects(Runnable r,String description) {
        boolean thrown=false;
        try {r.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,description);
    }
    private static final class Module implements GhostModule {
        public String id(){return "combat.test";}
        public String displayName(){return "Auto Clicker";}
        public Category category(){return Category.COMBAT;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }
    public static void main(String[] args) {
        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Module(),0);
        registry.setEnabled("combat.test",true);
        ModuleListLayout.Frame frame=ModuleListLayout.calculate(
            registry,300,180,9,24,false,"top_right","width",FONT);
        ModuleListLayout.Row row=frame.rows.get(0);
        ok(row.textWidth==FONT.applyAsInt(row.label),"layout caches measured width");
        ok(ModuleListTextGeometry.x(row,row.textWidth,300,true,1.0)==
           row.x+row.width-6-row.textWidth,"right-aligned settled text");
        ok(ModuleListTextGeometry.x(row,row.textWidth,300,false,1.0)==row.textX,
           "left-aligned settled text");
        ok(ModuleListTextGeometry.x(row,row.textWidth,300,true,0.0)>=
           ModuleListTextGeometry.x(row,row.textWidth,300,true,1.0),
           "right entry moves inward");
        ok(ModuleListTextGeometry.x(row,row.textWidth,300,false,0.0)<=
           ModuleListTextGeometry.x(row,row.textWidth,300,false,1.0),
           "left entry moves inward");
        rejects(()->ModuleListTextGeometry.x(row,row.textWidth,300,true,Double.NaN),
            "nonfinite progress rejected");
        rejects(()->ModuleListTextGeometry.x(row,row.textWidth,300,true,1.1),
            "out-of-range progress rejected");
        rejects(()->ModuleListTextGeometry.x(row,row.width,300,true,1.0),
            "oversize label rejected");
        Random random=new Random(20261028L);
        String[] positions={"top_right","top_left","bottom_right",
                            "bottom_left","custom"};
        for(int i=0;i<40000;i++) {
            int width=23+random.nextInt(2400),height=23+random.nextInt(1200);
            String position=positions[i%positions.length];
            double ax=random.nextDouble(),ay=random.nextDouble();
            ModuleListLayout.Frame next=ModuleListLayout.calculate(
                registry,width,height,9,24,false,position,"width",FONT,
                (i&1)==0,ax,ay);
            for(ModuleListLayout.Row r:next.rows) {
                ok(r.textWidth==FONT.applyAsInt(r.label),
                    "cached width exact "+i);
                for(int j=0;j<=4;j++) {
                    double phase=j/4.0;
                    for(boolean right:new boolean[]{true,false}) {
                        int x=ModuleListTextGeometry.x(r,r.textWidth,width,right,phase);
                        ok(x>=0 && x+r.textWidth<=width,
                           "entry remains on screen "+i);
                        if(phase==1.0) {
                            int intended=right?r.x+r.width-6-r.textWidth:r.textX;
                            ok(x==intended,"settled text equals layout "+i);
                        }
                    }
                }
            }
        }
        registry.shutdown();
        System.out.println("MODULE_LIST_TEXT_GEOMETRY_PASS checks="+checks);
    }
}
