package dev.ghostfusion.features;

import dev.ghostfusion.services.SessionServices;

/**
 * Last-resort, owner-thread cleanup after a shared combat RuntimeException.
 * Only combat-owned camera/attack/use requests are revoked. Unrelated bridge,
 * movement, inventory and projectile owners are intentionally untouched.
 *
 * A controller reset is best-effort: if it throws, cancellation still runs.
 * A failed cancellation cannot prevent other independent cancellations.
 * NativePulseSettlement is never reset or rearmed by this class.
 */
public final class CombatFaultCleanup {
    private CombatFaultCleanup() {}

    public static void contain(SessionServices session,Runnable reset) {
        if(session==null || reset==null)
            throw new IllegalArgumentException("combat fault cleanup dependencies");
        // Validate owner thread BEFORE invoking any supplied reset callback.
        session.generation();
        try { reset.run(); }
        catch (RuntimeException ignored) { /* frame is already quarantined */ }

        try { session.rotations().cancel("combat.aimassist"); }
        catch (RuntimeException ignored) { /* continue independent revocation */ }
        try { session.actions().cancelOwner("combat.clicker"); }
        catch (RuntimeException ignored) { /* continue independent revocation */ }
        try { session.actions().cancelOwner("combat.triggerbot"); }
        catch (RuntimeException ignored) { /* continue independent revocation */ }
        try { session.actions().cancelOwner("combat.blockhit"); }
        catch (RuntimeException ignored) { /* continue independent revocation */ }
    }
}
