package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BridgeAssistPolicy;
import dev.ghostfusion.host.HostRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * One native owner of the optional sneak-key state. This is a normal Forge
 * 1.8.9 feature; it does not intercept packets, place blocks or bypass
 * Minecraft's own movement and collision logic.
 */
final class ForgeBridgeAssistController {
    private static final String MODULE="movement.bridgeassist";
    private final Minecraft mc;
    private final HostRuntime host;
    private final BridgeAssistPolicy policy=new BridgeAssistPolicy();
    private boolean ownsSneak;
    private int ownedCode;
    ForgeBridgeAssistController(Minecraft game,HostRuntime runtime) {
        if(game==null || runtime==null)throw new IllegalArgumentException("null bridge dependency");
        mc=game;host=runtime;
    }
    /** Query the native physical device instead of the possibly synthetic
     * KeyBinding.isKeyDown state. Respect keyboard and mouse remaps. */
    private static boolean physicallyHeld(int code) {
        if(code>0 && code<256 && Keyboard.isCreated()) return Keyboard.isKeyDown(code);
        if(code<0 && Mouse.isCreated()) {
            int button=code+100;
            return button>=0 && button<Mouse.getButtonCount() && Mouse.isButtonDown(button);
        }
        return false;
    }
    private void releaseOwned() {
        if(!ownsSneak)return;
        // If a human is physically holding the same key, don't destroy their
        // input. Ordinary LWJGL key-up will later restore the vanilla state.
        if(!physicallyHeld(ownedCode))
            KeyBinding.setKeyBindState(ownedCode,false);
        ownsSneak=false;
        ownedCode=0;
    }
    void reset() {
        releaseOwned();
        policy.reset();
    }
    void tick() {
        if(mc.theWorld==null || mc.thePlayer==null ||
            mc.currentScreen!=null || !mc.inGameHasFocus ||
            !host.featureEnabled(MODULE) || !host.services().connected()) {
            reset();return;
        }
        boolean healthy=host.runFeature(MODULE,new Runnable() {
            public void run() { evaluate(); }
        });
        if(!healthy)reset();
    }
    private void evaluate() {
        final EntityPlayerSP player=mc.thePlayer;
        final int sneakCode=mc.gameSettings.keyBindSneak.getKeyCode();
        if(ownsSneak && ownedCode!=sneakCode)releaseOwned();
        if(sneakCode==0) {reset();return;}
        final boolean manual=physicallyHeld(sneakCode);
        if(player.movementInput==null) {reset();return;}
        final double forward=player.movementInput.moveForward;
        final double strafe=player.movementInput.moveStrafe;
        final boolean moving=forward>0.15 ||
            (host.bridgeAllowSideways() && Math.abs(strafe)>0.15);
        final boolean eligible=player.onGround && !player.capabilities.isFlying &&
            !player.isRiding() && !player.isInWater() && !player.isInLava() &&
            !player.isCollidedHorizontally &&
            (!host.bridgeRequireBlocks() || holdingBlocks(player));
        boolean supported=false,voidAhead=false;
        if(eligible && moving && !manual) {
            AxisAlignedBB base=player.getEntityBoundingBox();
            // Exact world collision is used instead of treating every
            // non-air block (e.g. slabs, rails, flowers) as solid support.
            supported=!mc.theWorld.getCollidingBoundingBoxes(
                player,base.offset(0.0,-0.085,0.0)).isEmpty();
            if(supported) {
                double radians=Math.toRadians(player.rotationYaw);
                double vx=-Math.sin(radians)*forward+Math.cos(radians)*strafe;
                double vz= Math.cos(radians)*forward+Math.sin(radians)*strafe;
                double magnitude=Math.hypot(vx,vz);
                if(magnitude>0.15) {
                    double distance=host.bridgeLookahead();
                    AxisAlignedBB projected=base.offset(
                        distance*vx/magnitude,-0.085,distance*vz/magnitude);
                    voidAhead=mc.theWorld.getCollidingBoundingBoxes(
                        player,projected).isEmpty();
                }
            }
        }
        final boolean desired=policy.wantsSneak(
            eligible,manual,player.onGround,supported,moving,voidAhead,
            host.bridgeReleaseDelayTicks(),host.services().tick(),
            host.services().generation());
        if(!desired || manual) {
            releaseOwned();
            return;
        }
        if(!ownsSneak && !mc.gameSettings.keyBindSneak.isKeyDown()) {
            KeyBinding.setKeyBindState(sneakCode,true);
            ownsSneak=true;
            ownedCode=sneakCode;
        }
    }
    private static boolean holdingBlocks(EntityPlayerSP player) {
        ItemStack stack=player.getHeldItem();
        return stack!=null && stack.stackSize>0 &&
            stack.getItem() instanceof ItemBlock;
    }
}
