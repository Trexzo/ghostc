package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiSliderDrag;
import dev.ghostfusion.ui.UiActions;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.features.FeatureModule;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic mouse capture, clamping, mutation and one-save release. */
public final class GuiSliderDragTests {
    private static int checks;
    private static void ok(boolean yes,String message) {
        checks++;if(!yes)throw new AssertionError(message);
    }
    private static void rejects(Runnable task,String reason) {
        boolean blocked=false;
        try {task.run();}catch(IllegalArgumentException | IllegalStateException expected){blocked=true;}
        ok(blocked,reason);
    }
    private static void stateAndGeometry() throws Exception {
        GuiSliderDrag drag=new GuiSliderDrag();
        ok(!drag.active() && !drag.finish(),"initial state inactive");
        rejects(()->drag.fraction(0),"fraction without pointer capture refused");
        rejects(()->drag.changed(),"no dirty state without capture");
        rejects(()->drag.begin("", "range",0,50,800,600),"missing module ID");
        rejects(()->drag.begin("render.esp",null,0,50,800,600),"missing setting ID");
        rejects(()->drag.begin("render.esp","range",0,0,800,600),"zero-width slider");
        rejects(()->drag.begin("render.esp","range",0,40,0,600),"zero-width viewport");
        drag.begin("render.esp","range",200,100,800,600);
        ok(drag.active() && drag.sameViewport(800,600),"pointer capture active");
        ok(!drag.sameViewport(801,600) && !drag.sameViewport(800,601),
           "viewport resize invalidates captured geometry");
        ok("render.esp".equals(drag.moduleId()) && "range".equals(drag.settingId()),
           "captured stable setting identities");
        ok(drag.fraction(200)==0.0 && drag.fraction(250)==0.5 &&
           drag.fraction(300)==1.0,"endpoints and center");
        ok(drag.fraction(-1000000)==0.0 && drag.fraction(1000000)==1.0,
           "clamp pointer outside track");
        ok(drag.fraction(Integer.MIN_VALUE)==0.0 &&
           drag.fraction(Integer.MAX_VALUE)==1.0,
           "extreme pixel coordinates cannot overflow track subtraction");
        rejects(()->drag.begin("a","b",0,30,800,600),"no nested captures");
        ok(!drag.finish(),"unchanged capture needs no disk save");
        ok(!drag.active(),"capture revoked");
        drag.begin("render.esp","range",200,100,800,600);
        drag.changed();
        ok(drag.finish() && !drag.finish(),"save signal fired exactly once");
        rejects(()->drag.moduleId(),"no stale module after release");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{drag.active();}catch(IllegalStateException expected){blocked.set(true);}
        });
        foreign.start();foreign.join();
        ok(blocked.get(),"mouse capture owner-thread only");
    }
    private static void previewAndCommit() {
        ModuleRegistry modules=new ModuleRegistry();
        Setting.Number rate=new Setting.Number("range",10.0,2.0,24.0);
        modules.register(new FeatureModule("render.test","Render Test",GhostModule.Category.RENDER),
                         0,rate);
        final int[] saves={0};
        UiActions actions=new UiActions(modules,()->saves[0]++);
        GuiSliderDrag drag=new GuiSliderDrag();
        drag.begin("render.test","range",100,200,640,480);
        for(int x=100;x<=300;x+=2) {
            boolean changed=actions.previewFraction(drag.moduleId(),drag.settingId(),drag.fraction(x));
            if(changed)drag.changed();
            ok(saves[0]==0,"mouse movement cannot write profile");
        }
        ok(rate.get()==24.0,"drag fully reaches maximum");
        ok(drag.finish(),"modified drag requires commit");
        actions.commitSlider();
        ok(saves[0]==1,"one profile write on pointer release");
        drag.begin("render.test","range",100,200,640,480);
        ok(!actions.previewFraction(drag.moduleId(),drag.settingId(),1.0),
           "identical value is a no-op");
        ok(!drag.finish() && saves[0]==1,"no writes for unchanged drag");
        actions.fraction("render.test","range",0);
        ok(rate.get()==2.0 && saves[0]==2,"legacy click API persists unchanged");
        rejects(()->actions.previewFraction("render.test","bad",0.5),
                "unknown slider identity rejected");
        rejects(()->actions.previewFraction("render.test","range",Double.NaN),
                "invalid pointer fraction rejected");
    }
    private static void revertToInitialValue() {
        ModuleRegistry modules=new ModuleRegistry();
        Setting.Number value=new Setting.Number("range",0.25,0.0,1.0);
        modules.register(new FeatureModule("render.roundtrip","Round Trip",
            GhostModule.Category.RENDER),0,value);
        final int[] saves={0};
        UiActions actions=new UiActions(modules,()->saves[0]++);
        GuiSliderDrag drag=new GuiSliderDrag();
        drag.begin("render.roundtrip","range",100,100,640,480,value.get());
        ok(!drag.finish(),"initial captured value needs no disk write");
        drag.begin("render.roundtrip","range",100,100,640,480,value.get());
        actions.previewFraction(drag.moduleId(),drag.settingId(),0.75);
        drag.observeValue(value.get());
        ok(value.get()==0.75,"preview applies temporary new value");
        actions.previewFraction(drag.moduleId(),drag.settingId(),0.25);
        drag.observeValue(value.get());
        ok(!drag.finish() && saves[0]==0 && value.get()==0.25,
            "drag round trip back to baseline writes no profile");
        drag.begin("render.roundtrip","range",100,100,640,480,value.get());
        actions.previewFraction(drag.moduleId(),drag.settingId(),0.75);
        drag.observeValue(value.get());
        ok(drag.finish(),"real final numeric difference persists");
        actions.commitSlider();
        ok(saves[0]==1 && value.get()==0.75,"changed gesture commits once");
        drag.begin("render.roundtrip","range",100,100,640,480,value.get());
        actions.previewFraction(drag.moduleId(),drag.settingId(),0.5);
        drag.observeValue(value.get());
        ok(drag.finish(),"interrupted dirty capture requests persistence");
        actions.commitSlider();
        ok(saves[0]==2 && value.get()==0.5,"interrupted change persists once");
        rejects(()->drag.observeValue(0.5),"observation after finish rejected");
        rejects(()->drag.begin("render.roundtrip","range",100,100,640,480,
                Double.NaN),"nonfinite initial numeric value rejected");
        drag.begin("render.roundtrip","range",100,100,640,480,value.get());
        rejects(()->drag.observeValue(Double.POSITIVE_INFINITY),
                "invalid observation cannot corrupt baseline");
        ok(!drag.finish(),"rejected observation leaves clean state");
        modules.shutdown();
    }
    private static void randomizedBaseline() {
        Random random=new Random(2752026L);
        GuiSliderDrag drag=new GuiSliderDrag();
        for(int scenario=0;scenario<75000;scenario++) {
            double initial=(random.nextInt(2001)-1000)/100.0;
            drag.begin("hud.value","range",0,100,800,600,initial);
            double actual=initial;
            int events=2+random.nextInt(9);
            for(int k=0;k<events;k++) {
                // Restore baseline frequently, including after prior mutations.
                actual=(k==events-1 && random.nextBoolean()) ?
                    initial : (random.nextInt(2001)-1000)/100.0;
                drag.observeValue(actual);
                ok(drag.active(),"observations retain active pointer capture");
            }
            boolean changed=Double.compare(actual,initial)!=0;
            ok(drag.finish()==changed,
                "only final difference triggers a persistence opportunity "+scenario);
            ok(!drag.active()&&!drag.finish(),
                "capture revoked and cannot commit twice "+scenario);
        }
    }
    private static void randomized() {
        Random random=new Random(2412026L);
        GuiSliderDrag drag=new GuiSliderDrag();
        int dirty=0,saves=0;
        for(int scenario=0;scenario<16000;scenario++) {
            int start=random.nextInt(3000)-1500;
            int track=1+random.nextInt(600);
            int width=1+random.nextInt(3840);
            int height=1+random.nextInt(2160);
            drag.begin("module.id","setting.id",start,track,width,height);
            int events=1+random.nextInt(20);
            boolean observed=false;
            for(int k=0;k<events;k++) {
                int mx=random.nextBoolean()?random.nextInt(4500)-2000:
                    random.nextBoolean()?Integer.MAX_VALUE:Integer.MIN_VALUE;
                double actual=drag.fraction(mx);
                double expected=Math.max(0.0,Math.min(1.0,((long)mx-(long)start)/(double)track));
                ok(actual==expected && Double.isFinite(actual),"clamped pointer fraction");
                if(k%3==0){drag.changed();observed=true;}
            }
            if(observed)dirty++;
            boolean needSave=drag.finish();
            if(needSave)saves++;
            ok(needSave==observed && !drag.active() && !drag.finish(),
                "exactly one dirty finish per simulated drag");
        }
        ok(dirty==saves && dirty==16000,"one commit opportunity per changed gesture");
    }
    public static void main(String[] args) throws Exception {
        stateAndGeometry();previewAndCommit();revertToInitialValue();randomizedBaseline();randomized();
        ok(checks>160000,"substantial bounded pointer capture replay");
        System.out.println("GUI_SLIDER_DRAG_PASS assertions="+checks);
    }
}
