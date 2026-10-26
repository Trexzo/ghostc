package dev.ghostfusion.tests;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.GameEvent;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.PacketCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline lifecycle and intent contracts. Does not claim native-game acceptance. */
public final class PanicTests {
    private static int assertions;
    private static void ok(boolean condition, String reason) {
        assertions++;
        if(!condition)throw new AssertionError(reason);
    }
    private static void rejects(Runnable task, String reason) {
        boolean denied=false;
        try { task.run(); } catch(RuntimeException expected) { denied=true; }
        ok(denied,reason);
    }
    private static void lifecycleAndPackets() throws Exception {
        final HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-panic-lifecycle-"));
        host.worldPresence(true);
        final ModuleRegistry registry=host.modules();
        final SessionServices s=host.services();
        registry.setEnabled("combat.clicker",true);
        registry.setEnabled("combat.aimassist",true);
        registry.setEnabled("hud.target",true);
        final AtomicBoolean callbackBlocked=new AtomicBoolean(false);
        final AtomicBoolean lifecycleBlocked=new AtomicBoolean(false);
        final AtomicBoolean panicReentryBlocked=new AtomicBoolean(false);
        final GhostModule hostile=new GhostModule() {
            public String id(){return "test.panic_probe";}
            public String displayName(){return "Panic Probe";}
            public Category category(){return Category.UTILITY;}
            public void onEnable(){}
            public void onEvent(GameEvent event){}
            public void onDisable(){
                try { registry.setEnabled("combat.clicker",true); }
                catch(IllegalStateException expected){lifecycleBlocked.set(true);}
                try { PanicController.disableAll(host); }
                catch(IllegalStateException expected){panicReentryBlocked.set(true);}
            }
        };
        registry.register(hostile,0);
        registry.setEnabled(hostile.id(),true);
        final long epoch=s.generation(),tick=s.tick();
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,8,tick,2,epoch);
        s.rotations().request("combat.aimassist",RotationCoordinator.Scope.CAMERA,
            25.0f,10.0f,3,tick,2,epoch);
        s.inventory().submit("test.panic_probe",9,3,tick,2,epoch);
        s.packets().submit("test.panic_probe",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1,2,3},epoch,new PacketCoordinator.Completion() {
                public void complete(PacketCoordinator.Result result) {
                    ok(result==PacketCoordinator.Result.CANCELLED,
                       "panic cancels pending packet with a terminal receipt");
                    try { registry.setEnabled("combat.clicker",true); }
                    catch(IllegalStateException expected){callbackBlocked.set(true);}
                }
            });
        ok(s.actions().pending()==1 && s.rotations().pending()==1 &&
           s.inventory().queued()==1 && s.packets().metrics().queued==1,
           "before Panic, four independent intent types are pending");
        int disabled=PanicController.disableAll(host);
        ok(disabled>=5,"all enabled modules including status disabled");
        ok(callbackBlocked.get() && lifecycleBlocked.get() &&
           panicReentryBlocked.get(),
           "packet and lifecycle callbacks cannot reenable or recursively Panic");
        for(ModuleRegistry.Entry e:registry.all())
            ok(e.state()!=ModuleRegistry.State.ENABLED,
               "Panic disables every enabled module "+e.module().id());
        ok(s.actions().pending()==0 && s.rotations().pending()==0 &&
           s.inventory().queued()==0 && !s.inventory().active() &&
           s.packets().metrics().queued==0,
           "no pending action, camera, packet or inventory work survives");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
           ActionCoordinator.Phase.OUTPUT,tick,epoch)==null,
           "old attack cannot execute after disable");
        ok(s.rotations().decide(RotationCoordinator.Scope.CAMERA,tick,epoch,0f,0f).owner==null,
           "camera fallback remains native");
        ok(PanicController.disableAll(host)==0,"Panic is idempotent");
        ok(s.connected() && host.worldPresent(),"Panic does not disconnect Minecraft");
        host.close();
        ok(PanicController.disableAll(host)==0,"closed host is a no-op");
    }
    private static void profilesAndBinding() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-panic-profile-");
        HostRuntime host=new HostRuntime(folder);
        host.worldPresence(true);
        host.modules().setEnabled("combat.aimassist",true);
        host.modules().setEnabled("combat.clicker",true);
        host.uiActions().fraction("combat.aimassist","degrees_per_tick",0.6);
        final double original=host.aimDegreesPerTick();
        rejects(()->host.modules().bind("combat.aimassist",64),
            "F6 cannot be assigned to another feature");
        host.key(64,true,false);
        host.key(64,false,false);
        ok(host.modules().get("combat.clicker").state()==ModuleRegistry.State.ENABLED,
            "dedicated F6 cannot toggle a module through core keybind routing");
        int disabled=PanicController.disableAll(host);
        ok(disabled>=3 && !host.featureEnabled("combat.clicker") &&
           !host.featureEnabled("combat.aimassist"),
           "Panic suppresses both combat features in a live world");
        ok(host.aimDegreesPerTick()==original,
           "Panic preserves the existing aim slider settings");
        ok(Files.exists(folder.resolve("default.properties")),
           "Panic persists an ordinary profile; never erases it");
        host.close();
        HostRuntime restored=new HostRuntime(folder);
        ok(restored.aimDegreesPerTick()==original &&
           restored.modules().get("combat.clicker").state()==ModuleRegistry.State.DISABLED &&
           restored.modules().get("combat.aimassist").state()==ModuleRegistry.State.DISABLED,
           "disabling survives profile reload while numerical tuning survives");
        restored.worldPresence(true);
        ok(!restored.featureEnabled("combat.clicker"),
           "rejoining a world does not silently reenable the client");
        restored.modules().setEnabled("combat.clicker",true);
        ok(restored.featureEnabled("combat.clicker"),
           "explicit user re-enable after Panic is supported");
        restored.key(HostRuntime.DEFAULT_STATUS_KEY,true,false);
        restored.key(HostRuntime.DEFAULT_STATUS_KEY,false,false);
        ok(restored.modules().get("hud.status").state()==ModuleRegistry.State.ENABLED,
           "normal F8 and module hotkeys remain usable after Panic");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        lifecycleAndPackets();
        profilesAndBinding();
        ok(assertions>=18,"Panic contract assertions executed");
        System.out.println("PANIC_REGRESSION_PASS assertions="+assertions);
    }
}
