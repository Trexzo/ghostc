package dev.ghostfusion.features;

/**
 * One owner-thread ledger of combat module lifecycle revisions. Individual
 * module enable/disable/fault transitions are not reconstructible from
 * their final ENABLED bit: OFF->ON may happen between two END ticks.
 *
 * Per-module bits reset only their own native policy/scheduler. The native
 * ATTACK/USE uncertain settlement is deliberately NOT owned here.
 */
public final class CombatLifecycleFence {
    public static final int AIM=1, CLICK=2, TRIGGER=4, HIT=8, BLOCK=16;
    public static final int ALL=AIM|CLICK|TRIGGER|HIT|BLOCK;
    private final Thread owner=Thread.currentThread();
    private long generation=-1;
    private long aim=-1,click=-1,trigger=-1,hit=-1,block=-1;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("combat lifecycle owner thread");
    }

    /** Read-only input; a fresh session resets every policy. */
    public int observe(long epoch,long aimRevision,long clickRevision,
                       long triggerRevision,long hitRevision,long blockRevision) {
        check();
        if(epoch<0||aimRevision<0||clickRevision<0||triggerRevision<0||
           hitRevision<0||blockRevision<0)
            throw new IllegalArgumentException("combat lifecycle clock");
        if(generation>=0&&epoch<generation)
            throw new IllegalStateException("backward combat lifecycle epoch");
        boolean fresh=generation!=epoch;
        if(!fresh && (aimRevision<aim||clickRevision<click||
                      triggerRevision<trigger||hitRevision<hit||
                      blockRevision<block))
            throw new IllegalStateException("backward combat module revision");
        int changed=0;
        if(fresh||aim!=aimRevision)changed|=AIM;
        if(fresh||click!=clickRevision)changed|=CLICK;
        if(fresh||trigger!=triggerRevision)changed|=TRIGGER;
        if(fresh||hit!=hitRevision)changed|=HIT;
        if(fresh||block!=blockRevision)changed|=BLOCK;
        generation=epoch;
        aim=aimRevision;click=clickRevision;trigger=triggerRevision;
        hit=hitRevision;block=blockRevision;
        return changed;
    }

    /** Explicit world/focus/Panic teardown; next admission starts clean. */
    public void reset() {
        check();
        generation=-1;
        aim=click=trigger=hit=block=-1;
    }
}
