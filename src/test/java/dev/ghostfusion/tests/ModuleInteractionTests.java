package dev.ghostfusion.tests;

import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Enabled;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Frame;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Scene;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Phase;
import dev.ghostfusion.features.ModuleInteractionStateMachine.InventoryOwner;
import dev.ghostfusion.features.ModuleInteractionStateMachine.SprintOwner;
import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Deterministic 65k mixed module tick contract, including actual WTap
 * reservation handoff. Forge still requires full native game acceptance. */
public final class ModuleInteractionTests {
    private static int assertions;
    private static void ok(boolean valid,String why) {
        assertions++;
        if(!valid)throw new AssertionError(why);
    }
    private static final Enabled ALL=new Enabled(true,true,true,true,true,true,true,true);
    private static Frame frame(ModuleInteractionStateMachine sm,long epoch,long tick,
                               boolean world,Scene scene,boolean focus,Enabled e,
                               boolean wtapPending) {
        return sm.advance(epoch,tick,world,scene,focus,e,wtapPending);
    }
    private static void basic() {
        ModuleInteractionStateMachine s=new ModuleInteractionStateMachine();
        Frame f=frame(s,1,0,true,Scene.GAME,true,ALL,false);
        ok(f.phase==Phase.GAMEPLAY && f.combat && f.bridge && f.velocity &&
           f.wtap && f.autoSprint &&
           f.sprintOwner==SprintOwner.VANILLA_OR_AUTO_SPRINT &&
           f.inventoryOwner==InventoryOwner.NONE,
           "gameplay grants combat, sneak, velocity and original sprint");
        f=frame(s,1,1,true,Scene.GAME,true,ALL,true);
        ok(f.combat && f.bridge && f.velocity && f.wtap && !f.autoSprint &&
           f.sprintOwner==SprintOwner.WTAP,
           "WTap pending reentry owns sprint while unrelated modules still run");
        f=frame(s,1,2,true,Scene.GAME,true,ALL,false);
        ok(f.autoSprint && f.sprintOwner==SprintOwner.VANILLA_OR_AUTO_SPRINT,
           "AutoSprint only resumes after WTap completes");
        f=frame(s,1,3,true,Scene.PLAYER_INVENTORY,true,ALL,true);
        ok(f.phase==Phase.PLAYER_ARMOR && f.transitioned &&
           f.autoArmor && !f.inventoryManager && !f.chestStealer &&
           !f.combat && !f.bridge && !f.velocity && !f.wtap && !f.autoSprint,
           "AutoArmor owns inventory while gameplay inputs are released");
        f=frame(s,1,4,true,Scene.PLAYER_INVENTORY,true,ALL,true);
        ok(f.phase==Phase.PLAYER_ARMOR && !f.transitioned,
           "same player GUI retains stable armor owner");
        f=frame(s,1,5,true,Scene.CHEST,true,ALL,false);
        ok(f.phase==Phase.CHEST_TRANSFER && f.chestStealer &&
           !f.autoArmor && !f.inventoryManager && !f.combat &&
           f.transitioned,
           "chest source clicks never coincide with armor/sorter actions");
        f=frame(s,1,6,true,Scene.OTHER_GUI,true,ALL,false);
        ok(f.phase==Phase.UI_PAUSED && f.inventoryOwner==InventoryOwner.NONE &&
           !f.autoSprint && !f.combat,
           "all input automation stopped for unknown custom menu");
        f=frame(s,1,7,true,Scene.PLAYER_INVENTORY,false,ALL,false);
        ok(f.phase==Phase.INACTIVE && f.inventoryOwner==InventoryOwner.NONE,
           "OS focus lost: even open player inventory automation stops");
        f=frame(s,1,8,false,Scene.GAME,true,ALL,true);
        ok(f.phase==Phase.INACTIVE && !f.combat && !f.autoSprint &&
           !f.velocity && !f.bridge,
           "session/world loss invalidates all native writes");
        f=frame(s,2,0,true,Scene.GAME,true,ALL,false);
        ok(f.transitioned && f.combat && f.generation==2 && f.tick==0,
           "new epoch can restart tick clock but cannot inherit old scene");
        s.reset();
        f=frame(s,2,1,false,Scene.NO_WORLD,false,ALL,false);
        ok(f.inventoryOwner==InventoryOwner.NONE &&
           f.sprintOwner==SprintOwner.NONE,
           "Panic resets ownership and cannot reissue prior work");
    }
    private static void priorities() {
        ModuleInteractionStateMachine s=new ModuleInteractionStateMachine();
        Enabled noArmor=new Enabled(true,true,true,true,true,false,true,true);
        Enabled noInventory=new Enabled(true,true,true,true,true,false,false,true);
        Enabled nothing=new Enabled(false,false,false,false,false,false,false,false);
        Frame f=frame(s,1,0,true,Scene.PLAYER_INVENTORY,true,noArmor,false);
        ok(f.inventoryOwner==InventoryOwner.INVENTORY_MANAGER &&
           f.inventoryManager && !f.autoArmor && !f.chestStealer,
           "sorter owns player inventory only if AutoArmor is disabled");
        f=frame(s,1,1,true,Scene.PLAYER_INVENTORY,true,noInventory,false);
        ok(f.inventoryOwner==InventoryOwner.NONE && !f.inventoryManager &&
           !f.autoArmor,"no enabled player GUI module, no click owner");
        f=frame(s,1,2,true,Scene.CHEST,true,noInventory,false);
        ok(f.inventoryOwner==InventoryOwner.CHEST_STEALER,
           "Chest Stealer only in correct GUI");
        f=frame(s,1,3,true,Scene.CHEST,true,nothing,false);
        ok(f.inventoryOwner==InventoryOwner.NONE && !f.chestStealer,
           "disabled Chest Stealer never inherits click authority");
        f=frame(s,1,4,true,Scene.GAME,true,nothing,true);
        ok(f.phase==Phase.GAMEPLAY && !f.combat && !f.velocity &&
           !f.wtap && !f.autoSprint && !f.bridge &&
           f.sprintOwner==SprintOwner.VANILLA_OR_AUTO_SPRINT,
           "unregistered/disabled modules never acquire native action grants");
        Enabled wtapOnly=new Enabled(false,false,false,true,true,false,false,false);
        f=frame(s,1,5,true,Scene.GAME,true,wtapOnly,true);
        ok(f.wtap && !f.autoSprint && f.sprintOwner==SprintOwner.WTAP,
           "WTap only preempts AutoSprint, not other gameplay lanes");
        f=frame(s,1,6,true,Scene.GAME,true,wtapOnly,false);
        ok(f.wtap && f.autoSprint,"WTap completion restores AutoSprint grant");
    }
    private static void seeded() {
        ModuleInteractionStateMachine s=new ModuleInteractionStateMachine();
        Random random=new Random(3432027L);
        long epoch=1,tick=0;
        for(int i=0;i<65000;i++) {
            if(i%311==0) {
                epoch++;
                tick=0;
            }
            boolean present=random.nextInt(8)!=0,foreground=random.nextInt(9)!=0;
            Scene scene=Scene.values()[random.nextInt(Scene.values().length)];
            boolean pending=random.nextBoolean();
            Enabled e=new Enabled(random.nextBoolean(),random.nextBoolean(),
                random.nextBoolean(),random.nextBoolean(),random.nextBoolean(),
                random.nextBoolean(),random.nextBoolean(),random.nextBoolean());
            Frame f=frame(s,epoch,tick++,present,scene,foreground,e,pending);
            int claimed=(f.autoArmor?1:0)+(f.inventoryManager?1:0)+
                        (f.chestStealer?1:0);
            ok(claimed<=1,"65k tick: at most one inventory click owner");
            ok((f.inventoryOwner==InventoryOwner.NONE)==(claimed==0),
               "65k tick: inventory owner and grants agree");
            ok(!(f.autoSprint && f.sprintOwner==SprintOwner.WTAP),
               "65k tick: WTap and AutoSprint never own sprint together");
            ok(!f.autoArmor || e.autoArmor && scene==Scene.PLAYER_INVENTORY &&
               present && foreground,
               "65k tick: Armor grant only in foreground native player inventory");
            ok(!f.inventoryManager || e.inventoryManager && !e.autoArmor &&
               scene==Scene.PLAYER_INVENTORY && present && foreground,
               "65k tick: Inventory Manager cannot interrupt staged armor");
            ok(!f.chestStealer || e.chestStealer && scene==Scene.CHEST &&
               present && foreground,
               "65k tick: Chest Stealer cannot click player inventory");
            ok(scene==Scene.GAME && foreground && present ||
               !f.combat && !f.bridge && !f.velocity && !f.wtap &&
               !f.autoSprint,
               "65k tick: GUI/focus/world forbid all gameplay automation");
            ok(!f.combat || e.combat,"65k tick: disable can never grant combat");
            ok(!f.velocity || e.velocity,"65k tick: disable can never grant packet/motion");
            ok(!f.bridge || e.bridge,"65k tick: disable can never grant sneak");
            ok(!f.wtap || e.wtap,"65k tick: disable can never grant sprint reset");
            if(i%79==0) {
                s.reset();
                Frame closed=frame(s,epoch,tick++,false,Scene.NO_WORLD,false,e,false);
                ok(!closed.combat && closed.inventoryOwner==InventoryOwner.NONE &&
                   closed.sprintOwner==SprintOwner.NONE,
                   "Panic/reset completely revokes all native writes");
            }
        }
    }
    private static void realWtapHandoff() {
        ModuleInteractionStateMachine s=new ModuleInteractionStateMachine();
        WTapPolicy w=new WTapPolicy();
        ok(w.tick(true,true,0,1)==WTapPolicy.Decision.NONE,"WTap idle");
        ok(w.attack(true,true,true,2,1,1),"actual observed attack reserves sprint");
        Frame f=frame(s,1,1,true,Scene.GAME,true,ALL,w.pending());
        ok(f.sprintOwner==SprintOwner.WTAP && !f.autoSprint,
           "real WTapPolicy reservation reaches the shared sprint owner");
        ok(w.tick(true,true,1,1)==WTapPolicy.Decision.NONE,
           "original attack frame retains native sprint bonus");
        f=frame(s,1,2,true,Scene.GAME,true,ALL,w.pending());
        ok(!f.autoSprint && w.tick(true,true,2,1)==WTapPolicy.Decision.RESET,
           "AutoSprint cannot undo WTap native RESET");
        f=frame(s,1,3,true,Scene.GAME,true,ALL,w.pending());
        ok(!f.autoSprint && w.tick(true,true,3,1)==WTapPolicy.Decision.HOLD,
           "AutoSprint cannot reassert during WTap hold");
        f=frame(s,1,4,true,Scene.GAME,true,ALL,w.pending());
        ok(!f.autoSprint && w.tick(true,true,4,1)==WTapPolicy.Decision.RESTORE,
           "WTap itself owns and completes sprint restoration");
        f=frame(s,1,5,true,Scene.GAME,true,ALL,w.pending());
        ok(f.autoSprint && f.sprintOwner==SprintOwner.VANILLA_OR_AUTO_SPRINT,
           "AutoSprint resumes only after real WTap state clears");
        w.reset();s.reset();
        ok(!w.pending(),"Panic clears native sprint reservation");
    }
    private static void ownerAndProfiles() throws Exception {
        final ModuleInteractionStateMachine s=new ModuleInteractionStateMachine();
        final AtomicBoolean refused=new AtomicBoolean(false);
        Thread t=new Thread(()->{
            try {frame(s,1,0,true,Scene.GAME,true,ALL,false);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        t.start();t.join();
        ok(refused.get(),"foreign thread cannot allocate native resource grants");
        Path dir=Files.createTempDirectory("ghostc-r343-modules-");
        HostRuntime host=new HostRuntime(dir);
        host.worldPresence(true);
        for(String module:new String[]{
            "combat.aimassist","combat.clicker","combat.blockhit","combat.hitselect",
            "combat.velocity","combat.wtap","movement.sprint","movement.bridgeassist",
            "inventory.autoarmor","inventory.manager","inventory.cheststealer"
        }) {
            host.uiActions().toggle(module);
            ok(host.featureEnabled(module),"all independent modules can coexist enabled");
        }
        Frame f=frame(s,host.services().generation(),host.services().tick(),
            host.services().connected(),Scene.PLAYER_INVENTORY,true,ALL,false);
        ok(f.autoArmor && !f.inventoryManager && !f.chestStealer,
           "simultaneously enabled native modules still have one inventory owner");
        int disabled=PanicController.disableAll(host);
        ok(disabled>=11,"Panic disables every registered action module");
        s.reset();
        Frame after=frame(s,host.services().generation(),host.services().tick(),
            host.services().connected(),Scene.GAME,true,
            new Enabled(false,false,false,false,false,false,false,false),false);
        ok(!after.combat&&!after.velocity&&!after.wtap&&!after.bridge &&
           !after.autoSprint,"Panic session cannot acquire enabled lanes");
        host.close();
    }
    public static void main(String[] args) throws Exception {
        basic();
        priorities();
        realWtapHandoff();
        seeded();
        ownerAndProfiles();
        ok(assertions>700000,"65k randomized combined native arbitration frames");
        System.out.println("MODULE_INTERACTION_PASS assertions="+assertions);
    }
}
