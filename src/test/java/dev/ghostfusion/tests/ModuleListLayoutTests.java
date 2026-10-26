package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.ui.ModuleListLayout;
import java.util.Random;
import java.util.function.ToIntFunction;

/** Independent policy tests: style, sorted ordering, clipping, live enable/disable. */
public final class ModuleListLayoutTests {
    private static int assertions;
    private static void ok(boolean value,String reason) {
        assertions++;
        if(!value)throw new AssertionError(reason);
    }
    private static void rejects(Runnable operation,String reason) {
        boolean failed=false;
        try {operation.run();} catch(IllegalArgumentException expected) {failed=true;}
        ok(failed,reason);
    }
    private static final ToIntFunction<String> FONT = String::length;
    private static final class Module implements GhostModule {
        final String id,name;final Category category;
        Module(String id,String name,Category category) {
            this.id=id;this.name=name;this.category=category;
        }
        public String id() {return id;}
        public String displayName() {return name;}
        public Category category() {return category;}
        public void onEnable() {}
        public void onDisable() {}
        public void onEvent(GameEvent e) {}
    }
    private static ModuleListLayout.Frame make(ModuleRegistry r,int w,int h,int rows,
                                                 boolean render,String pos,String order) {
        return ModuleListLayout.calculate(r,w,h,9,rows,render,pos,order,FONT);
    }
    private static void exercise() {
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Module("hud.modulelist","Module List",GhostModule.Category.RENDER),0);
        r.register(new Module("combat.z","Zebra",GhostModule.Category.COMBAT),0);
        r.register(new Module("movement.a","Alpha Extended",GhostModule.Category.MOVEMENT),0);
        r.register(new Module("combat.b","Beta",GhostModule.Category.COMBAT),0);
        r.register(new Module("hud.other","Other HUD",GhostModule.Category.RENDER),0);
        r.setEnabled("hud.modulelist",true);
        ok(make(r,200,100,10,false,"top_right","width").rows.isEmpty(),
            "self-only module list is never self-advertised");
        for(String id:new String[]{"combat.z","movement.a","combat.b","hud.other"})
            r.setEnabled(id,true);
        ModuleListLayout.Frame width=make(r,200,100,10,false,"top_right","width");
        ok(width.rows.size()==3,"render category hidden by default");
        ok(width.rows.get(0).label.equals("Alpha Extended"),"widest-first visible");
        ok(width.rows.get(1).label.equals("Zebra"),"second width");
        ok(width.rows.get(2).label.equals("Beta"),"third width");
        ok(width.x+width.width<=196 && width.y==4,
            "right side anchored inside screen");
        ModuleListLayout.Frame alphabet=make(r,200,100,10,true,
            "bottom_left","alphabetical");
        ok(alphabet.rows.size()==4,"opt-in render category visible");
        ok(alphabet.rows.get(0).label.equals("Alpha Extended"),"alphabet first");
        ok(alphabet.rows.get(1).label.equals("Beta"),"alphabet next");
        ok(alphabet.y+alphabet.height==96 && alphabet.x==4,
            "bottom-left aligned");
        ModuleListLayout.Frame limited=make(r,200,100,2,true,"top_left","width");
        ok(limited.rows.size()==2,"row limit enforced");
        ModuleListLayout.Frame cramped=make(r,20,20,24,true,"bottom_right","width");
        ok(cramped.rows.size()<=1,"short viewport admits no more than one row");
        for(ModuleListLayout.Row row:cramped.rows)
            ok(row.x>=0 && row.textX>=0 && row.x+row.width<=20 &&
               row.y>=0 && row.y+row.height<=20,"tiny viewport bounds");
        ok(make(r,5,2,24,true,"top_left","width").rows.isEmpty(),
            "too-small viewport skips all font work");
        ModuleListLayout.Frame custom=ModuleListLayout.calculate(r,200,100,9,
            10,true,"custom","width",FONT,false,0.4,0.6);
        ok(custom.rows.size()==4 && custom.x>=0 && custom.y>=0 &&
           custom.x+custom.width<=200 && custom.y+custom.height<=100,
           "custom anchor resolves to a contained frame");
        ModuleListLayout.Frame far=ModuleListLayout.calculate(r,200,100,9,
            10,true,"custom","width",FONT,false,1,1);
        ok(far.x+far.width==200 && far.y+far.height==100,
            "custom bottom-right snaps to screen edge");
        rejects(()->ModuleListLayout.calculate(r,200,100,9,10,true,
            "custom","width",FONT,false,Double.NaN,0.5),
            "custom nonfinite anchor must fail closed");
        ModuleListLayout.Frame compact=ModuleListLayout.calculate(r,200,100,9,
            10,true,"top_right","width",FONT,true);
        ok(compact.rows.size()==4 && compact.rows.get(0).height==11,
            "compact spacing changes only row pitch");
        ok(width.rows.get(0).height==13,"default spacing retains legacy pixels");
        r.setEnabled("combat.z",false);
        ok(make(r,200,100,10,false,"top_left","alphabetical").rows.size()==2,
            "disabled modules immediately disappear");
        r.setEnabled("combat.b",false);
        r.setEnabled("movement.a",false);
        ok(make(r,200,100,10,false,"top_left","alphabetical").rows.isEmpty(),
            "all hidden when only render module exists");
        rejects(()->make(r,200,100,25,true,"top_left","width"),"row cap");
        rejects(()->make(r,200,100,2,true,"somewhere","width"),"position validation");
        rejects(()->make(r,200,100,2,true,"top_left","bad"),"ordering validation");
        rejects(()->ModuleListLayout.calculate(r,0,100,9,2,true,"top_left",
            "width",FONT),"viewport validation");
        r.setEnabled("combat.z",true);
        Random random=new Random(26102026L);
        String[] corners={"top_left","top_right","bottom_left","bottom_right"};
        for(int i=0;i<1600;i++) {
            int w=1+random.nextInt(180),h=1+random.nextInt(110);
            ModuleListLayout.Frame frame=make(r,w,h,1+random.nextInt(24),true,
                corners[i%4],i%2==0?"width":"alphabetical");
            ok(frame.rows.size()<=24,"bounded row count "+i);
            for(ModuleListLayout.Row row:frame.rows) {
                ok(row.x>=0 && row.y>=0 && row.x+row.width<=w &&
                   row.y+row.height<=h,"viewport fit "+i);
                ok(row.textX>=row.x && row.textX<=row.x+row.width &&
                   row.textY>=row.y && row.textY+9<=row.y+row.height,
                   "font baseline bounds "+i);
            }
        }
        // Malicious formatting and stray line breaks cannot be painted raw.
        ModuleRegistry weird=new ModuleRegistry();
        weird.register(new Module("combat.format","Name\u00a7k\nHidden",GhostModule.Category.COMBAT),0);
        weird.setEnabled("combat.format",true);
        String safe=make(weird,180,100,4,true,"top_left","width").rows.get(0).label;
        ok(safe.indexOf('\u00a7')<0 && safe.indexOf('\n')<0,"sanitized module display");
        weird.shutdown();r.shutdown();
    }
    public static void main(String[] args) {
        exercise();
        System.out.println("MODULE_LIST_LAYOUT_PASS assertions="+assertions);
    }
}
