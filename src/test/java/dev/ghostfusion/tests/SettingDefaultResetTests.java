package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.core.ProfileStore;
import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.ui.UiActions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.65: one focused typed setting resets to its actual constructor default. */
public final class SettingDefaultResetTests {
    private static int checks;
    private static void ok(boolean condition,String message){
        checks++;if(!condition)throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        boolean failed=false;
        try {action.run();}catch(IllegalArgumentException expected){failed=true;}
        ok(failed,message);
    }
    private static final class Dummy implements GhostModule {
        public String id(){return "test.defaults";}
        public String displayName(){return "Default restore test";}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }
    private static void typedGateway(){
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool flag=new Setting.Bool("flag",true);
        Setting.Choice choice=new Setting.Choice("style","classic","classic","glass","minimal");
        Setting.Number scale=new Setting.Number("scale",1.25,.25,2.0);
        registry.register(new Dummy(),0,flag,choice,scale);
        final int[] saves={0};
        UiActions actions=new UiActions(registry,()->saves[0]++);
        ok(!actions.reset("test.defaults","flag") && saves[0]==0,
            "default Bool is a no-op with no save");
        ok(!actions.reset("test.defaults","style") && saves[0]==0,
            "default Choice is a no-op with no save");
        ok(!actions.reset("test.defaults","scale") && saves[0]==0,
            "default Number is a no-op with no save");
        actions.bool("test.defaults","flag");
        actions.cycle("test.defaults","style");
        actions.fraction("test.defaults","scale",1.0);
        ok(!flag.get() && "glass".equals(choice.get()) && scale.get()==2,
            "all three types can deviate from defaults");
        ok(actions.reset("test.defaults","flag"),"Bool reset reports mutation");
        ok(flag.get() && "glass".equals(choice.get()) && scale.get()==2,
            "single reset does not modify sibling settings");
        ok(actions.reset("test.defaults","style"),"Choice reset reports mutation");
        ok("classic".equals(choice.get()),"Choice constructor value restored");
        ok(actions.reset("test.defaults","scale"),"Number reset reports mutation");
        ok(scale.get()==1.25 && saves[0]==6,"one save per real reset");
        ok(!actions.reset("test.defaults","scale") && saves[0]==6,
            "repeated reset never persists unchanged Number");
        rejects(()->actions.reset("test.defaults","missing"),"unknown setting rejected");
        rejects(()->actions.reset("test.unknown","flag"),"unknown module rejected");
        ok(saves[0]==6,"failed reset never saves");
        AtomicBoolean denied=new AtomicBoolean();
        Thread offOwner=new Thread(()->{
            try{actions.reset("test.defaults","flag");}
            catch(IllegalStateException expected){denied.set(true);}
        });
        offOwner.start();
        try{offOwner.join();}catch(InterruptedException ex){throw new AssertionError(ex);}
        ok(denied.get() && saves[0]==6,"off-thread resets cannot persist");
        registry.shutdown();
    }
    private static void randomized(){
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool bool=new Setting.Bool("bool",false);
        Setting.Choice choice=new Setting.Choice("style","a","a","b","c");
        Setting.Number number=new Setting.Number("number",.75,0,1);
        registry.register(new Dummy(),0,bool,choice,number);
        final int[] saves={0};
        UiActions ui=new UiActions(registry,()->saves[0]++);
        Random random=new Random(2652026L);
        int resetWrites=0;
        for(int i=0;i<40000;i++){
            int type=random.nextInt(3);
            if(type==0){
                bool.setFromText(Boolean.toString(random.nextBoolean()));
                boolean changed=bool.get();
                ok(ui.reset("test.defaults","bool")==changed,
                    "Bool default parity "+i);
                ok(!bool.get(),"Bool reset to false "+i);
                if(changed)resetWrites++;
            } else if(type==1){
                String selected=random.nextInt(3)==0?"a":
                    random.nextBoolean()?"b":"c";
                choice.setFromText(selected);
                boolean changed=!"a".equals(selected);
                ok(ui.reset("test.defaults","style")==changed,
                    "Choice default parity "+i);
                ok("a".equals(choice.get()),"Choice default exact "+i);
                if(changed)resetWrites++;
            } else {
                double altered=random.nextInt(5)*.25;
                number.setFromText(Double.toString(altered));
                boolean changed=altered!=.75;
                ok(ui.reset("test.defaults","number")==changed,
                    "Number default parity "+i);
                ok(number.get()==.75,"Number default exact "+i);
                if(changed)resetWrites++;
            }
            ok(saves[0]==resetWrites,
                "every real reset saves once; repeated defaults never save "+i);
        }
        registry.shutdown();
    }
    /** Sparse profiles must be independent of previous live setting edits. */
    private static void sparseProfileDefaults() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r313-sparse-profiles");
        ModuleRegistry registry=new ModuleRegistry();
        Setting.Bool flag=new Setting.Bool("flag",true);
        Setting.Choice style=new Setting.Choice("style","classic",
            "classic","glass","minimal");
        Setting.Number scale=new Setting.Number("scale",1.25,.25,2.0);
        registry.register(new Dummy(),44,flag,style,scale);
        ProfileStore store=new ProfileStore(dir,registry);
        Path path=dir.resolve("sparse.properties");
        for(int i=0;i<512;i++) {
            java.util.Properties p=new java.util.Properties();
            p.setProperty("schema","1");
            p.setProperty("module.test.defaults.key","44");
            p.setProperty("module.test.defaults.enabled","false");
            int present=i%4;
            if(present==0)p.setProperty("module.test.defaults.setting.flag","false");
            if(present==1)p.setProperty("module.test.defaults.setting.style","minimal");
            if(present==2)p.setProperty("module.test.defaults.setting.scale","0.75");
            if((i&1)==0)ConfigIntegrity.seal(p);
            try(java.io.OutputStream out=Files.newOutputStream(path)) {
                p.store(out,"sparse fixture");
            }
            flag.setFromText((i&1)==0?"true":"false");
            style.setFromText("glass");
            scale.setFromText("2.0");
            store.load("sparse");
            ok(flag.get()==(present!=0),"missing flag uses constructor default "+i);
            ok((present==1?"minimal":"classic").equals(style.get()),
               "missing choice uses constructor default "+i);
            ok(scale.get()==(present==2?.75:1.25),
               "missing number uses constructor default "+i);
            ok(registry.get("test.defaults").keybind()==44,
               "sparse legacy binding is preserved "+i);
        }
        // A sparse sealed backup must also recover using defaults, not live
        // settings mutated after the backup was written.
        java.util.Properties recovery=new java.util.Properties();
        recovery.setProperty("schema","1");
        recovery.setProperty("module.test.defaults.key","44");
        recovery.setProperty("module.test.defaults.setting.flag","false");
        ConfigIntegrity.seal(recovery);
        Path backup=dir.resolve("restore.properties.bak");
        try(java.io.OutputStream out=Files.newOutputStream(backup)) {
            recovery.store(out,"trusted sparse recovery");
        }
        Files.write(dir.resolve("restore.properties"),
            "schema=1\nmodule.test.defaults.key=oops\n".getBytes(
                java.nio.charset.StandardCharsets.ISO_8859_1));
        flag.setFromText("true");
        style.setFromText("glass");
        scale.setFromText("2.0");
        ok(store.loadRecovering("restore"),"sparse verified backup recovers");
        ok(!flag.get() && "classic".equals(style.get()) && scale.get()==1.25,
           "sparse recovery applies explicit and default values");
        ok(ConfigIntegrity.verify(ConfigIntegrity.read(
            dir.resolve("restore.properties"))),"recovered primary sealed");
        registry.shutdown();
    }
    private static void profileRoundTrip()throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r265-default");
        HostRuntime host=new HostRuntime(dir);
        ok(!host.targetDistance(),"existing Target HUD distance default is false");
        ok("classic".equals(host.targetSkin()),"existing Target HUD classic default");
        host.uiActions().bool("hud.target","show_distance");
        host.uiActions().cycle("hud.target","skin");
        host.uiActions().fraction("render.esp","red",1.0);
        ok(host.targetDistance() && "glass".equals(host.targetSkin()),
            "old profile supports edited settings");
        ok(host.uiActions().reset("hud.target","show_distance"),
            "actual host Boolean default restored");
        ok(host.uiActions().reset("hud.target","skin"),
            "actual host Choice default restored");
        ok(host.uiActions().reset("render.esp","red"),
            "actual host Number default restored");
        ok(!host.targetDistance() && "classic".equals(host.targetSkin()),
            "host live values match original defaults");
        host.persist();host.close();
        HostRuntime loaded=new HostRuntime(dir);
        ok(!loaded.targetDistance() && "classic".equals(loaded.targetSkin()),
            "reset state survives saved profile reload");
        ok(Math.abs(((Setting.Number)loaded.modules().get("render.esp")
            .settings().get("red")).get()-.62)<1e-9,
            "Number default survives profile reload");
        ok(!loaded.uiActions().reset("render.esp","red"),
            "fresh host does not rewrite already-default number");
        loaded.close();
    }
    public static void main(String[] args)throws Exception {
        typedGateway();randomized();sparseProfileDefaults();profileRoundTrip();
        ok(checks>120000,"typed default restores deeply covered");
        System.out.println("SETTING_DEFAULT_RESET_PASS checks="+checks);
    }
}
