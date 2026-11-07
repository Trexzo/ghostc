package dev.ghostfusion.features;

import dev.ghostfusion.services.SessionServices;

/**
 * Owner-thread containment for a shared combat reset at a world lifecycle
 * boundary. The host must complete worldPresence transition before calling.
 *
 * An incomplete reset is not evidence that native ATTACK/USE was rolled back.
 * Pin the exact failing native context and quarantine optional combat before
 * independently cancelling combat-only coordinator intents. Movement,
 * inventory and projectile state are not touched here.
 */
public final class CombatWorldBoundaryRecovery {
    private CombatWorldBoundaryRecovery() {}

    /**
     * @return true only when the normal controller reset completed; false if
     *         its ordinary RuntimeException was contained and combat fenced.
     */
    public static boolean reset(SessionServices session,
                                CombatFrameFaultFence frameFault,
                                Object world,Object player,Runnable controllerReset) {
        if(session==null||frameFault==null||controllerReset==null)
            throw new IllegalArgumentException("combat boundary dependencies");
        final long epoch=session.generation(); // Validate owner before callbacks.
        try {
            controllerReset.run();
            return true;
        } catch (RuntimeException resetFailure) {
            // A SWITCH exposes the new native object before ENTER completes.
            // Pin that exact object at the fault's own epoch so a subsequent
            // worldPresence(true) alone cannot rearm a failed reset.
            frameFault.observe(epoch,world,player);
            frameFault.quarantine();
            // The reset may fail again. Cancel each combat intent anyway.
            // Errors and foreign-thread violations are not swallowed.
            CombatFaultCleanup.contain(session,controllerReset);
            return false;
        }
    }
}
