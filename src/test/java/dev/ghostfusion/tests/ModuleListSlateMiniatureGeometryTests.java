package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.HudPreviewArt;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.ModuleListLayout;
import dev.ghostfusion.ui.ModuleListSlateMiniatureGeometry;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * 50k seeded letterboxed viewports, enabled-only actual module membership
 * and zero overdraw outside the genuine F7 widget footprint.
 */
public final class ModuleListSlateMiniatureGeometryTests {
    private static long checks;
    private static void ok(boolean good,String why) {
        checks++;if(!good)throw new AssertionError(why);
    }
    private static void invalid(Runnable r,String why) {
        boolean failed=false;
        try{r.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
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
    private static final class Sink implements HudPreviewArt.Sink {
        HudPreviewGeometry.Rect bounds;
        int panels,lines,rails,muted;
        int widest,shortest=Integer.MAX_VALUE;
        int lastPanelRight,lastPanelLeft;
        Sink(HudPreviewGeometry.Rect bounds){this.bounds=bounds;}
        public void box(int x,int y,int w,int h,int role) {
            ok(w>0&&h>0,"every paint box is positive");
            ok(x>=bounds.x&&y>=bounds.y&&
               (long)x+w<=bounds.x+bounds.width&&
               (long)y+h<=bounds.y+bounds.height,
               "no glyph/bar can escape the real F7 widget bounds");
            if(role==HudPreviewArt.PANEL) {
                panels++;
                widest=Math.max(widest,w);
                shortest=Math.min(shortest,w);
                lastPanelRight=x+w;lastPanelLeft=x;
            }else if(role==HudPreviewArt.SECONDARY)lines++;
            else if(role==HudPreviewArt.PRIMARY)rails++;
            else if(role==HudPreviewArt.MUTED)muted++;
            else throw new AssertionError("unexpected Slate miniature role");
        }
    }
    public static void main(String[] args) {
        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Module("visual.one","Velocity"),0);
        registry.register(new Module("visual.two","Auto Clicker Pro"),0);
        registry.register(new Module("visual.three","Aim"),0);
        registry.register(new Module("visual.four","Hide This Disabled"),0);
        for(String id:new String[]{"visual.one","visual.two","visual.three"})
            registry.setEnabled(id,true);
        ToIntFunction<String> font=s->s.length()*6;
        String[] positions={"top_right","top_left","bottom_right","bottom_left","custom"};
        Random rng=new Random(30102026L);
        int seen=0;
        for(int i=0;i<50000;i++) {
            int vw=32+rng.nextInt(2000),vh=32+rng.nextInt(1250);
            int panelW=3+rng.nextInt(590),panelH=3+rng.nextInt(350);
            HudPreviewGeometry geometry=new HudPreviewGeometry(
                23,31,panelW,panelH,vw,vh);
            String pos=positions[i%positions.length];
            ModuleListLayout.Frame frame=ModuleListLayout.calculate(
                registry,vw,vh,9,24,true,pos,i%2==0?"width":"alphabetical",
                font,i%3==0,rng.nextDouble(),rng.nextDouble());
            if(frame.rows.isEmpty())continue;
            double factor=rng.nextDouble();
            HudPreviewGeometry.Rect bounds=geometry.at(factor,rng.nextDouble(),
                frame.width,frame.height);
            Sink sink=new Sink(bounds);
            boolean right=pos.endsWith("_right")||
                (pos.equals("custom")&&factor>=0.5);
            boolean background=i%2==0,accent=i%3!=0;
            boolean drawn=ModuleListSlateMiniatureGeometry.paint(
                bounds,frame,right,background,accent,sink);
            ok(drawn,"active real modules get painted");
            ok(sink.lines==frame.rows.size(),"one real bar per active row");
            ok(sink.panels==(background?frame.rows.size():0),
                "matte respects existing background setting");
            ok(sink.rails==(accent?frame.rows.size():0),
                "rail respects existing accent setting");
            if(background&&frame.width>24&&bounds.width>40)
                ok(sink.shortest<=sink.widest,"independent real text widths");
            seen++;
        }
        ok(seen>25000,"substantial combinations with eligible rows");
        ModuleListLayout.Frame sample=ModuleListLayout.calculate(
            registry,800,500,9,24,true,"top_left","width",font);
        HudPreviewGeometry.Rect box=new HudPreviewGeometry(
            0,0,220,100,800,500).at(0,0,sample.width,sample.height);
        invalid(()->ModuleListSlateMiniatureGeometry.paint(
            null,sample,false,true,true,new Sink(box)),
            "null widget footprint");
        invalid(()->ModuleListSlateMiniatureGeometry.paint(
            box,null,false,true,true,new Sink(box)),
            "null list frame");
        invalid(()->ModuleListSlateMiniatureGeometry.paint(
            box,sample,false,true,true,null),
            "null pixel sink");
        // No enabled modules must NEVER reintroduce the generic fake
        // three-row picture. Empty F7 widgets remain selectable.
        for(String id:new String[]{"visual.one","visual.two","visual.three"})
            registry.setEnabled(id,false);
        ModuleListLayout.Frame empty=ModuleListLayout.calculate(
            registry,800,500,9,24,true,"top_left","width",font);
        ok(empty.rows.isEmpty()&&empty.width==0&&empty.height==0,
            "real enabled-only model has no rows");
        for(int i=0;i<30000;i++) {
            int vw=100+rng.nextInt(2200),vh=100+rng.nextInt(1300);
            int panelWidth=3+rng.nextInt(600),panelHeight=3+rng.nextInt(380);
            HudPreviewGeometry geom=new HudPreviewGeometry(
                5,7,panelWidth,panelHeight,vw,vh);
            HudPreviewGeometry.Rect blank=geom.at(rng.nextDouble(),
                rng.nextDouble(),128,36);
            Sink sink=new Sink(blank);
            boolean handled=ModuleListSlateMiniatureGeometry.paint(
                blank,empty,(i&1)==0,(i&2)==0,(i&4)==0,sink);
            ok(handled,"empty Slate list is fully handled without generic fallback");
            ok(sink.panels==1&&sink.muted==1&&sink.lines==0&&sink.rails==0,
                "one neutral surface and marker, no fake enabled module rows");
        }
        registry.shutdown();
        System.out.println("MODULE_LIST_SLATE_MINIATURE_GEOMETRY_PASS checks="+checks);
    }
}
