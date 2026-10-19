package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiHitZones;
import dev.ghostfusion.ui.GuiLayout;
import dev.ghostfusion.ui.UiState;
import dev.ghostfusion.services.WorldIdentityTracker;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R1.5 deterministic visible-row hitboxes and between-tick world-identity safety. */
public final class ViewportHitTests {
    private static int assertions;
    private static void ok(boolean value,String reason) {
        assertions++;
        if(!value) throw new AssertionError(reason);
    }
    private static void rejects(Runnable task,String why) {
        boolean failed=false;
        try {task.run();} catch(IllegalArgumentException expected){failed=true;}
        ok(failed,why);
    }
    private static void identity() throws Exception {
        WorldIdentityTracker tracker=new WorldIdentityTracker();
        Object world=new Object(),player=new Object();
        ok(!tracker.matches(world,player),"not admitted before END tick");
        tracker.observe(world,player);
        ok(tracker.matches(world,player),"current world and player accepted");
        ok(!tracker.matches(new Object(),player),"world swap blocks early render");
        ok(!tracker.matches(world,new Object()),"respawn blocks early render");
        ok(!tracker.matches(null,player),"missing world blocks callback");
        ok(!tracker.matches(world,null),"missing player blocks callback");
        ok(tracker.matches(world,player),"observation is read only");
        tracker.observe(new Object(),player);
        ok(!tracker.matches(world,player),"old world cannot be read after transition");
        tracker.reset();
        ok(!tracker.matches(world,player),"disconnect revokes immediately");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread wrongThread=new Thread(()->{
            try {tracker.matches(world,player);}catch(IllegalStateException expected){blocked.set(true);}
        });
        wrongThread.start();wrongThread.join();
        ok(blocked.get(),"render identity check owner-thread confined");
    }
    private static void boundaries() {
        GuiLayout g=GuiLayout.calculate(1920,1080,UiState.Layout.WIDE);
        ok(g.visibleRows>0,"desktop has rows");
        int x=g.x+g.sidebar+10;
        for(int row=0;row<g.visibleRows;row++) {
            int yy=g.listY+row*28;
            ok(GuiHitZones.moduleIndex(g,x,yy,2,100)==row+2,
               "module top pixel "+row);
            ok(GuiHitZones.moduleIndex(g,x,yy+24,2,100)==row+2,
               "module last visible pixel "+row);
            ok(GuiHitZones.moduleIndex(g,x,yy+25,2,100)==-1,
               "module separator must not activate a hidden item "+row);
            ok(GuiHitZones.moduleIndex(g,x,yy+27,2,100)==-1,
               "module separator boundary "+row);
        }
        ok(GuiHitZones.moduleIndex(g,x,g.listY-1,0,100)==-1,"outside top");
        ok(GuiHitZones.moduleIndex(g,x,g.listY+g.listHeight,0,100)==-1,"outside bottom");
        ok(GuiHitZones.moduleIndex(g,g.x+g.sidebar+8,g.listY,0,100)==-1,"outside left");
        ok(GuiHitZones.moduleIndex(g,g.x+g.width-11,g.listY,0,100)==-1,"outside right");
        ok(GuiHitZones.moduleIndex(g,x,g.listY,100,100)==-1,"scrolled beyond count");
        int settingX=g.x+g.sidebar+12;
        int start=GuiHitZones.settingStartY(g),visible=GuiHitZones.visibleSettings(g);
        ok(visible>0,"desktop settings visible");
        for(int row=0;row<visible;row++) {
            int yy=start+row*22;
            ok(GuiHitZones.settingIndex(g,settingX,yy,1,100)==row+1,
               "setting active row "+row);
            ok(GuiHitZones.settingIndex(g,settingX,yy+18,1,100)==row+1,
               "setting last active pixel "+row);
            ok(GuiHitZones.settingIndex(g,settingX,yy+19,1,100)==-1,
               "setting separator rejects clicks "+row);
            ok(GuiHitZones.settingIndex(g,settingX,yy+21,1,100)==-1,
               "setting separator boundary "+row);
        }
        ok(!GuiHitZones.settingsArea(g,settingX,g.listY),"settings not module list");
        ok(!GuiHitZones.moduleArea(g,x,start),"modules not settings");
        ok(GuiHitZones.settingsArea(g,settingX,start),"settings area only");
        ok(GuiHitZones.moduleArea(g,x,g.listY),"module area only");
        rejects(()->GuiHitZones.moduleIndex(g,x,g.listY,-1,1),"negative module scroll");
        rejects(()->GuiHitZones.settingIndex(g,settingX,start,0,-1),"negative settings count");
        rejects(()->GuiHitZones.moduleIndex(null,x,g.listY,0,1),"null layout");
    }
    private static void fuzz() {
        Random rng=new Random(913512345L);
        UiState.Layout[] modes=UiState.Layout.values();
        for(int i=0;i<18000;i++) {
            int w=1+rng.nextInt(2400),h=1+rng.nextInt(1400);
            GuiLayout g=GuiLayout.calculate(w,h,modes[i%2]);
            int x=g.x+g.sidebar+10;
            int settingX=g.x+g.sidebar+12;
            int count=rng.nextInt(85),scroll=rng.nextInt(96);
            for(int row=0;row<Math.min(10,g.visibleRows);row++) {
                int top=g.listY+row*28;
                boolean possible=g.width-g.sidebar-20>1 &&
                    row+scroll<count && top+24<g.listY+g.listHeight;
                int got=GuiHitZones.moduleIndex(g,x,top+7,scroll,count);
                ok(got==(possible?row+scroll:-1),"drawn module row "+i+"/"+row);
                ok(GuiHitZones.moduleIndex(g,x,top+26,scroll,count)==-1,
                   "separator never maps to next module "+i+"/"+row);
            }
            int vis=GuiHitZones.visibleSettings(g),sCount=rng.nextInt(23),sScroll=rng.nextInt(25);
            for(int row=0;row<Math.min(8,vis);row++) {
                int top=GuiHitZones.settingStartY(g)+row*22;
                int got=GuiHitZones.settingIndex(g,settingX,top+4,sScroll,sCount);
                boolean possible=g.width-g.sidebar-26>1 && sScroll+row<sCount;
                ok(got==(possible?sScroll+row:-1),"settings row "+i+"/"+row);
                ok(GuiHitZones.settingIndex(g,settingX,top+20,sScroll,sCount)==-1,
                   "settings separator "+i+"/"+row);
            }
            // Clicks just outside the panel must not activate hidden items.
            ok(GuiHitZones.moduleIndex(g,g.x-1,g.listY,scroll,count)==-1,
               "left-outside "+i);
            ok(GuiHitZones.settingIndex(g,g.x+g.width,GuiHitZones.settingStartY(g),
                sScroll,sCount)==-1,"right-outside "+i);
        }
    }
    public static void main(String[] args) throws Exception {
        identity();boundaries();fuzz();
        ok(assertions>90000,"large hitbox replay");
        System.out.println("VIEWPORT_HIT_PASS assertions="+assertions);
    }
}
