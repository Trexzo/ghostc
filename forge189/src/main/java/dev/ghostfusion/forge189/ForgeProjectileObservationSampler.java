package dev.ghostfusion.forge189;

import dev.ghostfusion.features.ProjectileObservationWindow;
import dev.ghostfusion.features.ProjectileObservationWindow.Outcome;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
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
    private final ProjectileObservationWindow snowball=new ProjectileObservationWindow();
    private EntityPlayerSP rodPlayer,snowPlayer;
    private World rodWorld,snowWorld;
    private ItemStack snowStack;
    private int snowSlot=-1,snowCount=-1;

    ForgeProjectileObservationSampler(Minecraft client,HostRuntime runtime) {
        if(client==null||runtime==null)
            throw new IllegalArgumentException("null observation dependencies");
        mc=client;host=runtime;
    }
    void armRod(EntityPlayerSP player,World world,long generation,long tick) {
        if(player==null||world==null)throw new IllegalArgumentException("rod session");
        rodPlayer=player;rodWorld=world;
        rod.arm(generation,tick);
    }
    void armSnowball(EntityPlayerSP player,World world,long generation,long tick,
                     int slot,ItemStack originalStack,int originalCount) {
        if(player==null||world==null||slot<0||slot>8||
           originalStack==null||originalCount<1)
            throw new IllegalArgumentException("snowball observation");
        snowPlayer=player;snowWorld=world;
        snowStack=originalStack;snowSlot=slot;snowCount=originalCount;
        snowball.arm(generation,tick);
    }
    void sample() {
        final long epoch=host.services().generation(),tick=host.services().tick();
        if(rod.pending()) {
            boolean same=mc.thePlayer!=null&&mc.thePlayer==rodPlayer &&
                mc.theWorld==rodWorld &&
                mc.theWorld.getEntityByID(rodPlayer.getEntityId())==rodPlayer;
            boolean hook=same&&rodPlayer.fishEntity!=null&&
                !rodPlayer.fishEntity.isDead;
            Outcome o=rod.observe(epoch,tick,same,
                hook?Signal.ROD_HOOK_VISIBLE:Signal.NONE);
            record(o,true);
            if(!rod.pending()) {rodPlayer=null;rodWorld=null;}
        }
        if(snowball.pending()) {
            boolean same=mc.thePlayer!=null&&mc.thePlayer==snowPlayer &&
                mc.theWorld==snowWorld &&
                mc.theWorld.getEntityByID(snowPlayer.getEntityId())==snowPlayer;
            Signal signal=Signal.NONE;
            if(same) {
                ItemStack now=snowPlayer.inventory.getStackInSlot(snowSlot);
                if(now==null || (now==snowStack&&now.stackSize<snowCount))
                    signal=Signal.SNOWBALL_COUNT_DECREASED;
                else if(now!=snowStack||now.stackSize>snowCount)
                    signal=Signal.INVENTORY_REPLACED;
            }
            Outcome o=snowball.observe(epoch,tick,same,signal);
            record(o,false);
            if(!snowball.pending()) {
                snowPlayer=null;snowWorld=null;snowStack=null;
                snowSlot=snowCount=-1;
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
        rod.reset();snowball.reset();
        rodPlayer=snowPlayer=null;
        rodWorld=snowWorld=null;
        snowStack=null;snowSlot=snowCount=-1;
    }
}
