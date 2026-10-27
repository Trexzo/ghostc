package dev.ghostfusion.features;

/**
 * Owner-thread, session-fenced arbitration for native Minecraft modules.
 * Enabling a feature never by itself grants a native write.
 * Each controller still validates native player and physical input state.
 */
public final class ModuleInteractionStateMachine {
    public enum Scene { GAME, PLAYER_INVENTORY, CHEST, OTHER_GUI, NO_WORLD }
    public enum Phase {
        INACTIVE, GAMEPLAY, PLAYER_ARMOR, PLAYER_SORT, CHEST_TRANSFER, UI_PAUSED
    }
    public enum InventoryOwner { NONE, AUTO_ARMOR, INVENTORY_MANAGER, CHEST_STEALER }
    public enum SprintOwner { NONE, VANILLA_OR_AUTO_SPRINT, WTAP }

    public static final class Enabled {
        public final boolean combat, bridge, velocity, wtap, sprint;
        public final boolean autoArmor, inventoryManager, chestStealer;
        public Enabled(boolean combat,boolean bridge,boolean velocity,
                       boolean wtap,boolean sprint,boolean autoArmor,
                       boolean inventoryManager,boolean chestStealer) {
            this.combat=combat;this.bridge=bridge;this.velocity=velocity;
            this.wtap=wtap;this.sprint=sprint;this.autoArmor=autoArmor;
            this.inventoryManager=inventoryManager;
            this.chestStealer=chestStealer;
        }
    }
    public static final class Frame {
        public final Phase phase;
        public final InventoryOwner inventoryOwner;
        public final SprintOwner sprintOwner;
        public final boolean combat,bridge,velocity,wtap,autoSprint;
        public final boolean chestStealer,autoArmor,inventoryManager;
        public final boolean transitioned;
        public final long generation,tick;
        private Frame(Phase phase,InventoryOwner owner,SprintOwner sprint,
                      Enabled enabled,boolean worldInput,boolean wtapReserved,
                      boolean changed,long generation,long tick) {
            this.phase=phase;this.inventoryOwner=owner;this.sprintOwner=sprint;
            combat=worldInput&&enabled.combat;
            bridge=worldInput&&enabled.bridge;
            velocity=worldInput&&enabled.velocity;
            wtap=worldInput&&enabled.wtap;
            autoSprint=worldInput&&enabled.sprint&&!wtapReserved;
            chestStealer=owner==InventoryOwner.CHEST_STEALER;
            autoArmor=owner==InventoryOwner.AUTO_ARMOR;
            inventoryManager=owner==InventoryOwner.INVENTORY_MANAGER;
            transitioned=changed;
            this.generation=generation;this.tick=tick;
        }
    }

    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1;
    private Phase last=Phase.INACTIVE;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("module interaction owner thread");
    }
    /** An immutable frame grants exactly one native GUI inventory-click owner.
     * A pending WTap reset temporarily preempts AutoSprint, not vanilla input. */
    public Frame advance(long epoch,long tick,boolean worldPresent,
                         Scene scene,boolean activeWindow,
                         Enabled modules,boolean wtapReserved) {
        checkOwner();
        if(epoch<0||tick<0||scene==null||modules==null)
            throw new IllegalArgumentException("invalid module tick");
        if(generation!=epoch) {
            generation=epoch;
            lastTick=-1;
            last=Phase.INACTIVE;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("backward module tick");
        lastTick=tick;

        boolean usable=worldPresent&&activeWindow&&scene!=Scene.NO_WORLD;
        boolean worldInput=usable&&scene==Scene.GAME;
        InventoryOwner inventory=InventoryOwner.NONE;
        Phase phase=Phase.INACTIVE;
        if(usable) {
            if(scene==Scene.GAME) {
                phase=Phase.GAMEPLAY;
            } else if(scene==Scene.PLAYER_INVENTORY) {
                // A failed armor upgrade must not cause the sorter to click.
                if(modules.autoArmor) {
                    inventory=InventoryOwner.AUTO_ARMOR;
                    phase=Phase.PLAYER_ARMOR;
                } else if(modules.inventoryManager) {
                    inventory=InventoryOwner.INVENTORY_MANAGER;
                    phase=Phase.PLAYER_SORT;
                } else {
                    phase=Phase.UI_PAUSED;
                }
            } else if(scene==Scene.CHEST) {
                if(modules.chestStealer) {
                    inventory=InventoryOwner.CHEST_STEALER;
                    phase=Phase.CHEST_TRANSFER;
                } else {
                    phase=Phase.UI_PAUSED;
                }
            } else {
                phase=Phase.UI_PAUSED;
            }
        }
        SprintOwner sprint=worldInput
            ? (modules.wtap && wtapReserved ? SprintOwner.WTAP :
               SprintOwner.VANILLA_OR_AUTO_SPRINT)
            : SprintOwner.NONE;
        Frame frame=new Frame(phase,inventory,sprint,modules,
            worldInput,sprint==SprintOwner.WTAP,
            phase!=last,generation,tick);
        last=phase;
        return frame;
    }
    public void reset() {
        checkOwner();
        generation=-1;lastTick=-1;last=Phase.INACTIVE;
    }
}
