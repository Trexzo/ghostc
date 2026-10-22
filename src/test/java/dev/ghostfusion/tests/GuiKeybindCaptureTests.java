package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ReservedKeys;
import dev.ghostfusion.ui.GuiKeybindCapture;
import dev.ghostfusion.ui.UiActions;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.64: F7 GUI can capture a module bind without mouse input. */
public final class GuiKeybindCaptureTests {
    private static long checks;
    private static void ok(boolean pass,String reason){
        checks++; if(!pass)throw new AssertionError(reason);
    }
    private static void rejects(Runnable code,String reason){
        boolean rejected=false;
        try{code.run();}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,reason);
    }
    private static final class Dummy implements GhostModule {
        private final String id;
        Dummy(String id){this.id=id;}
        public String id(){return id;}
        public String displayName(){return "Bind Test "+id;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }
    private static void focusPolicy(){
        ok(GuiKeybindCapture.arm(true,false,-1,"hud.target",false),
            "B may arm capture on module list selection");
        ok(!GuiKeybindCapture.arm(false,false,-1,"hud.target",false),
            "HUD editor never arms module binding");
        ok(!GuiKeybindCapture.arm(true,true,-1,"hud.target",false),
            "search input owns B while focused");
        ok(!GuiKeybindCapture.arm(true,false,0,"hud.target",false),
            "setting focus owns B until module list regains focus");
        ok(!GuiKeybindCapture.arm(true,false,-1,null,false),
            "empty search result cannot arm bind capture");
        ok(!GuiKeybindCapture.arm(true,false,-1,"",false),
            "blank module selection cannot arm bind capture");
        ok(!GuiKeybindCapture.arm(true,false,-1,"hud.target",true),
            "B autorepeat cannot repeatedly rearm capture");
        ok(GuiKeybindCapture.arm(true,false,-9,"hud.target",false),
            "negative focus remains a module selection focus");
        Random rnd=new Random(2642026L);
        for(int i=0;i<140000;i++){
            boolean page=rnd.nextBoolean(),search=rnd.nextBoolean();
            boolean repeat=rnd.nextBoolean();
            int setting=rnd.nextInt(8)-2;
            String selected=rnd.nextBoolean()?"combat.aimassist":
                rnd.nextBoolean()?null:"";
            boolean expected=page && !search && setting<0 &&
                selected!=null && !selected.isEmpty() && !repeat;
            ok(GuiKeybindCapture.arm(page,search,setting,selected,repeat)==expected,
                "capture owns keyboard only in eligible state "+i);
        }
    }
    private static void actionsAndConflict(){
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Dummy("hud.alpha"),12);
        r.register(new Dummy("hud.beta"),13);
        final int[] saves={0};
        UiActions actions=new UiActions(r,()->saves[0]++);
        // B arms capture but does not mutate any persisted binding until
        // a later distinct key event successfully passes the UI gateway.
        ok(GuiKeybindCapture.arm(true,false,-1,"hud.alpha",false),
            "keyboard capture armed for selected module");
        ok(r.get("hud.alpha").keybind()==12 && saves[0]==0,
            "arming never changes the old bind or saves a profile");
        actions.bind("hud.alpha",30);
        ok(r.get("hud.alpha").keybind()==30&&saves[0]==1,
            "captured key persisted once through original UI gateway");
        rejects(()->actions.bind("hud.alpha",13),
            "duplicate bind is rejected without changing old assignment");
        rejects(()->actions.bind("hud.alpha",ReservedKeys.CLICK_GUI),
            "reserved F7 remains invalid for keyboard capture");
        ok(r.get("hud.alpha").keybind()==30&&saves[0]==1,
            "invalid shortcut binding never saves");
        // ESC cancels in Forge without issuing any action; Backspace/Delete
        // commits keycode 0 through the same validated gateway as mouse.
        actions.bind("hud.alpha",0);
        ok(r.get("hud.alpha").keybind()==0&&saves[0]==2,
            "Backspace clears keybind and saves exactly once");
        actions.bind("hud.alpha",48);
        ok(r.get("hud.alpha").keybind()==48&&saves[0]==3,
            "a later key can be bound after clearing");
        ok(r.get("hud.beta").keybind()==13,
            "no unrelated module keybind is affected");
        AtomicBoolean denied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try{actions.bind("hud.alpha",55);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(denied.get()&&r.get("hud.alpha").keybind()==48&&saves[0]==3,
            "off-owner mutation cannot bind or persist");
        r.shutdown();
    }
    public static void main(String[] args){
        focusPolicy();actionsAndConflict();
        ok(checks>140000,"substantial keyboard bind ownership test");
        System.out.println("GUI_KEYBIND_CAPTURE_PASS assertions="+checks);
    }
}
