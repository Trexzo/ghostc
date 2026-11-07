package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.ModuleListTextGeometry;
import dev.ghostfusion.ui.ModuleListSlateRowGeometry;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Slinky/Vape-inspired text-first panel geometry with real layout rows. */
public final class ModuleListSlateRowGeometryTests {
    private static long checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static final ToIntFunction<String> FONT=s->s.length()*6;
    private static final class Module implements GhostModule {
        private final String id,name;
        Module(String id,String name){this.id=id;this.name=name;}
        public String id(){return id;}
        public String displayName(){return name;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }
    private static void invalid(Runnable r,String why) {
        boolean threw=false;
        try{r.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,why);
    }
    private static void inspect(ModuleListLayout.Row row,int vw,boolean right) {
        int bw=ModuleListSlateRowGeometry.width(row);
        ok(bw==row.textWidth+12 && bw<=row.width,
           "surface follows measured text width, not maximum row width");
        for(int j=0;j<=20;j++) {
            double phase=j/20.0;
            int tx=ModuleListTextGeometry.x(row,row.textWidth,vw,right,phase);
            int sx=ModuleListSlateRowGeometry.x(row,tx,vw);
            ok(sx>=0&&sx+bw<=vw,"animated label surface contained");
            ok(tx>=sx&&tx+row.textWidth<=sx+bw,
               "animated title remains within moving surface");
            int rx=ModuleListSlateRowGeometry.railX(sx,bw,right);
            ok(rx>=sx&&rx+2<=sx+bw,"rail stays within label background");
            int ry=ModuleListSlateRowGeometry.railY(row);
            int rh=ModuleListSlateRowGeometry.railHeight(row);
            ok(ry>=row.y&&ry+rh<=row.y+row.height,
               "muted accent dash centered vertically");
            if(phase==1.0) {
                ok(sx==(!right?row.x:
                       row.x+row.width-bw),"settled surface anchored to text edge");
            }
        }
    }
    public static void main(String[] args) {
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Module("utility.short","HUD"),0);
        r.register(new Module("utility.long","Module List Preview"),0);
        r.register(new Module("utility.medium","Auto Tool"),0);
        r.setEnabled("utility.short",true);
        r.setEnabled("utility.long",true);
        r.setEnabled("utility.medium",true);
        String[] positions={"top_right","top_left","bottom_right","bottom_left","custom"};
        Random rand=new Random(28102026L);
        int inspected=0;
        for(int i=0;i<50000;i++) {
            int vw=25+rand.nextInt(2600),vh=25+rand.nextInt(1400);
            String position=positions[i%positions.length];
            ModuleListLayout.Frame frame=ModuleListLayout.calculate(
                r,vw,vh,9,24,true,position,
                i%2==0?"width":"alphabetical",FONT,
                i%3==0,rand.nextDouble(),rand.nextDouble());
            if(frame.rows.isEmpty())continue;
            for(ModuleListLayout.Row row:frame.rows) {
                inspected++;
                inspect(row,vw,position.endsWith("_right") ||
                   ("custom".equals(position) && row.x>=vw/2));
            }
            if(frame.rows.size()>=2) {
                int shortest=Integer.MAX_VALUE,longest=0;
                for(ModuleListLayout.Row row:frame.rows) {
                    shortest=Math.min(shortest,ModuleListSlateRowGeometry.width(row));
                    longest=Math.max(longest,ModuleListSlateRowGeometry.width(row));
                }
                ok(shortest<=longest,"each row keeps individual text width");
            }
        }
        ok(inspected>20000,"substantial varied layouts checked");
        ModuleListLayout.Frame frame=ModuleListLayout.calculate(
            r,320,200,9,24,true,"top_left","width",FONT);
        ModuleListLayout.Row sample=frame.rows.get(0);
        invalid(()->ModuleListSlateRowGeometry.x(sample,-1,320),
            "negative text origin rejected");
        invalid(()->ModuleListSlateRowGeometry.x(sample,0,1),
            "impossibly narrow screen rejected");
        invalid(()->ModuleListSlateRowGeometry.width(null),
            "null row rejected");
        invalid(()->ModuleListSlateRowGeometry.railX(-1,13,false),
            "negative rail position rejected");
        r.shutdown();
        System.out.println("MODULE_LIST_SLATE_ROW_GEOMETRY_PASS checks="+checks);
    }
}
