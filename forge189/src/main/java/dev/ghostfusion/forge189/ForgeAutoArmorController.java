package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AutoArmorPolicy;
import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Conservative vanilla 1.8.9 AutoArmor: ONLY in the local GuiInventory.
 * Source slots 9..44; armor slots 5..8. Vanilla shift-click mode 1 only.
 *
 * Upgrade is two separate, independently observed transfers: MOVE OLD ARMOR
 * TO EMPTY STORAGE, then EQUIP BETTER ARMOR from its unchanged source.
 * The client never drops any item, bypasses vanilla window transactions or
 * manually sends C0E packets.
 */
final class ForgeAutoArmorController {
    private static final String MODULE="inventory.autoarmor";
    private final Minecraft mc;
    private final HostRuntime host;
    private final AutoArmorPolicy policy=new AutoArmorPolicy();
    private Container observed;
    private AutoArmorPolicy.Action pending;
    private ItemStack beforeSource,beforeWorn;
    private int oldCopiesInStorage;
    private ItemStack pinnedCandidate;
    private int pinnedSource=-1,pinnedType=-1;

    ForgeAutoArmorController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)
            throw new IllegalArgumentException("null AutoArmor dependencies");
        mc=game;host=runtime;
    }
    void reset() {
        policy.reset();
        observed=null;
        pending=null;
        beforeSource=null;beforeWorn=null;
        oldCopiesInStorage=0;
        pinnedCandidate=null;pinnedSource=-1;pinnedType=-1;
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null ||
           !(mc.currentScreen instanceof GuiInventory) ||
           !host.services().connected() || !host.featureEnabled(MODULE) ||
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
        GuiInventory gui=(GuiInventory)mc.currentScreen;
        Container container=gui.inventorySlots;
        if(container==null || container!=player.inventoryContainer ||
           player.openContainer!=container ||
           !(container instanceof ContainerPlayer) ||
           container.windowId!=0 || container.inventorySlots.size()<45 ||
           player.isDead || player.capabilities.isCreativeMode ||
           player.inventory.getItemStack()!=null || mc.playerController==null ||
           userManipulatingInventory()) {
            reset();return;
        }
        if(observed!=container) {
            reset();
            observed=container;
        }
        boolean confirmed=false;
        if(pending!=null) {
            ItemStack wornNow=container.getSlot(5+pending.armorKind).getStack();
            ItemStack sourceNow=container.getSlot(pending.candidateSlot).getStack();
            if(pending.step==AutoArmorPolicy.Step.REMOVE) {
                confirmed=wornNow==null &&
                    beforeWorn!=null &&
                    countCopies(container,beforeWorn)>oldCopiesInStorage &&
                    ItemStack.areItemStacksEqual(beforeSource,sourceNow);
                if(!ItemStack.areItemStacksEqual(beforeSource,sourceNow)) {
                    // The intended upgrade candidate changed while the
                    // earlier armor was being moved: never click a new item.
                    policy.blockSession();
                }
            } else {
                confirmed=ItemStack.areItemStacksEqual(wornNow,beforeSource) &&
                    !ItemStack.areItemStacksEqual(sourceNow,beforeSource);
            }
            if(confirmed) {
                if(pending.step==AutoArmorPolicy.Step.EQUIP) {
                    pinnedCandidate=null;pinnedSource=-1;pinnedType=-1;
                }
                pending=null;beforeWorn=null;beforeSource=null;
            }
        }
        AutoArmorPolicy.Piece[] storage=new AutoArmorPolicy.Piece[36];
        AutoArmorPolicy.Piece[] worn=new AutoArmorPolicy.Piece[4];
        for(int i=0;i<storage.length;i++)
            storage[i]=describe(container.getSlot(9+i).getStack());
        for(int i=0;i<worn.length;i++)
            worn[i]=describe(container.getSlot(5+i).getStack());
        AutoArmorPolicy.Action action=policy.next(true,storage,worn,
            host.autoArmorUpgrade(),host.autoArmorOpenDelay(),
            host.autoArmorClickDelay(),confirmed,
            host.services().tick(),host.services().generation());
        if(action==null)return;
        final int kind=action.armorKind;
        final int candidate=action.candidateSlot;
        final int click=action.containerSlot;
        if(kind<0 || kind>3 || candidate<9 || candidate>44 ||
           click<5 || click>44 || player.openContainer!=container ||
           container.windowId!=0) {
            policy.blockSession();return;
        }
        ItemStack candidateStack=container.getSlot(candidate).getStack();
        if(!isArmorKind(candidateStack,kind)) {
            policy.blockSession();return;
        }
        ItemStack wornStack=container.getSlot(5+kind).getStack();
        if(action.step==AutoArmorPolicy.Step.REMOVE) {
            if(click!=5+kind || wornStack==null ||
               !host.autoArmorUpgrade() || !hasFreeInventorySlot(container)) {
                policy.blockSession();return;
            }
            pinnedCandidate=candidateStack.copy();
            pinnedSource=candidate;pinnedType=kind;
        } else {
            if(click!=candidate || wornStack!=null) {
                policy.blockSession();return;
            }
            if(pinnedSource>=0 &&
               (pinnedSource!=candidate || pinnedType!=kind ||
                !ItemStack.areItemStacksEqual(pinnedCandidate,candidateStack))) {
                // A manual/server inventory mutation changed the promised
                // replacement while the old armor was being unequipped.
                policy.blockSession();return;
            }
        }
        beforeSource=candidateStack.copy();
        beforeWorn=copy(wornStack);
        oldCopiesInStorage=beforeWorn==null?0:
            countCopies(container,beforeWorn);
        pending=action;
        mc.playerController.windowClick(container.windowId,click,0,1,player);
    }
    private static boolean userManipulatingInventory() {
        if(Mouse.isCreated()) {
            for(int i=0;i<Mouse.getButtonCount();i++)
                if(Mouse.isButtonDown(i))return true;
        }
        if(Keyboard.isCreated()) {
            if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
               Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ||
               Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
               Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))
                return true;
            for(int key=Keyboard.KEY_1;key<=Keyboard.KEY_9;key++)
                if(Keyboard.isKeyDown(key))return true;
        }
        return false;
    }
    private static boolean hasFreeInventorySlot(Container c) {
        for(int slot=9;slot<=44;slot++)
            if(c.getSlot(slot).getStack()==null)return true;
        return false;
    }
    private static int countCopies(Container c,ItemStack wanted) {
        if(wanted==null)return 0;
        int count=0;
        for(int slot=9;slot<=44;slot++)
            if(ItemStack.areItemStacksEqual(c.getSlot(slot).getStack(),wanted))
                count++;
        return count;
    }
    private static boolean isArmorKind(ItemStack stack,int kind) {
        return stack!=null && stack.stackSize==1 &&
            stack.getItem() instanceof ItemArmor &&
            ((ItemArmor)stack.getItem()).armorType==kind;
    }
    private static ItemStack copy(ItemStack stack) {
        return stack==null?null:stack.copy();
    }
    private static AutoArmorPolicy.Piece describe(ItemStack stack) {
        if(stack==null)
            return new AutoArmorPolicy.Piece(AutoArmorPolicy.EMPTY,0);
        if(stack.stackSize!=1 || !(stack.getItem() instanceof ItemArmor))
            return new AutoArmorPolicy.Piece(AutoArmorPolicy.OTHER,0);
        ItemArmor armor=(ItemArmor)stack.getItem();
        int type=armor.armorType;
        if(type<0 || type>3)
            return new AutoArmorPolicy.Piece(AutoArmorPolicy.OTHER,0);
        // Generic protection-oriented ranking, not situational prediction:
        // base defense dominates, then all-around Protection and minor
        // enchantments, with current durability only as a final tie-break.
        double score=100.0*armor.damageReduceAmount +
            8.0*EnchantmentHelper.getEnchantmentLevel(0,stack) +
            2.0*EnchantmentHelper.getEnchantmentLevel(1,stack) +
            2.0*EnchantmentHelper.getEnchantmentLevel(3,stack) +
            2.0*EnchantmentHelper.getEnchantmentLevel(4,stack) +
            0.5*EnchantmentHelper.getEnchantmentLevel(7,stack);
        if(stack.isItemStackDamageable() && stack.getMaxDamage()>0)
            score+=0.1*Math.max(0.0,
                1.0-(double)stack.getItemDamage()/stack.getMaxDamage());
        return new AutoArmorPolicy.Piece(type,score);
    }
}
