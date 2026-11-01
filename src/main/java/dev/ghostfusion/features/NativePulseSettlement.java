package dev.ghostfusion.features;

import java.lang.ref.WeakReference;

/**
 * Owner-thread, session-scoped final settlement of optional Minecraft
 * KeyBinding pulses. An exception may happen AFTER vanilla accepted a pulse:
 * no rollback or replay is safe when native delivery is ambiguous.
 *
 * Each ATTACK/USE lane becomes UNCERTAIN if dispatch was begun but either
 * native KeyBinding.onTick or all required policy commits did not finish.
 * An uncertain lane remains closed across ordinary feature resets, manual
 * input and focus/GUI transitions. A genuinely new session generation is
 * the only rearm. A completed pulse is never mistaken for an unknown failure
 * merely because optional diagnostics failed afterwards.
 *
 * No packets, input, entities, server claims, or telemetry are produced here.
 */
public final class NativePulseSettlement {
    public enum Lane { ATTACK, USE }
    public enum Phase { IDLE, IN_FLIGHT, RETURNED, UNCERTAIN }
    private final Thread owner=Thread.currentThread();
    private long generation=-1;
    // Weak identity references cannot retain a disconnected Minecraft World.
    private WeakReference<Object> nativeWorld,nativePlayer;
    private boolean nativePinned;
    private final Phase[] phases={Phase.IDLE,Phase.IDLE};
    private final long[] activeTick={-1,-1};
    private final long[] lastAttemptTick={-1,-1};

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("native pulse settlement owner thread");
    }
    private static int index(Lane lane) {
        if(lane==null)throw new IllegalArgumentException("pulse lane");
        return lane.ordinal();
    }
    private void clearForEpoch(long epoch) {
        generation=epoch;
        for(int i=0;i<phases.length;i++) {
            phases[i]=Phase.IDLE;
            activeTick[i]=-1;
            lastAttemptTick[i]=-1;
        }
    }
    private void clock(long epoch) {
        if(epoch<0)throw new IllegalArgumentException("pulse generation");
        if(epoch<generation)throw new IllegalStateException("stale pulse generation");
        if(epoch!=generation) {
            if(!nativePinned)clearForEpoch(epoch);
            else {
                // Numeric epoch alone is not evidence of a new native world:
                // preserve uncertain lanes across same-object reentries.
                generation=epoch;
                for(int i=0;i<phases.length;i++)
                    if(phases[i]==Phase.IN_FLIGHT||phases[i]==Phase.RETURNED)
                        phases[i]=Phase.UNCERTAIN;
            }
        }
    }
    /**
     * Only a newer session generation AND a different exact world/player
     * instance may clear a pinned native uncertainty. Never rearm on a
     * same-generation object swap before WorldIdentityTracker advances.
     */
    public boolean admitNative(Lane lane,long epoch,Object world,Object player) {
        check();int i=index(lane);
        if(world==null||player==null)return false;
        final long formerGeneration=generation;
        clock(epoch);
        if(!nativePinned) {
            nativeWorld=new WeakReference<Object>(world);
            nativePlayer=new WeakReference<Object>(player);
            nativePinned=true;
        } else if(nativeWorld.get()!=world||nativePlayer.get()!=player) {
            if(epoch==formerGeneration)return false;
            clearForEpoch(epoch);
            nativeWorld=new WeakReference<Object>(world);
            nativePlayer=new WeakReference<Object>(player);
        }
        return phases[i]==Phase.IDLE;
    }
    /** Pure-policy compatibility; cannot clear a native-pinned uncertainty. */
    public boolean admit(Lane lane,long epoch) {
        check();int i=index(lane);clock(epoch);
        return phases[i]==Phase.IDLE;
    }
    public Phase phase(Lane lane,long epoch) {
        check();int i=index(lane);clock(epoch);return phases[i];
    }
    public void begin(Lane lane,long epoch,long tick) {
        check();int i=index(lane);clock(epoch);
        if(tick<0||phases[i]!=Phase.IDLE || tick<=lastAttemptTick[i])
            throw new IllegalStateException("duplicate or uncertain native pulse");
        phases[i]=Phase.IN_FLIGHT;
        activeTick[i]=lastAttemptTick[i]=tick;
    }
    public void nativeReturned(Lane lane,long epoch,long tick) {
        require(lane,epoch,tick,Phase.IN_FLIGHT);
        phases[lane.ordinal()]=Phase.RETURNED;
    }
    public void committed(Lane lane,long epoch,long tick) {
        require(lane,epoch,tick,Phase.RETURNED);
        int i=lane.ordinal();
        phases[i]=Phase.IDLE;
        activeTick[i]=-1;
    }
    /**
     * Called from finally after begin. If native call or policy commit
     * aborted at any point, refuse all further pulses in this generation.
     */
    public void abandon(Lane lane,long epoch,long tick) {
        check();int i=index(lane);clock(epoch);
        if(activeTick[i]!=tick || phases[i]==Phase.IDLE ||
           phases[i]==Phase.UNCERTAIN)return;
        phaseUncertain(i);
    }
    private void phaseUncertain(int i) {
        phases[i]=Phase.UNCERTAIN;
    }
    private void require(Lane lane,long epoch,long tick,Phase expected) {
        check();int i=index(lane);clock(epoch);
        if(tick<0||activeTick[i]!=tick||phases[i]!=expected)
            throw new IllegalStateException("invalid native pulse settlement");
    }
}
