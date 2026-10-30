package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy.Action;
import dev.ghostfusion.features.EspTeamPolicy;
import dev.ghostfusion.features.ProjectileTargetFence;
import dev.ghostfusion.features.ProjectileInventoryFence;
import dev.ghostfusion.features.ProjectileNativeUseResult;
import dev.ghostfusion.features.ProjectileNativeUseResult.Outcome;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.services.ActionCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Sole owner of opt-in AutoSnowball's short native hotbar transaction.
 * Always run BEFORE ForgeCombatController. While SELECT/USE/RESTORE is owned,
 * the adapter suppresses optional Attack/BlockHit/TriggerBot/CAMERA writes.
 * Vanilla physical input is untouched. No raw packets or packet interception.
 */
final class ForgeAutoSnowballController {
    private static final String MODULE="combat.autosnowball";
    private final Minecraft mc;
    private final HostRuntime host;
    private final AutoSnowballPolicy policy=new AutoSnowballPolicy();
    private final ProjectileTargetFence targetFence=new ProjectileTargetFence();
    private final ProjectileInventoryFence inventoryFence=new ProjectileInventoryFence();
    private Outcome useOutcome=Outcome.NOT_INVOKED;
    private ForgeProjectileObservationSampler observations;
    private EntityPlayerSP owner;
    private World world;
    ForgeAutoSnowballController(Minecraft minecraft,HostRuntime runtime) {
        if(minecraft==null||runtime==null)
            throw new IllegalArgumentException("null AutoSnowball dependencies");
        mc=minecraft;host=runtime;
    }
    void bindObservations(ForgeProjectileObservationSampler sampler) {
        if(sampler==null)throw new IllegalArgumentException("null observations");
        observations=sampler;
    }
    private static boolean physical(int code) {
        if(code>0 && code<256 && Keyboard.isCreated())
            return Keyboard.isKeyDown(code);
        if(code<0 && Mouse.isCreated()) {
            int button=code+100;
            return button>=0 && button<Mouse.getButtonCount() &&
                Mouse.isButtonDown(button);
        }
        return false; // unbound or unsupported keys never acquire a slot
    }
    private boolean manualHotbar() {
        for(net.minecraft.client.settings.KeyBinding key:mc.gameSettings.keyBindsHotbar)
            if(physical(key.getKeyCode()))return true;
        return false;
    }
    private static Object item(ItemStack s) {return s==null?null:s.getItem();}
    private static int count(ItemStack s) {return s==null?0:s.stackSize;}
    private static int damage(ItemStack s) {return s==null?0:s.getItemDamage();}

    private boolean inventoryRestoreSafe(EntityPlayerSP p) {
        if(p==null||!policy.ownsHotbar())return false;
        int source=policy.originalSlot(),selected=policy.ownedSnowballSlot();
        ItemStack original=p.inventory.getStackInSlot(source);
        ItemStack projectile=p.inventory.getStackInSlot(selected);
        return inventoryFence.mayRestore(host.services().generation(),source,selected,
            original,item(original),count(original),damage(original),projectile);
    }
    private void restoreIfOwned(boolean manualOverride) {
        boolean wasOwned=policy.ownsHotbar();
        EntityPlayerSP p=mc.thePlayer;
        boolean same=p!=null&&p==owner&&mc.theWorld==world &&
            mc.playerController!=null &&
            mc.theWorld.getEntityByID(p.getEntityId())==p;
        int slot=p==null?-1:p.inventory.currentItem;
        // Does not silently overwrite a manual slot selection, including
        // when the user has already scrolled to something else.
        // A changed original hotbar stack is a human/server inventory action;
        // do not restore to that slot just because the temporary projectile
        // slot still happens to be selected.
        boolean unchanged=same && inventoryRestoreSafe(p);
        int restore=policy.relinquish(slot,unchanged,
            manualOverride || manualHotbar());
        if(restore>=0&&same) {
            p.inventory.currentItem=restore;
            mc.playerController.updateController();
        }
        if(wasOwned)
            host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.CANCELLED);
        owner=null;world=null;
        targetFence.reset();
        inventoryFence.reset();
        useOutcome=Outcome.NOT_INVOKED;
    }
    boolean ownsHotbar() { return policy.ownsHotbar(); }
    void reset() { reset(false); }
    void reset(boolean manualOverride) {
        try { restoreIfOwned(manualOverride); }
        finally {
            policy.reset();
            targetFence.reset();
            inventoryFence.reset();
            useOutcome=Outcome.NOT_INVOKED;
            host.services().actions().cancelOwner(MODULE);
        }
    }
    /** True reserves optional combat ATTACK/USE/camera for the current tick. */
    boolean tick(boolean manualOverride) {
        if(manualOverride) {
            // A number-key tap or wheel event is authoritative even if the
            // slot was changed away and back between END ticks.
            reset(true);
            return false;
        }
        if(mc.thePlayer==null||mc.theWorld==null||mc.playerController==null||
           mc.currentScreen!=null||!mc.inGameHasFocus||
           !host.services().connected()||!host.featureEnabled(MODULE)) {
            reset();return false;
        }
        final boolean[] busy={false};
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() {busy[0]=evaluate();}
        });
        if(!healthy) {reset();return false;}
        return busy[0];
    }
    private int snowballSlot(EntityPlayerSP p) {
        for(int i=0;i<9;i++) {
            ItemStack item=p.inventory.getStackInSlot(i);
            if(item!=null&&item.stackSize>0&&item.getItem() instanceof ItemSnowball)
                return i;
        }
        return -1;
    }
    private EntityPlayer targetCandidate(EntityPlayerSP p) {
        MovingObjectPosition hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.ENTITY ||
           !(hit.entityHit instanceof EntityPlayer))return null;
        EntityPlayer target=(EntityPlayer)hit.entityHit;
        if(target==p||mc.theWorld.getEntityByID(target.getEntityId())!=target ||
           target.isDead||target.getHealth()<=0 ||
           !p.canEntityBeSeen(target))return null;
        if(host.snowballIgnoreTeammates() &&
           EspTeamPolicy.exclude(p.getTeam(),target.getTeam()))
            return null;
        double dx=target.posX-p.posX,dy=target.posY-p.posY,
               dz=target.posZ-p.posZ;
        double dist=dx*dx+dy*dy+dz*dz;
        double range=host.snowballRange();
        return Double.isFinite(dist)&&dist<=range*range ? target : null;
    }
    private boolean evaluate() {
        EntityPlayerSP p=mc.thePlayer;
        boolean ourSlot=policy.ownsHotbar();
        if(ourSlot&&(owner!=p||world!=mc.theWorld ||
            mc.theWorld.getEntityByID(p.getEntityId())!=p)) {
            reset();return false;
        }
        // A human hotbar key and wheel-changed currentItem always outrank us.
        if(ourSlot && (manualHotbar() ||
            p.inventory.currentItem!=policy.ownedSnowballSlot())) {
            restoreIfOwned(true);
            policy.reset();
            return false;
        }
        int attackCode=mc.gameSettings.keyBindAttack.getKeyCode();
        int useCode=mc.gameSettings.keyBindUseItem.getKeyCode();
        boolean attackHeld=physical(attackCode);
        boolean manualUse=physical(useCode)||
            mc.gameSettings.keyBindUseItem.isKeyDown();
        if(ourSlot && manualUse) {
            // Real player use-item input always wins. Do not move the
            // selected hotbar slot behind the player's right click.
            reset(true);
            return false;
        }
        boolean safeBinding=attackCode!=0&&useCode!=0&&attackCode!=useCode &&
            attackCode!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI &&
            useCode!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI;
        boolean validPlayer=mc.theWorld.getEntityByID(p.getEntityId())==p &&
            !p.isDead&&Double.isFinite(p.getHealth())&&p.getHealth()>0 &&
            !p.capabilities.isFlying&&!p.isRiding()&&
            !p.isUsingItem()&&
            !mc.playerController.isSpectator();
        final EntityPlayer targetCandidate=targetCandidate(p);
        boolean target=validPlayer&&safeBinding&&!manualUse&&targetCandidate!=null;
        ItemStack held=p.getHeldItem();
        boolean start=target&&!manualHotbar()&&
            (!host.snowballRequireSword() ||
             (held!=null&&held.getItem() instanceof ItemSword));
        // After selecting a snowball, no sword is equipped; subsequent state
        // must still require the same live target, world and physical safety.
        boolean active=ourSlot?target&&!manualHotbar():start;
        int snowball=ourSlot?policy.ownedSnowballSlot():snowballSlot(p);
        final long tick=host.services().tick(),epoch=host.services().generation();
        Action wanted=policy.decide(epoch,tick,attackHeld,active,
            p.inventory.currentItem,snowball,host.snowballCooldownTicks());
        if(wanted==Action.NONE)return policy.ownsHotbar();

        // Owner's OUTPUT reservation excludes conflicting optional hotbar or
        // use actions in this generation/tick. A denied intent never commits.
        ActionCoordinator.Type lane=wanted==Action.USE?
            ActionCoordinator.Type.USE:ActionCoordinator.Type.HOTBAR_SELECT;
        host.services().actions().cancelOwner(MODULE);
        host.services().actions().request(MODULE,lane,
            ActionCoordinator.Phase.OUTPUT,
            wanted==Action.SELECT?snowball:policy.originalSlot(),40,
            tick,1,epoch);
        ActionCoordinator.Decision selected=host.services().actions().consume(
            lane,ActionCoordinator.Phase.OUTPUT,tick,epoch);
        if(selected==null||!MODULE.equals(selected.owner)) {
            policy.cancelPrepared();
            restoreIfOwned(false); // denied optional OUTPUT, not human input
            return false;
        }
        if(wanted==Action.SELECT) {
            if(!active||p.inventory.currentItem==snowball||
               p.inventory.getStackInSlot(snowball)==null ||
               !(p.inventory.getStackInSlot(snowball).getItem() instanceof ItemSnowball)) {
                policy.cancelPrepared();return false;
            }
            final int originalSlot=p.inventory.currentItem;
            final ItemStack sourceStack=p.inventory.getStackInSlot(originalSlot);
            final ItemStack projectileStack=p.inventory.getStackInSlot(snowball);
            if(projectileStack.stackSize<1) {
                policy.cancelPrepared();return false;
            }
            owner=p;world=mc.theWorld;
            p.inventory.currentItem=snowball;
            mc.playerController.updateController();
            policy.committed(Action.SELECT,epoch,tick);
            // Bind both native inventory stacks BEFORE anything can be used.
            inventoryFence.selected(epoch,tick,originalSlot,snowball,
                sourceStack,item(sourceStack),count(sourceStack),damage(sourceStack),
                projectileStack,item(projectileStack),
                count(projectileStack),damage(projectileStack));
            // The slot and target were selected together. The next native
            // USE can only address this exact player on the exact next tick.
            targetFence.selected(epoch,tick,targetCandidate.getUniqueID(),
                targetCandidate.getEntityId());
            host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.ATTEMPT);
            return true;
        }
        if(wanted==Action.USE) {
            ItemStack snowballStack=p.getHeldItem();
            // Fresh native range/visibility/crosshair checks are necessary
            // but not sufficient: reject a different player after SELECT,
            // even when that new opponent is otherwise valid/in range.
            boolean sameTarget=targetFence.allowUse(epoch,tick,
                targetCandidate==null?null:targetCandidate.getUniqueID(),
                targetCandidate==null?-1:targetCandidate.getEntityId(),
                target);
            final int sourceSlot=policy.originalSlot();
            final int ownedSlot=policy.ownedSnowballSlot();
            final ItemStack sourceNow=p.inventory.getStackInSlot(sourceSlot);
            boolean exactItem=inventoryFence.allowUse(epoch,tick,sourceSlot,ownedSlot,
                sourceNow,item(sourceNow),count(sourceNow),damage(sourceNow),
                snowballStack,item(snowballStack),count(snowballStack),damage(snowballStack));
            if(!active||!sameTarget||!exactItem||snowballStack==null||
               snowballStack.stackSize<1||
               !(snowballStack.getItem() instanceof ItemSnowball) ||
               mc.playerController.isSpectator()) {
                // Spectator mode and pre-use native veto cannot dispatch.
                // Safe RESTORE is still required after an owned SELECT.
                policy.cancelPrepared();return true;
            }
            // Vanilla 1.8.9 returns false when the LOCAL ItemStack did not
            // change. It nevertheless submitted the use packet before
            // calling useItemRightClick in non-spectator mode. Creative-mode
            // snowballs may remain unchanged; false is NOT rejected use.
            // Capture quantity BEFORE vanilla mutates the stack.
            final int quantityBeforeNativeUse=snowballStack.stackSize;
            boolean localChanged=mc.playerController.sendUseItem(
                p,mc.theWorld,snowballStack);
            useOutcome=ProjectileNativeUseResult.classify(
                false,true,localChanged);
            policy.committed(Action.USE,epoch,tick);
            inventoryFence.nativeUsed(epoch,tick);
            // Observes CLIENT inventory, not a server acknowledgement.
            if(observations!=null)
                observations.armSnowball(p,mc.theWorld,epoch,tick,
                    ownedSlot,snowballStack,quantityBeforeNativeUse);
            // Local native submission only, never proof of server receipt,
            // spawned hook/projectile or a PvP hit.
            host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                ModuleActionTelemetry.Event.DISPATCHED);
            return true;
        }
        if(wanted==Action.RESTORE) {
            if(p.inventory.currentItem==policy.ownedSnowballSlot()&&
               !manualHotbar() && inventoryRestoreSafe(p)) {
                p.inventory.currentItem=policy.originalSlot();
                mc.playerController.updateController();
                boolean cast=policy.castObserved();
                policy.committed(Action.RESTORE,epoch,tick);
                host.nativeAction(ModuleActionTelemetry.Lane.AUTOSNOWBALL,
                    !cast ? ModuleActionTelemetry.Event.CANCELLED :
                    useOutcome.localChange() ?
                        ModuleActionTelemetry.Event.LOCAL_CHANGE :
                        ModuleActionTelemetry.Event.LOCAL_COMPLETE);
            } else {
                // Manual native input wins even if it left the slot at our
                // synthetic value. Never write stale original selection.
                restoreIfOwned(true);
            }
            owner=null;world=null;
            targetFence.reset();
            inventoryFence.reset();
            useOutcome=Outcome.NOT_INVOKED;
            return true;
        }
        return true;
    }
}
