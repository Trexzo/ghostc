package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.ui.GuiSettingKeyboard;
import dev.ghostfusion.ui.NumericSliderScale;
import dev.ghostfusion.ui.UiActions;
import java.util.Random;

/** R2.59: typed GUI settings are navigable and adjustable without a mouse. */
public final class GuiSettingKeyboardTests {
    private static int checks;
    private static void ok(boolean valid,String what) {
        checks++;
        if(!valid)throw new AssertionError(what);
    }
    private static void rejects(Runnable work,String why) {
        boolean thrown=false;
        try {work.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,why);
    }
    private static final class Dummy implements GhostModule {
        public String id(){return "gui.keyboard";}
        public String displayName(){return "Keyboard";}
        public Category category(){return Category.RENDER;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }
    private static void boundaries() {
        ok(GuiSettingKeyboard.next(-1,0,1)==-1,"no settings means no focus");
        ok(GuiSettingKeyboard.next(-1,6,1)==0,"tab starts on first setting");
        ok(GuiSettingKeyboard.next(-1,6,-1)==5,"shift-tab starts at last");
        ok(GuiSettingKeyboard.next(5,6,1)==0,"tab cycles forward");
        ok(GuiSettingKeyboard.next(0,6,-1)==5,"shift-tab cycles back");
        ok(GuiSettingKeyboard.next(300,4,1)==0,"stale focus is bounded");
        ok(GuiSettingKeyboard.reveal(4,0,7,3)==2,"focus scrolls down to reveal");
        ok(GuiSettingKeyboard.reveal(1,4,7,3)==1,"focus scrolls up to reveal");
        ok(GuiSettingKeyboard.reveal(4,3,7,3)==3,"visible focus leaves scroll unchanged");
        ok(GuiSettingKeyboard.reveal(-1,500,7,3)==4,"unfocused viewport bounded");
        ok(GuiSettingKeyboard.reveal(4,-9,7,0)==0,
            "no visible rows does not produce negative offsets");
        rejects(()->GuiSettingKeyboard.next(0,-1,1),"negative count invalid");
        rejects(()->GuiSettingKeyboard.next(0,3,0),"zero tab direction invalid");
        rejects(()->GuiSettingKeyboard.reveal(0,0,-1,3),"negative row count invalid");
        rejects(()->GuiSettingKeyboard.reveal(0,0,3,-1),"negative viewport invalid");
        rejects(()->GuiSettingKeyboard.nudgeFraction(1,0,2,0),
            "zero numeric direction invalid");
        rejects(()->GuiSettingKeyboard.nudgeFraction(Double.NaN,0,2,1),
            "nan numeric setting rejected");
        ok(GuiSettingKeyboard.nudgeFraction(0,0,100,-1)==0,
            "left arrow stops at numeric minimum");
        ok(GuiSettingKeyboard.nudgeFraction(100,0,100,1)==1,
            "right arrow stops at numeric maximum");
        ok(GuiSettingKeyboard.nudgeFraction(5,5,5,1)==.05,
            "constant numeric range returns harmless fraction");
    }
    private static void precisionAndEndpoints(){
        ok(Math.abs(GuiSettingKeyboard.nudgeFraction(50,0,100,1,true)-.51)<1e-12,
            "Shift+Right uses one percent");
        ok(Math.abs(GuiSettingKeyboard.nudgeFraction(50,0,100,-1,true)-.49)<1e-12,
            "Shift+Left uses one percent");
        ok(Math.abs(GuiSettingKeyboard.nudgeFraction(50,0,100,1,false)-.55)<1e-12,
            "plain Right retains five percent");
        ok(GuiSettingKeyboard.endpointFraction(0,100,false)==0,
            "Home selects the exact minimum fraction");
        ok(GuiSettingKeyboard.endpointFraction(0,100,true)==1,
            "End selects the exact maximum fraction");
        ok(GuiSettingKeyboard.nudgeFraction(0,0,100,-1,true)==0,
            "fine adjustment cannot underflow");
        ok(GuiSettingKeyboard.nudgeFraction(100,0,100,1,true)==1,
            "fine adjustment cannot overflow");
        rejects(()->GuiSettingKeyboard.endpointFraction(Double.NaN,1,false),
            "invalid endpoint bound refused");
        Random random=new Random(2702026L);
        for(int i=0;i<90000;i++){
            double min=random.nextDouble()*200-100;
            double max=min+random.nextDouble()*300+.01;
            double fraction=random.nextDouble();
            double value=NumericSliderScale.value(min,max,fraction);
            int direction=random.nextBoolean()?1:-1;
            boolean fine=random.nextBoolean();
            double actual=GuiSettingKeyboard.nudgeFraction(value,min,max,direction,fine);
            double expected=Math.max(0,Math.min(1,
                NumericSliderScale.fraction(value,min,max)+direction*(fine?.01:.05)));
            ok(Double.isFinite(actual)&&Math.abs(actual-expected)<1e-12,
                "seeded fine/coarse step matches independent clamp "+i);
            ok(GuiSettingKeyboard.endpointFraction(min,max,false)==0 &&
               GuiSettingKeyboard.endpointFraction(min,max,true)==1,
                "seeded Home/End matches bounds "+i);
        }
    }
    private static void typedActions() {
        ModuleRegistry modules=new ModuleRegistry();
        Setting.Bool enabled=new Setting.Bool("enabled",false);
        Setting.Choice mode=new Setting.Choice("mode","classic","classic","glass","plain");
        Setting.Number amount=new Setting.Number("amount",20,0,100);
        modules.register(new Dummy(),0,enabled,mode,amount);
        final int[] saved={0};
        UiActions actions=new UiActions(modules,()->saved[0]++);
        int focus=GuiSettingKeyboard.next(-1,3,1);
        ok(focus==0,"bool row keyboard-selected");
        actions.bool("gui.keyboard","enabled");
        ok(enabled.get(),"space activates Bool via existing actions");
        focus=GuiSettingKeyboard.next(focus,3,1);
        ok(focus==1,"choice row keyboard-selected");
        actions.cycle("gui.keyboard","mode");
        ok("glass".equals(mode.get()),"enter cycles typed choices");
        focus=GuiSettingKeyboard.next(focus,3,1);
        ok(focus==2,"number row keyboard-selected");
        double before=amount.get();
        double fraction=GuiSettingKeyboard.nudgeFraction(before,amount.min(),amount.max(),1);
        actions.fraction("gui.keyboard","amount",fraction);
        ok(amount.get()>before&&amount.get()<=100,"right arrow uses validated fraction action");
        fraction=GuiSettingKeyboard.nudgeFraction(amount.get(),amount.min(),amount.max(),-1);
        actions.fraction("gui.keyboard","amount",fraction);
        ok(Math.abs(amount.get()-before)<1e-9,
            "left arrow reverses one step in normal finite range");
        ok(saved[0]==4,"all mutations use one persistence action per press");
        modules.shutdown();
    }
    private static void replay() {
        Random r=new Random(2592026L);
        for(int round=0;round<45000;round++) {
            int count=r.nextInt(45);
            int rows=r.nextInt(12);
            int focus=-1;
            int start=r.nextInt(200)-100;
            int steps=1+r.nextInt(6);
            for(int k=0;k<steps;k++) {
                int direction=r.nextBoolean()?1:-1;
                focus=GuiSettingKeyboard.next(focus,count,direction);
                start=GuiSettingKeyboard.reveal(focus,start,count,rows);
                ok(start>=0&&start<=Math.max(0,count-rows),
                    "settings viewport always bounded "+round);
                ok(count==0?focus==-1:focus>=0&&focus<count,
                    "focus cannot leave selected setting list "+round);
                if(rows>0 && focus>=0)
                    ok(focus>=start&&focus<start+rows,
                        "focused setting remains visible "+round);
            }
            double min=-250+r.nextDouble()*50;
            double max=min+.001+r.nextDouble()*1500;
            double value=NumericSliderScale.value(min,max,r.nextDouble());
            int direction=r.nextBoolean()?1:-1;
            double fraction=GuiSettingKeyboard.nudgeFraction(value,min,max,direction);
            double result=NumericSliderScale.value(min,max,fraction);
            ok(fraction>=0&&fraction<=1&&Double.isFinite(result)&&
                result>=min&&result<=max,
                "bounded numeric keyboard edit "+round);
        }
    }
    public static void main(String[] args) {
        boundaries();precisionAndEndpoints();typedActions();replay();
        ok(checks>300000,"large keyboard settings regression replay");
        System.out.println("GUI_SETTING_KEYBOARD_PASS assertions="+checks);
    }
}
