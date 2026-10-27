package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ChestStealerPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Standard 1.8.9 GuiChest shift-click transfer, never a raw packet sender.
 * Only a single owner-thread click is admitted after checking GUI/container
 * identity, a real chest inventory, free player inventory and source changes.
 */
final class ForgeChestStealerController {
    private static final String MODULE="inventory.cheststealer";
    private final Minecraft mc;
    private final HostRuntime host;
    private final ChestStealerPolicy policy=new ChestStealerPolicy();
    private Container observed;
    private int pendingSlot=-1;
    private long pendingSince=-1;
    private ItemStack pendingBefore;
    private static final ModuleActionTelemetry.Lane LANE=
        ModuleActionTelemetry.Lane.CHEST;
    private void outcome(ModuleActionTelemetry.Event e) {
        host.nativeAction(LANE,e);
    }

    ForgeChestStealerController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null chest dependencies");
        mc=game;host=runtime;
    }
    void reset() {
        if(pendingSlot>=0)outcome(ModuleActionTelemetry.Event.CANCELLED);
        policy.reset();
        observed=null;
        pendingSlot=-1;pendingSince=-1;
        pendingBefore=null;
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null ||
           !(mc.currentScreen instanceof GuiChest) ||
           !host.featureEnabled(MODULE) || !host.services().connected() ||
           !Display.isActive()) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() { evaluate(); }
        });
        if(!healthy)reset();
    }
    private void evaluate() {
        EntityPlayerSP player=mc.thePlayer;
        GuiChest gui=(GuiChest)mc.currentScreen;
        Container container=gui.inventorySlots;
        if(container==null || player.openContainer!=container ||
           !(container instanceof ContainerChest) ||
           mc.playerController==null || player.isDead ||
           player.inventory.getItemStack()!=null) {
            reset();return;
        }
        // A player interacting manually owns the GUI. Wait for a new open
        // delay rather than racing mouse clicks or shift-clicks.
        if((Mouse.isCreated() && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1))) ||
           (Keyboard.isCreated() && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
                                     Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)))) {
            reset();return;
        }
        ContainerChest chest=(ContainerChest)container;
        IInventory source=chest.getLowerChestInventory();
        int count=source.getSizeInventory();
        if((count!=27 && count!=54) ||
           (!host.chestStealerAllowCustom() && source.hasCustomName()) ||
           container.inventorySlots.size()<count) {
            reset();return;
        }
        if(observed!=container) {
            reset();
            observed=container;
        }
        boolean changed=false;
        if(pendingSlot>=0) {
            ItemStack current=source.getStackInSlot(pendingSlot);
            changed=!ItemStack.areItemStacksEqual(pendingBefore,current);
            if(changed) {
                outcome(ModuleActionTelemetry.Event.LOCAL_CHANGE);
                pendingSlot=-1;pendingSince=-1;
                pendingBefore=null;
            }
        }
        boolean[] candidates=new boolean[count];
        for(int i=0;i<count;i++) {
            ItemStack stack=source.getStackInSlot(i);
            candidates[i]=stack!=null && stack.stackSize>0 &&
                canFitEntireStack(player,stack);
        }
        int slot=policy.next(true,container.windowId,count,
            host.chestStealerOpenDelay(),host.chestStealerClickDelay(),
            candidates,changed,host.services().tick(),
            host.services().generation());
        if(slot<0) {
            if(pendingSlot>=0 && pendingSince>=0 &&
               host.services().tick()-pendingSince>=12) {
                outcome(ModuleActionTelemetry.Event.TIMEOUT);
                pendingSlot=-1;pendingBefore=null;pendingSince=-1;
            }
            return;
        }
        ItemStack before=source.getStackInSlot(slot);
        if(before==null || before.stackSize<=0 || !canFitEntireStack(player,before)) {
            policy.blockWindow();
            outcome(ModuleActionTelemetry.Event.REJECTED);
            return;
        }
        // Read native slot immediately before attempting an actual vanilla
        // shift-click. Do not send window clicks directly as network packets.
        pendingSlot=slot;
        pendingBefore=before.copy();
        pendingSince=host.services().tick();
        mc.playerController.windowClick(container.windowId,slot,0,1,player);
        outcome(ModuleActionTelemetry.Event.ATTEMPT);
    }
    private static boolean canFitEntireStack(EntityPlayerSP player,ItemStack source) {
        int remaining=source.stackSize;
        if(remaining<=0)return false;
        for(ItemStack in:player.inventory.mainInventory) {
            if(in==null)continue;
            if(ItemStack.areItemsEqual(in,source) &&
               ItemStack.areItemStackTagsEqual(in,source)) {
                int limit=Math.min(in.getMaxStackSize(),player.inventory.getInventoryStackLimit());
                remaining-=Math.max(0,limit-in.stackSize);
                if(remaining<=0)return true;
            }
        }
        int limit=Math.min(source.getMaxStackSize(),player.inventory.getInventoryStackLimit());
        for(ItemStack in:player.inventory.mainInventory) {
            if(in==null) {
                remaining-=limit;
                if(remaining<=0)return true;
            }
        }
        return false;
    }
}
