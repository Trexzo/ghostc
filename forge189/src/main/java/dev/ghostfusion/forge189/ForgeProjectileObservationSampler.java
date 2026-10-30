package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileObservationWindow;
import dev.ghostfusion.features.ProjectileInventoryReconciliation;
import dev.ghostfusion.features.ProjectileHookProvenance;
import dev.ghostfusion.features.ProjectileObservationWindow.Outcome;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * Passive END-tick reader, distinct from temporary projectile hotbar owners.
 * No synthetic input, item updates, packets, targets or entity spawning.
 * Evidence is local game state, NEVER verified remote acknowledgement.
 */
final class ForgeProjectileObservationSampler {
    private final Minecraft mc;
    private final HostRuntime host;
    private final ProjectileObservationWindow rod=new ProjectileObservationWindow();
    private final ProjectileHookProvenance rodProvenance=new ProjectileHookProvenance();
    private final ProjectileObservationWindow snowball=new ProjectileObservationWindow();
    private EntityPlayerSP rodPlayer,snowPlayer;
    private World rodWorld,snowWorld;
    private ItemStack snowStack;
    private Object snowItem;
    private int snowSlot=-1,snowCount=-1,snowDamage;
    private boolean snowNativeLocalMutation;

    ForgeProjectileObservationSampler(Minecraft client,HostRuntime runtime) {
        if(client==null||runtime==null)
            throw new IllegalArgumentException("null observation dependencies");
        mc=client;host=runtime;
    }
    void armRod(EntityPlayerSP player,World world,long generation,long tick,
                EntityFishHook beforeNativeHook) {
        if(player==null||world==null)throw new IllegalArgumentException("rod session");
        rodPlayer=player;rodWorld=world;
        rodProvenance.arm(player,world,beforeNativeHook);
        rod.arm(generation,tick);
    }
    void armSnowball(EntityPlayerSP player,World world,long generation,long tick,
                     int slot,ItemStack originalStack,int originalCount,
                     boolean nativeLocalMutation) {
        if(player==null||world==null||slot<0||slot>8||
           originalStack==null||originalCount<1)
            throw new IllegalArgumentException("snowball observation");
        snowPlayer=player;snowWorld=world;
        snowStack=originalStack;snowSlot=slot;snowCount=originalCount;
        snowItem=originalStack.getItem();
        snowDamage=originalStack.getItemDamage();
        snowNativeLocalMutation=nativeLocalMutation;
        snowball.arm(generation,tick);
    }
    void sample() {
        final long epoch=host.services().generation(),tick=host.services().tick();
        if(rod.pending()) {
            boolean same=mc.thePlayer!=null&&mc.thePlayer==rodPlayer &&
                mc.theWorld==rodWorld &&
                mc.theWorld.getEntityByID(rodPlayer.getEntityId())==rodPlayer;
            final EntityFishHook hook=same?rodPlayer.fishEntity:null;
            boolean newRegisteredHook=same && rodProvenance.eligible(
                mc.thePlayer,mc.theWorld,hook,
                hook==null?null:hook.angler,
                hook==null?null:hook.worldObj,
                hook==null?null:rodWorld.getEntityByID(hook.getEntityId()),
                hook==null||hook.isDead);
            Outcome o=rod.observe(epoch,tick,same,
                newRegisteredHook?Signal.ROD_HOOK_VISIBLE:Signal.NONE);
            record(o,true);
            if(!rod.pending()) {
                rodPlayer=null;rodWorld=null;rodProvenance.reset();
            }
        }
        if(snowball.pending()) {
            boolean same=mc.thePlayer!=null&&mc.thePlayer==snowPlayer &&
                mc.theWorld==snowWorld &&
                mc.theWorld.getEntityByID(snowPlayer.getEntityId())==snowPlayer;
            Signal signal=Signal.NONE;
            if(same) {
                ItemStack now=snowPlayer.inventory.getStackInSlot(snowSlot);
                signal=ProjectileInventoryReconciliation.classify(
                    snowStack,snowItem,snowCount,snowDamage,snowNativeLocalMutation,
                    now,now==null?null:now.getItem(),
                    now==null?0:now.stackSize,
                    now==null?0:now.getItemDamage());
            }
            Outcome o=snowball.observe(epoch,tick,same,signal);
            record(o,false);
            if(!snowball.pending()) {
                snowPlayer=null;snowWorld=null;snowStack=null;
                snowItem=null;snowSlot=snowCount=-1;snowDamage=0;
                snowNativeLocalMutation=false;
            }
        }
    }
    private void record(Outcome outcome,boolean isRod) {
        if(!host.diagnosticsEnabled())return;
        switch(outcome) {
            case LOCAL_HOOK:
                host.diagnostics().record(Counter.PROJECTILE_ROD_LOCAL_HOOK);
                break;
            case LOCAL_DECREASE:
                host.diagnostics().record(Counter.PROJECTILE_SNOWBALL_LOCAL_DECREASE);
                break;
            case AMBIGUOUS_REPLACEMENT:
                host.diagnostics().record(Counter.PROJECTILE_OBSERVATION_AMBIGUOUS);
                break;
            case EXPIRED:
                host.diagnostics().record(Counter.PROJECTILE_OBSERVATION_EXPIRED);
                break;
            case INVALIDATED:
                host.diagnostics().record(Counter.PROJECTILE_OBSERVATION_INVALIDATED);
                break;
            default:break;
        }
    }
    void reset() {
        rod.reset();snowball.reset();rodProvenance.reset();
        rodPlayer=snowPlayer=null;
        rodWorld=snowWorld=null;
        snowStack=null;snowItem=null;snowSlot=snowCount=-1;
        snowDamage=0;snowNativeLocalMutation=false;
    }
}
