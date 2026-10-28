package dev.ghostfusion.tests;

import dev.ghostfusion.core.ConfigIntegrity;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Denial;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Enabled;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Frame;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Resource;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Scene;
import dev.ghostfusion.features.ModuleInteractionReadout;
import dev.ghostfusion.host.RuntimeDiagnostics;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/** Decision reasons, anonymous opt-in readout, counter backup compatibility. */
public final class ModuleInteractionDiagnosticsTests {
    private static int checks;
    private static void ok(boolean good,String reason) {
        checks++;if(!good)throw new AssertionError(reason);
    }
    private static Enabled all() {
        return new Enabled(true,true,true,true,true,true,true,true,true,true);
    }
    private static void decisions() {
        ModuleInteractionStateMachine m=new ModuleInteractionStateMachine();
        Frame f=m.advance(1,0,false,Scene.NO_WORLD,false,all(),false);
        for(Resource r:Resource.values())
            ok(f.denial(r)==Denial.WORLD_UNAVAILABLE,
               "all enabled resources fenced by no-world state");
        f=m.advance(1,1,true,Scene.GAME,false,all(),true);
        for(Resource r:Resource.values())
            ok(f.denial(r)==Denial.WINDOW_INACTIVE,
               "window focus denial precedes ownership precedence");
        f=m.advance(1,2,true,Scene.GAME,true,all(),true);
        ok(f.denial(Resource.AUTO_SPRINT)==Denial.WTAP_RESERVED &&
           f.denial(Resource.JUMP_RESET)==Denial.WTAP_RESERVED &&
           f.denial(Resource.AUTO_ROD)==Denial.WTAP_RESERVED &&
           f.denial(Resource.WTAP)==Denial.NONE &&
           f.denial(Resource.VELOCITY)==Denial.NONE &&
           f.denial(Resource.COMBAT)==Denial.NONE,
           "WTap preempts AutoSprint and Jump Reset, not combat/velocity");
        ok(ModuleInteractionReadout.suppression(f).contains("WTap"),
           "same state machine decision drives diagnostic reason");
        f=m.advance(1,3,true,Scene.PLAYER_INVENTORY,true,all(),false);
        ok(f.denial(Resource.INVENTORY_MANAGER)==Denial.ARMOR_PRIORITY &&
           f.denial(Resource.AUTO_ARMOR)==Denial.NONE &&
           f.denial(Resource.CHEST_STEALER)==Denial.WRONG_SCREEN &&
           f.denial(Resource.COMBAT)==Denial.WRONG_SCREEN,
           "AutoArmor priority and distinct GUI denial codes");
        ok(ModuleInteractionReadout.suppression(f).contains("AutoArmor") &&
           ModuleInteractionReadout.ownership(f).contains("PLAYER_ARMOR"),
           "opt-in text describes actual action-grant state");
        f=m.advance(1,4,true,Scene.CHEST,true,all(),false);
        ok(f.denial(Resource.CHEST_STEALER)==Denial.NONE &&
           f.denial(Resource.AUTO_ARMOR)==Denial.WRONG_SCREEN &&
           f.denial(Resource.INVENTORY_MANAGER)==Denial.WRONG_SCREEN,
           "no player-container write while chest screen owns GUI");
        Enabled movementOnly=new Enabled(false,true,true,false,false,
            false,false,false);
        f=m.advance(1,5,true,Scene.OTHER_GUI,true,movementOnly,false);
        ok(f.denial(Resource.COMBAT)==Denial.DISABLED &&
           f.denial(Resource.BRIDGE)==Denial.WRONG_SCREEN &&
           ModuleInteractionReadout.suppression(f).contains("GUI open"),
           "movement-only setup still explains why bridge/velocity are held");
        f=m.advance(1,6,true,Scene.GAME,false,movementOnly,false);
        ok(f.denial(Resource.BRIDGE)==Denial.WINDOW_INACTIVE &&
           ModuleInteractionReadout.suppression(f).contains("window inactive"),
           "movement-only setup reports lost native input focus");
        Enabled none=new Enabled(false,false,false,false,false,false,false,false);
        f=m.advance(1,7,true,Scene.GAME,true,none,true);
        for(Resource r:Resource.values())
            ok(f.denial(r)==Denial.DISABLED,
               "disabled native resources cannot show as a focus conflict");
        ok("Grants: unavailable".equals(ModuleInteractionReadout.ownership(null)) &&
           "Held: no live state".equals(ModuleInteractionReadout.suppression(null)),
           "absent frame is a safe fixed-vocabulary no-op");
        boolean bad=false;
        try{f.denial(null);}catch(IllegalArgumentException expected){bad=true;}
        ok(bad,"null diagnostic resources rejected");
    }
    private static void seeded() {
        ModuleInteractionStateMachine m=new ModuleInteractionStateMachine();
        Random rng=new Random(3442027L);
        long generation=1,tick=0;
        for(int i=0;i<32000;i++) {
            if(i%191==0){generation++;tick=0;}
            Scene scene=Scene.values()[rng.nextInt(Scene.values().length)];
            boolean world=rng.nextBoolean(),foreground=rng.nextBoolean();
            boolean reserved=rng.nextBoolean();
            Enabled e=new Enabled(rng.nextBoolean(),rng.nextBoolean(),
                rng.nextBoolean(),rng.nextBoolean(),rng.nextBoolean(),
                rng.nextBoolean(),rng.nextBoolean(),rng.nextBoolean(),
                rng.nextBoolean(),rng.nextBoolean());
            Frame f=m.advance(generation,tick++,world,scene,foreground,e,reserved);
            for(Resource r:Resource.values()) {
                Denial why=f.denial(r);
                ok(why!=null && why.name().length()<32,
                    "256k frames have bounded fixed denial vocabulary");
                if(why==Denial.NONE) {
                    ok(world && foreground && scene!=Scene.NO_WORLD,
                        "every granted resource requires live focused world");
                }
            }
            if(f.autoArmor)
                ok(f.denial(Resource.AUTO_ARMOR)==Denial.NONE,
                   "real AutoArmor grant must match the public reason");
            if(f.inventoryManager)
                ok(f.denial(Resource.INVENTORY_MANAGER)==Denial.NONE,
                   "real sorter grant must match the public reason");
            if(f.chestStealer)
                ok(f.denial(Resource.CHEST_STEALER)==Denial.NONE,
                   "real chest grant must match the public reason");
            if(f.autoSprint)
                ok(f.denial(Resource.AUTO_SPRINT)==Denial.NONE,
                   "real AutoSprint grant must match the public reason");
            if(f.jumpReset)
                ok(f.denial(Resource.JUMP_RESET)==Denial.NONE,
                   "native Jump Reset grant must match public state-machine denial");
            if(f.autoRod)
                ok(f.denial(Resource.AUTO_ROD)==Denial.NONE,
                   "AutoRod own native hotbar/input grant must match the state machine");
            String owned=ModuleInteractionReadout.ownership(f);
            String held=ModuleInteractionReadout.suppression(f);
            ok(owned.length()<130 && held.length()<130 &&
               !owned.contains("@") && !held.contains("@") &&
               !owned.contains("127.") && !held.contains("127."),
               "32k HUD frames are anonymous, bounded and fixed-vocabulary");
        }
    }
    private static void legacyReports() throws Exception {
        Path folder=Files.createTempDirectory("ghostc-r344-diag-migration");
        RuntimeDiagnostics d=new RuntimeDiagnostics();
        d.record(RuntimeDiagnostics.Counter.MODULE_ARMOR_PREEMPTED_SORTER);
        d.record(RuntimeDiagnostics.Counter.MODULE_WTAP_PREEMPTED_SPRINT);
        d.save(folder);
        Path primary=folder.resolve("diagnostics-last.properties");
        Properties legacy=ConfigIntegrity.read(primary);
        // Simulate an exact existing schema-1 report from before R3.44.
        for(RuntimeDiagnostics.Counter counter:RuntimeDiagnostics.Counter.values())
            if(counter.name().startsWith("MODULE_"))
                legacy.remove("count."+counter.name().toLowerCase(java.util.Locale.ROOT));
        try(OutputStream out=Files.newOutputStream(primary)) {
            legacy.store(out,"original anonymous diagnostics schema1");
        }
        d.save(folder);
        Properties rotated=ConfigIntegrity.read(
            folder.resolve("diagnostics-last.properties.bak"));
        Properties updated=ConfigIntegrity.read(primary);
        ok(rotated.equals(legacy),
           "old valid schema1 report remains an accepted rotated backup");
        ok("1".equals(updated.getProperty(
               "count.module_armor_preempted_sorter")) &&
           "1".equals(updated.getProperty(
               "count.module_wtap_preempted_sprint")),
           "new schema1 report persists only anonymous grant-frame counts");
        ok(!updated.containsKey("player") && !updated.containsKey("server") &&
           !updated.containsKey("packet"),
           "saved diagnostic report never exports player/server/packet data");
        for(String key:updated.stringPropertyNames()) {
            ok(key.equals("schema")||key.equals("scope")||
               key.startsWith("count."),
               "fresh opt-in report retains original bounded schema");
        }
    }
    public static void main(String[] args) throws Exception {
        decisions();
        seeded();
        legacyReports();
        ok(checks>=285000,"substantial cross-module denial replay");
        System.out.println("MODULE_INTERACTION_DIAGNOSTICS_PASS assertions="+checks);
    }
}
