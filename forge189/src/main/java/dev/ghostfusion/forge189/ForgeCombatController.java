package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.RotationCoordinator;
import dev.ghostfusion.services.SessionServices;
import dev.ghostfusion.services.WorldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;

/**
 * Single Forge owner of optional local camera and attack-key pulse writes.
 * Both feature producers submit intent through G3 coordinators before this
 * bridge consumes it. No packet injection, hooks or bypass routines.
 */
public final class ForgeCombatController {
    private static final String AIM_ID = "combat.aimassist";
    private static final String CLICK_ID = "combat.clicker";

    private final Minecraft mc;
    private final HostRuntime host;
    private final AimAssistPolicy aim = new AimAssistPolicy();
    private final FixedClickScheduler scheduler = new FixedClickScheduler();

    ForgeCombatController(Minecraft mc, HostRuntime host) {
        if (mc == null || host == null) throw new IllegalArgumentException("null bridge dependency");
        this.mc = mc;
        this.host = host;
    }

    void tick() {
        SessionServices session = host.services();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || !session.connected() ||
            mc.currentScreen != null || !mc.inGameHasFocus) {
            reset();
            return;
        }

        boolean held = mc.gameSettings.keyBindAttack.isKeyDown();
        WorldSnapshot snapshot = session.snapshot();

        if (host.featureEnabled(AIM_ID) && snapshot != null &&
            (!host.aimRequireClick() || held)) {
            // Aim at player upper torso; policy never writes rotation itself.
            AimAssistPolicy.Suggestion suggestion = aim.suggest(
                snapshot, player.getEyeHeight(), 1.25,
                host.aimRange(), host.aimFov(), host.aimDegreesPerTick(),
                host.aimVisibleOnly());
            if (suggestion != null) {
                session.rotations().request(AIM_ID, RotationCoordinator.Scope.CAMERA,
                    suggestion.yaw, suggestion.pitch, 10,
                    session.tick(), 1, session.generation());
            } else {
                session.rotations().cancel(AIM_ID);
            }
        } else session.rotations().cancel(AIM_ID);

        RotationCoordinator.Decision rotation = session.rotations().decide(
            RotationCoordinator.Scope.CAMERA, session.tick(), session.generation(),
            player.rotationYaw, player.rotationPitch);
        if (AIM_ID.equals(rotation.owner) && host.featureEnabled(AIM_ID) &&
            (!host.aimRequireClick() || held)) {
            player.rotationYaw = rotation.yaw;
            player.rotationPitch = rotation.pitch;
        }

        boolean validTarget = mc.objectMouseOver != null &&
            mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY &&
            mc.objectMouseOver.entityHit instanceof EntityLivingBase &&
            mc.objectMouseOver.entityHit != player &&
            !mc.objectMouseOver.entityHit.isDead;
        boolean enabled = host.featureEnabled(CLICK_ID) &&
            (!host.clickEntityOnly() || validTarget);
        if (scheduler.due(System.nanoTime(), enabled, held, host.clickCps())) {
            session.actions().request(CLICK_ID, ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT, 0, 10,
                session.tick(), 1, session.generation());
        }
        ActionCoordinator.Decision action = session.actions().consume(
            ActionCoordinator.Type.ATTACK, ActionCoordinator.Phase.OUTPUT,
            session.tick(), session.generation());
        if (action != null && CLICK_ID.equals(action.owner) &&
            host.featureEnabled(CLICK_ID) && held &&
            (!host.clickEntityOnly() || validTarget)) {
            // Vanilla processes the queued press via its normal key binding path.
            // Do not call Minecraft.clickMouse via reflection or attackEntity directly.
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        }
    }

    /** Clears held-timer state and both owned requests on world/focus transition. */
    void reset() {
        scheduler.reset();
        SessionServices session = host.services();
        if (!session.connected()) return;
        session.rotations().cancel(AIM_ID);
        session.actions().cancelOwner(CLICK_ID);
    }
}
