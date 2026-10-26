package dev.ghostfusion.forge189;

import dev.ghostfusion.features.InventoryManagerPolicy;
import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.EnchantmentHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Real 1.8.9 inventory-screen-only hotbar sorting, using vanilla click mode 2.
 * Never drops or destroys items. Never sorts an unopened inventory or chest.
 */
final class ForgeInventoryManagerController {
    private static final String MODULE="inventory.manager";
    private final Minecraft mc;
    private final HostRuntime host;
    private final InventoryManagerPolicy policy=new InventoryManagerPolicy();
    private Container observed;
    private int pendingSource=-1,pendingTarget=-1;
    private ItemStack beforeSource,beforeTarget;

    ForgeInventoryManagerController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null inventory manager dependencies");
        mc=game;host=runtime;
    }
    void reset() {
        policy.reset();
        observed=null;
        pendingSource=-1;pendingTarget=-1;
        beforeSource=null;beforeTarget=null;
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null ||
           !(mc.currentScreen instanceof GuiInventory) ||
           !Display.isActive() || !host.services().connected() ||
           !host.featureEnabled(MODULE)) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run(){evaluate();}
        });
        if(!healthy)reset();
    }
    private void evaluate() {
        EntityPlayerSP player=mc.thePlayer;
        GuiInventory gui=(GuiInventory)mc.currentScreen;
        Container container=gui.inventorySlots;
        if(container==null || container!=player.inventoryContainer ||
           player.openContainer!=container ||
           !(container instanceof ContainerPlayer) ||
           container.windowId!=0 || container.inventorySlots.size()<45 ||
           player.isDead || player.capabilities.isCreativeMode ||
           player.inventory.getItemStack()!=null || mc.playerController==null) {
            reset();return;
        }
        // Physical interaction always wins. A new warmup is required after a
        // user click/Shift, avoiding simultaneous drag/swaps/quick-moves.
        if((Mouse.isCreated() &&
            (Mouse.isButtonDown(0) || Mouse.isButtonDown(1) ||
             (Mouse.getButtonCount()>2 && Mouse.isButtonDown(2)))) ||
           (Keyboard.isCreated() &&
            (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
             Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)))) {
            reset();return;
        }
        if(observed!=container) {
            reset();
            observed=container;
        }
        boolean changed=false;
        if(pendingSource>=0) {
            ItemStack sourceNow=container.getSlot(pendingSource).getStack();
            ItemStack targetNow=container.getSlot(36+pendingTarget).getStack();
            // Both sides of an actual hotbar swap must change. This is local
            // observational evidence, not proof of server confirmation.
            changed=!ItemStack.areItemStacksEqual(beforeSource,sourceNow) &&
                !ItemStack.areItemStacksEqual(beforeTarget,targetNow);
            if(changed) {
                pendingSource=-1;pendingTarget=-1;
                beforeSource=null;beforeTarget=null;
            }
        }
        InventoryManagerPolicy.Slot[] slots=new InventoryManagerPolicy.Slot[36];
        for(int i=0;i<slots.length;i++)
            slots[i]=describe(container.getSlot(i+9).getStack());
        InventoryManagerPolicy.Swap swap=policy.next(
            true,slots,host.inventoryManagerUpgrade(),
            host.inventoryManagerOpenDelay(),host.inventoryManagerClickDelay(),
            changed,host.services().tick(),host.services().generation());
        if(swap==null)return;
        int source=swap.sourceContainerSlot;
        int hotbar=swap.targetHotbarIndex;
        if(source<9 || source>44 || hotbar<0 || hotbar>3 ||
           source==36+hotbar || player.openContainer!=container) {
            policy.blockWindow();return;
        }
        pendingSource=source;pendingTarget=hotbar;
        beforeSource=copy(container.getSlot(source).getStack());
        beforeTarget=copy(container.getSlot(36+hotbar).getStack());
        // Vanilla hotbar-swap click; controller owns the transaction and
        // Minecraft owns packet synchronization/transaction confirmation.
        mc.playerController.windowClick(container.windowId,source,hotbar,2,player);
    }
    private static ItemStack copy(ItemStack stack) {
        return stack==null?null:stack.copy();
    }
    private static InventoryManagerPolicy.Slot describe(ItemStack stack) {
        if(stack==null || stack.stackSize<=0)
            return new InventoryManagerPolicy.Slot(InventoryManagerPolicy.EMPTY,0);
        Item item=stack.getItem();
        int kind;
        double quality;
        if(item instanceof ItemSword) {
            kind=InventoryManagerPolicy.SWORD;
            quality=((ItemSword)item).getDamageVsEntity() +
                1.25*EnchantmentHelper.getEnchantmentLevel(16,stack);
        } else if(item instanceof ItemTool &&
                  (item instanceof ItemPickaxe ||
                   item instanceof ItemAxe || item instanceof ItemSpade)) {
            final ItemTool tool=(ItemTool)item;
            kind=item instanceof ItemPickaxe?InventoryManagerPolicy.PICKAXE:
                item instanceof ItemAxe?InventoryManagerPolicy.AXE:
                InventoryManagerPolicy.SHOVEL;
            quality=100*tool.getToolMaterial().getHarvestLevel() +
                tool.getToolMaterial().getEfficiencyOnProperMaterial() +
                2.5*EnchantmentHelper.getEnchantmentLevel(32,stack);
        } else {
            // All other player items are protected from selection and from
            // destination overwrite even when upgrade is enabled.
            return new InventoryManagerPolicy.Slot(InventoryManagerPolicy.OTHER,0);
        }
        if(stack.isItemStackDamageable() && stack.getMaxDamage()>0) {
            quality+=0.01*Math.max(0.0,
                1.0-(double)stack.getItemDamage()/stack.getMaxDamage());
        }
        return new InventoryManagerPolicy.Slot(kind,quality);
    }
}
