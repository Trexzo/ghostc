package dev.ghostfusion.features;

/**
 * Read-only, owner-thread acceptance of native combat MODULE COOPERATION.
 * A frame grant is not a native action. A KeyBinding pulse is not an attack
 * callback; a Forge AttackEntityEvent is not a server hit or ACK.
 *
 * No game objects, target IDs, names, UUIDs, timings, packets or positions
 * are retained. Never authorizes or delays a combat write.
 */
public final class CombatFrameAcceptance {
    public enum Result {
        INACTIVE, COMBAT_RAN, COMBAT_SKIPPED, PROJECTILE_EXCLUSIVE,
        MANUAL_OVERRIDE, OWNER_CONFLICT, POST_PROJECTILE_REARM,
        UNEXPECTED_NATIVE_WRITE
    }
    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1;
    private boolean priorProjectile;
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("combat acceptance owner thread");
    }
    public void reset() {
        check();generation=lastTick=-1;priorProjectile=false;
    }

    /**
     * Native write flags are deltas of opt-in counters sampled directly
     * before/after this frame's controller calls, not inferred from intents.
     * Synthetic attack is CLICKER/TRIGGER only, not vanilla manual input.
     */
    public Result observe(long epoch,long tick,boolean planCombat,
                          boolean rodOwns,boolean snowballOwns,
                          boolean manualOverride,boolean ownerConflict,
                          boolean combatCalled,boolean nativeWrite,
                          boolean syntheticAttackWrite) {
        check();
        if(epoch<0||tick<0)
            throw new IllegalArgumentException("invalid combat acceptance clock");
        if(syntheticAttackWrite&&!nativeWrite)
            throw new IllegalArgumentException("synthetic dispatch must be a native write");
        if(epoch!=generation) {
            generation=epoch;lastTick=-1;priorProjectile=false;
        }
        if(tick<=lastTick)
            throw new IllegalArgumentException("duplicate/backward combat frame");
        lastTick=tick;
        boolean exclusive=rodOwns||snowballOwns;
        boolean blocked=exclusive||manualOverride||ownerConflict;
        boolean violation=(blocked&&(combatCalled||nativeWrite))||
            (!combatCalled&&nativeWrite)||
            (priorProjectile&&syntheticAttackWrite);
        Result result;
        if(violation)result=Result.UNEXPECTED_NATIVE_WRITE;
        else if(ownerConflict)result=Result.OWNER_CONFLICT;
        else if(manualOverride)result=Result.MANUAL_OVERRIDE;
        else if(exclusive)result=Result.PROJECTILE_EXCLUSIVE;
        else if(combatCalled&&priorProjectile)
            result=Result.POST_PROJECTILE_REARM;
        else if(combatCalled)result=Result.COMBAT_RAN;
        else if(planCombat)result=Result.COMBAT_SKIPPED;
        else result=Result.INACTIVE;
        priorProjectile=exclusive;
        return result;
    }
}
