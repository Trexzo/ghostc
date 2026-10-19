package dev.ghostfusion.tests;

import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.InputRouter;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.core.ProfileStore;
import dev.ghostfusion.core.ReservedKeys;
import dev.ghostfusion.core.Setting;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.ui.UiActions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;

/** Reserved F7 is unusable as a module key, including in persisted profiles. */
public final class ReservedKeyTests {
    private static int checks;
    private static void ok(boolean yes,String reason) {
        checks++;
        if (!yes) throw new AssertionError(reason);
    }
    private static void rejects(Runnable action,String reason) {
        boolean rejected=false;
        try {action.run();}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,reason);
    }
    private static final class Dummy implements GhostModule {
        final String id;
        int enabled;
        Dummy(String id){this.id=id;}
        public String id(){return id;}
        public String displayName(){return id;}
        public Category category(){return Category.UTILITY;}
        public void onEnable(){enabled++;}
        public void onDisable(){}
        public void onEvent(GameEvent event){}
    }

    private static void registryAndRouter() {
        ok(ReservedKeys.CLICK_GUI==65,"F7 is LWJGL 2 keycode 65");
        ok(ReservedKeys.reserved(65) && !ReservedKeys.reserved(66) &&
           !ReservedKeys.reserved(0),"exact reservation");
        ModuleRegistry registry=new ModuleRegistry();
        Dummy status=new Dummy("hud.status"),other=new Dummy("test.other");
        registry.register(status,66);
        registry.register(other,30);
        ok(registry.get("hud.status").keybind()==66,"F8 status remains valid");
        rejects(()->ModuleRegistry.validateKey(65),"F7 rejected at central validation");
        rejects(()->registry.register(new Dummy("test.bad"),65),"new module cannot reserve F7");
        rejects(()->registry.bind("test.other",65),"rebind cannot reserve F7");
        ok(registry.get("test.other").keybind()==30,"failed rebind leaves previous key");
        ok(registry.all().size()==2,"failed registration cannot leave partial module");
        InputRouter router=new InputRouter(registry);
        router.keyEvent(65,true,false);
        router.keyEvent(65,false,false);
        ok(status.enabled==0 && other.enabled==0,"F7 cannot toggle modules");
        router.keyEvent(66,true,false);
        ok(status.enabled==1,"F8 status still toggles");
        router.keyEvent(66,true,false);
        ok(status.enabled==1,"auto-repeat suppressed");
        router.keyEvent(66,false,false);
        router.keyEvent(66,true,true);
        ok(status.enabled==1,"F8 module key suppressed in GUI");
        registry.shutdown();
    }
    private static void uiAndProfiles() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-f7-profile");
        ModuleRegistry registry=new ModuleRegistry();
        Dummy a=new Dummy("hud.status"),b=new Dummy("test.optional");
        Setting.Bool shadow=new Setting.Bool("shadow",true);
        registry.register(a,66,shadow);
        registry.register(b,30);
        registry.setEnabled("hud.status",true);
        final int[] saves={0};
        UiActions ui=new UiActions(registry,()->saves[0]++);
        rejects(()->ui.bind("test.optional",65),"GUI mutation rejects F7");
        ok(saves[0]==0,"failed key bind never persists");
        ok(registry.get("test.optional").keybind()==30,"GUI rejection is atomic");
        ui.bind("test.optional",33);
        ok(saves[0]==1 && registry.get("test.optional").keybind()==33,
           "valid reassignment persists");
        ProfileStore store=new ProfileStore(dir,registry);
        store.save("default");
        Path file=dir.resolve("default.properties");
        Properties original=new Properties();
        try(InputStream in=Files.newInputStream(file)){original.load(in);}
        for(String field:new String[]{"module.hud.status.key","module.test.optional.key"}) {
            Properties modified=new Properties();
            modified.putAll(original);
            modified.setProperty(field,"65");
            modified.setProperty("module.hud.status.setting.shadow","false");
            modified.setProperty("module.test.optional.enabled","true");
            try(OutputStream out=Files.newOutputStream(file)){
                modified.store(out,"negative reserved bind fixture");
            }
            rejects(()->{
                try{store.load("default");}
                catch(java.io.IOException e){throw new AssertionError(e);}
            },"profile cannot load reserved F7 in "+field);
            ok(registry.get("hud.status").state()==ModuleRegistry.State.ENABLED,
               "invalid file does not disable existing status module");
            ok(registry.get("test.optional").state()==ModuleRegistry.State.DISABLED,
               "invalid file does not enable unrelated module");
            ok(registry.get("hud.status").keybind()==66 &&
               registry.get("test.optional").keybind()==33,
               "invalid file never mutates keys");
            ok(shadow.get(),"invalid file never mutates settings");
        }
        try(OutputStream out=Files.newOutputStream(file)){
            original.store(out,"restore valid fixture");
        }
        registry.bind("test.optional",41);
        store.load("default");
        ok(registry.get("test.optional").keybind()==33,"valid profile still restores");
        ok(registry.get("hud.status").keybind()==66,"F8 preserved on load");
        registry.shutdown();
    }
    private static void physicalGate() {
        PhysicalInputGate gate=new PhysicalInputGate();
        ok(!gate.allowed(1,true,true,false,false,-100,-99),"release admission");
        ok(!gate.allowed(1,true,true,false,true,ReservedKeys.CLICK_GUI,-99),
           "F7 never becomes physical attack");
        ok(gate.allowed(1,true,true,false,true,-100,-99),
           "normal mouse attack remains valid after release");
    }
    private static void randomizedRebinds() {
        ModuleRegistry r=new ModuleRegistry();
        r.register(new Dummy("hud.status"),66);
        r.register(new Dummy("test.extra"),0);
        Random random=new Random(20171019L);
        for(int i=0;i<10000;i++) {
            int key=random.nextInt(256);
            int before=r.get("test.extra").keybind();
            boolean shouldReject=key==ReservedKeys.CLICK_GUI || key==66;
            if(shouldReject) {
                rejects(()->r.bind("test.extra",key),"reserved/duplicate random key "+i);
                ok(r.get("test.extra").keybind()==before,"rejection atomic "+i);
            } else {
                r.bind("test.extra",key);
                ok(r.get("test.extra").keybind()==key,"random key accepted "+i);
            }
            ok(r.get("hud.status").keybind()==66,
               "unrelated F8 key remains owned "+i);
        }
        r.shutdown();
    }
    public static void main(String[] args) throws Exception {
        registryAndRouter();
        uiAndProfiles();
        physicalGate();
        randomizedRebinds();
        ok(checks>19000,"broad reserved key replay");
        System.out.println("RESERVED_KEY_PASS assertions="+checks);
    }
}