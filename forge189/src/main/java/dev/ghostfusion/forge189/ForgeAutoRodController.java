package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoRodPolicy.Action;
import dev.ghostfusion.features.EspTeamPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.ModuleActionTelemetry;
import dev.ghostfusion.services.ActionCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Sole owner of opt-in AutoRod's short native hotbar transaction.
 * Always run BEFORE ForgeCombatController. While SELECT/USE/RESTORE is owned,
 * the adapter suppresses optional Attack/BlockHit/TriggerBot/CAMERA writes.
 * Vanilla physical input is untouched. No raw packets or packet interception.
 */
final class ForgeAutoRodController {
    private static final String MODULE="combat.autorod";
    private final Minecraft mc;
    private final HostRuntime host;
    private final AutoRodPolicy policy=new AutoRodPolicy();
    private EntityPlayerSP owner;
    private World world;
    ForgeAutoRodController(Minecraft minecraft,HostRuntime runtime) {
        if(minecraft==null||runtime==null)
            throw new IllegalArgumentException("null AutoRod dependencies");
        mc=minecraft;host=runtime;
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
    private void restoreIfOwned(boolean manualOverride) {
        boolean wasOwned=policy.ownsHotbar();
        EntityPlayerSP p=mc.thePlayer;
        boolean same=p!=null&&p==owner&&mc.theWorld==world &&
            mc.playerController!=null &&
            mc.theWorld.getEntityByID(p.getEntityId())==p;
        int slot=p==null?-1:p.inventory.currentItem;
        // Does not silently overwrite a manual slot selection, including
        // when the user has already scrolled to something else.
        int restore=policy.relinquish(slot,same,
            manualOverride || manualHotbar());
        if(restore>=0&&same) {
            p.inventory.currentItem=restore;
            mc.playerController.updateController();
        }
        if(wasOwned)
            host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                ModuleActionTelemetry.Event.CANCELLED);
        owner=null;world=null;
    }
    boolean ownsHotbar() { return policy.ownsHotbar(); }
    void reset() { reset(false); }
    void reset(boolean manualOverride) {
        try { restoreIfOwned(manualOverride); }
        finally {policy.reset();host.services().actions().cancelOwner(MODULE);}
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
    private int rodSlot(EntityPlayerSP p) {
        for(int i=0;i<9;i++) {
            ItemStack item=p.inventory.getStackInSlot(i);
            if(item!=null&&item.stackSize>0&&item.getItem() instanceof ItemFishingRod)
                return i;
        }
        return -1;
    }
    private boolean targetValid(EntityPlayerSP p) {
        MovingObjectPosition hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.ENTITY ||
           !(hit.entityHit instanceof EntityPlayer))return false;
        Entity target=hit.entityHit;
        if(target==p||mc.theWorld.getEntityByID(target.getEntityId())!=target ||
           target.isDead||((EntityPlayer)target).getHealth()<=0 ||
           !p.canEntityBeSeen(target))return false;
        if(host.rodIgnoreTeammates() &&
           EspTeamPolicy.exclude(p.getTeam(),((EntityPlayer)target).getTeam()))
            return false;
        double dx=target.posX-p.posX,dy=target.posY-p.posY,
               dz=target.posZ-p.posZ;
        double dist=dx*dx+dy*dy+dz*dz;
        double range=host.rodRange();
        return Double.isFinite(dist)&&dist<=range*range;
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
            p.inventory.currentItem!=policy.ownedRodSlot())) {
            restoreIfOwned(true);
            policy.reset();
            return false;
        }
        int attackCode=mc.gameSettings.keyBindAttack.getKeyCode();
        int useCode=mc.gameSettings.keyBindUseItem.getKeyCode();
        boolean attackHeld=physical(attackCode);
        boolean manualUse=physical(useCode)||
            mc.gameSettings.keyBindUseItem.isKeyDown();
        boolean safeBinding=attackCode!=0&&useCode!=0&&attackCode!=useCode &&
            attackCode!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI &&
            useCode!=dev.ghostfusion.core.ReservedKeys.CLICK_GUI;
        boolean validPlayer=mc.theWorld.getEntityByID(p.getEntityId())==p &&
            !p.isDead&&Double.isFinite(p.getHealth())&&p.getHealth()>0 &&
            !p.capabilities.isFlying&&!p.isRiding()&&
            !p.isUsingItem()&&p.fishEntity==null;
        boolean target=validPlayer&&safeBinding&&!manualUse&&targetValid(p);
        ItemStack held=p.getHeldItem();
        boolean start=target&&!manualHotbar()&&
            (!host.rodRequireSword() ||
             (held!=null&&held.getItem() instanceof ItemSword));
        // After selecting a rod, no sword is equipped; subsequent state
        // must still require the same live target, world and physical safety.
        boolean active=ourSlot?target&&!manualHotbar():start;
        int rod=ourSlot?policy.ownedRodSlot():rodSlot(p);
        final long tick=host.services().tick(),epoch=host.services().generation();
        Action wanted=policy.decide(epoch,tick,attackHeld,active,
            p.inventory.currentItem,rod,host.rodCooldownTicks());
        if(wanted==Action.NONE)return policy.ownsHotbar();

        // Owner's OUTPUT reservation excludes conflicting optional hotbar or
        // use actions in this generation/tick. A denied intent never commits.
        ActionCoordinator.Type lane=wanted==Action.USE?
            ActionCoordinator.Type.USE:ActionCoordinator.Type.HOTBAR_SELECT;
        host.services().actions().cancelOwner(MODULE);
        host.services().actions().request(MODULE,lane,
            ActionCoordinator.Phase.OUTPUT,
            wanted==Action.SELECT?rod:policy.originalSlot(),40,
            tick,1,epoch);
        ActionCoordinator.Decision selected=host.services().actions().consume(
            lane,ActionCoordinator.Phase.OUTPUT,tick,epoch);
        if(selected==null||!MODULE.equals(selected.owner)) {
            policy.cancelPrepared();
            restoreIfOwned();
            return false;
        }
        if(wanted==Action.SELECT) {
            if(!active||p.inventory.currentItem==rod||
               p.inventory.getStackInSlot(rod)==null ||
               !(p.inventory.getStackInSlot(rod).getItem() instanceof ItemFishingRod)) {
                policy.cancelPrepared();return false;
            }
            owner=p;world=mc.theWorld;
            p.inventory.currentItem=rod;
            mc.playerController.updateController();
            policy.committed(Action.SELECT,epoch,tick);
            host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                ModuleActionTelemetry.Event.ATTEMPT);
            return true;
        }
        if(wanted==Action.USE) {
            ItemStack rodStack=p.getHeldItem();
            if(!active||rodStack==null||
               !(rodStack.getItem() instanceof ItemFishingRod)) {
                policy.cancelPrepared();return true;
            }
            // Genuine vanilla 1.8.9 right-click player controller method:
            // the game manages the rod entity, animation and held item.
            // Boolean return is a local result, never server hit proof.
            boolean used=mc.playerController.sendUseItem(p,mc.theWorld,rodStack);
            if(used) {
                policy.committed(Action.USE,epoch,tick);
                host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                    ModuleActionTelemetry.Event.DISPATCHED);
            } else policy.cancelPrepared();
            return true;
        }
        if(wanted==Action.RESTORE) {
            if(p.inventory.currentItem==policy.ownedRodSlot()&&
               !manualHotbar()) {
                p.inventory.currentItem=policy.originalSlot();
                mc.playerController.updateController();
                boolean cast=policy.castObserved();
                policy.committed(Action.RESTORE,epoch,tick);
                host.nativeAction(ModuleActionTelemetry.Lane.AUTOROD,
                    cast ? ModuleActionTelemetry.Event.LOCAL_COMPLETE :
                           ModuleActionTelemetry.Event.CANCELLED);
            } else {
                // Manual native input wins even if it left the slot at our
                // synthetic value. Never write stale original selection.
                restoreIfOwned(true);
            }
            owner=null;world=null;
            return true;
        }
        return true;
    }
}
