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
    /** R2.98: a pending keybind must yield to the explicit Ctrl+F
     * search shortcut while ordinary F/key capture remains functional. */
    private static void explicitSearchTransfer() {
        ok(GuiKeybindCapture.transferToSearch(true,true,true,true),
            "armed module capture yields to Ctrl+F");
        ok(!GuiKeybindCapture.transferToSearch(true,true,true,false),
            "plain F remains a legal keybind");
        ok(!GuiKeybindCapture.transferToSearch(true,true,false,true),
            "Ctrl plus unrelated key remains capturable");
        ok(!GuiKeybindCapture.transferToSearch(false,true,true,true),
            "no capture does not invent a focus transfer");
        ok(!GuiKeybindCapture.transferToSearch(true,false,true,true),
            "HUD page never routes module binding to search");
        Random rnd=new Random(2982026L);
        for(int i=0;i<160000;i++) {
            boolean armed=rnd.nextBoolean(),modules=rnd.nextBoolean();
            boolean f=rnd.nextBoolean(),ctrl=rnd.nextBoolean();
            boolean expected=armed&&modules&&f&&ctrl;
            ok(GuiKeybindCapture.transferToSearch(armed,modules,f,ctrl)==expected,
                "random keyboard focus ownership "+i);
        }
        ModuleRegistry registry=new ModuleRegistry();
        registry.register(new Dummy("hud.alpha"),12);
        final int[] saves={0};
        UiActions actions=new UiActions(registry,()->saves[0]++);
        int old=registry.get("hud.alpha").keybind();
        boolean transferred=GuiKeybindCapture.transferToSearch(true,true,true,true);
        if(!transferred)actions.bind("hud.alpha",33);
        ok(transferred && registry.get("hud.alpha").keybind()==old && saves[0]==0,
            "search transfer performs no hidden keybind write");
        boolean captured=GuiKeybindCapture.transferToSearch(true,true,true,false);
        if(!captured)actions.bind("hud.alpha",33);
        ok(!captured && registry.get("hud.alpha").keybind()==33 && saves[0]==1,
            "ordinary F capture still uses validated persistence gateway");
        registry.shutdown();
    }
    /** R3.00: Ctrl-down cannot prematurely commit an active capture.
     * Ctrl-release without F still binds Ctrl, and Ctrl+F never saves. */
    private static void controlChordAndRelease() {
        ok(GuiKeybindCapture.deferControl(true,true),
           "control-down is deferred during capture");
        ok(!GuiKeybindCapture.deferControl(true,false),
           "ordinary keys are not delayed");
        ok(!GuiKeybindCapture.deferControl(false,true),
           "no capture cannot create deferred bind");
        ok(GuiKeybindCapture.commitDeferredControl(true,29,29),
           "left Ctrl-alone commits on release");
        ok(GuiKeybindCapture.commitDeferredControl(true,157,157),
           "right Ctrl-alone commits on release");
        ok(!GuiKeybindCapture.commitDeferredControl(true,29,157),
           "other Ctrl release cannot commit wrong key");
        ok(!GuiKeybindCapture.commitDeferredControl(true,29,33),
           "F-down is not a control release");
        ok(!GuiKeybindCapture.commitDeferredControl(false,29,29),
           "focus transfer cancels deferred Ctrl release");
        ok(!GuiKeybindCapture.commitDeferredControl(true,0,29),
           "no pending control cannot commit");
        Random rnd=new Random(3002026L);
        int[] keys={0,29,157,33,45,56,54};
        for(int i=0;i<150000;i++) {
            boolean armed=rnd.nextBoolean(),ctrl=rnd.nextBoolean();
            int pending=keys[rnd.nextInt(keys.length)];
            int released=keys[rnd.nextInt(keys.length)];
            ok(GuiKeybindCapture.deferControl(armed,ctrl)==(armed&&ctrl),
               "deferred key-down gate "+i);
            ok(GuiKeybindCapture.commitDeferredControl(armed,pending,released)==
                (armed&&pending!=0&&pending==released),
                "matching release admission "+i);
        }
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Dummy("hud.chord"),12);
        final int[] saves={0};
        UiActions actions=new UiActions(r,()->saves[0]++);
        boolean ctrlPending=GuiKeybindCapture.deferControl(true,true);
        ok(ctrlPending&&r.get("hud.chord").keybind()==12&&saves[0]==0,
           "Ctrl-down alone does not save a premature binding");
        boolean toSearch=GuiKeybindCapture.transferToSearch(true,true,true,true);
        if(!toSearch)actions.bind("hud.chord",33);
        ok(toSearch&&saves[0]==0&&r.get("hud.chord").keybind()==12,
           "Ctrl then F transfers to search without profile mutation");
        ok(!GuiKeybindCapture.commitDeferredControl(false,29,29),
           "released Ctrl after search transfer cannot bind");
        if(GuiKeybindCapture.commitDeferredControl(true,29,29))
            actions.bind("hud.chord",29);
        ok(r.get("hud.chord").keybind()==29&&saves[0]==1,
           "standalone left Ctrl remains bindable after release");
        if(GuiKeybindCapture.commitDeferredControl(true,157,157))
            actions.bind("hud.chord",157);
        ok(r.get("hud.chord").keybind()==157&&saves[0]==2,
           "standalone right Ctrl remains bindable after release");
        actions.bind("hud.chord",33);
        ok(r.get("hud.chord").keybind()==33&&saves[0]==3,
           "ordinary F remains bindable without Ctrl");
        r.shutdown();
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
    /** Repeated capture of an existing binding is a validated no-op. */
    private static void unchangedBindings() {
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Dummy("hud.alpha"),12);
        r.register(new Dummy("hud.beta"),13);
        final int[] saves={0};
        UiActions actions=new UiActions(r,()->saves[0]++);
        actions.bind("hud.alpha",12);
        ok(saves[0]==0 && r.get("hud.alpha").keybind()==12,
            "rebinding existing default key does not persist");
        actions.bind("hud.alpha",0);
        ok(saves[0]==1,"first unbind persists once");
        actions.bind("hud.alpha",0);
        ok(saves[0]==1,"repeated unbind is idempotent");
        actions.bind("hud.alpha",30);
        ok(saves[0]==2,"new key persists once");
        actions.bind("hud.alpha",30);
        ok(saves[0]==2,"recapturing same key does not save");
        rejects(()->actions.bind("hud.alpha",13),
            "duplicate key still rejected");
        rejects(()->actions.bind("hud.alpha",ReservedKeys.CLICK_GUI),
            "reserved key still rejected");
        rejects(()->actions.bind("hud.alpha",-1),
            "negative key still rejected");
        rejects(()->actions.bind("missing.module",30),
            "invalid module still rejected");
        ok(saves[0]==2 && r.get("hud.alpha").keybind()==30,
            "rejected binds do not mutate or save");
        Random random=new Random(2732026L);
        final int[] keys={0,12,30,48,55};
        int expected=30,writes=2;
        for(int i=0;i<60000;i++){
            int key=keys[random.nextInt(keys.length)];
            if(key!=expected)writes++;
            actions.bind("hud.alpha",key);
            expected=key;
            ok(r.get("hud.alpha").keybind()==expected,
                "random bind matches requested key "+i);
            ok(saves[0]==writes,
                "exact one save per changed binding "+i);
        }
        final int savedBefore=saves[0],oldKey=expected;
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread t=new Thread(()->{
            try{actions.bind("hud.alpha",oldKey);}
            catch(IllegalStateException ex){denied.set(true);}
        });
        t.start();
        try{t.join();}catch(InterruptedException ex){throw new AssertionError(ex);}
        ok(denied.get() && saves[0]==savedBefore &&
            r.get("hud.alpha").keybind()==oldKey,
            "unchanged binding still requires owner thread");
        r.shutdown();
        boolean closed=false;
        try{actions.bind("hud.alpha",oldKey);}
        catch(IllegalStateException expectedFailure){closed=true;}
        ok(closed && saves[0]==savedBefore,
            "shutdown registry still rejects unchanged binds");
    }
    public static void main(String[] args){
        focusPolicy();explicitSearchTransfer();controlChordAndRelease();
        actionsAndConflict();unchangedBindings();
        ok(checks>140000,"substantial keyboard bind ownership test");
        System.out.println("GUI_KEYBIND_CAPTURE_PASS assertions="+checks);
    }
}
