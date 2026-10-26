package dev.ghostfusion.features;

/**
 * Conservative owner-thread 1.8.9 inventory hotbar sorter.
 * Snapshot indices 0..35 correspond to ContainerPlayer slots 9..44,
 * and hotbar indices 0..8 correspond to snapshot slots 27..35.
 *
 * At most one vanilla hotbar-swap intent may be pending. A missing observed
 * two-slot change suspends the window rather than blindly retrying clicks.
 */
public final class InventoryManagerPolicy {
    public static final int EMPTY=0, SWORD=1, PICKAXE=2, AXE=3, SHOVEL=4, OTHER=5;
    public static final class Slot {
        public final int kind;
        public final double quality;
        public Slot(int kind,double quality) {
            if(kind<EMPTY || kind>OTHER || !Double.isFinite(quality) ||
               quality<0 || (kind==EMPTY && quality!=0))
                throw new IllegalArgumentException("invalid inventory slot score");
            this.kind=kind;this.quality=quality;
        }
    }
    public static final class Swap {
        public final int sourceContainerSlot, targetHotbarIndex;
        private Swap(int sourceContainerSlot,int targetHotbarIndex) {
            this.sourceContainerSlot=sourceContainerSlot;
            this.targetHotbarIndex=targetHotbarIndex;
        }
    }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1,openAt=-1,lastSwap=-1,pendingAt=-1;
    private int pendingSource=-1,pendingTarget=-1,nextCategory=0;
    private boolean blocked;

    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("inventory manager owner thread");
    }
    public Swap next(boolean eligible,Slot[] snapshot,boolean allowUpgrade,
                     int openDelay,int clickDelay,boolean priorSwapConfirmed,
                     long tick,long generation) {
        checkOwner();
        if(tick<0 || generation<0 || openDelay<2 || openDelay>20 ||
           clickDelay<2 || clickDelay>20 || snapshot==null ||
           snapshot.length!=36)
            throw new IllegalArgumentException("invalid inventory manager session");
        for(Slot slot:snapshot)if(slot==null)
            throw new IllegalArgumentException("missing inventory slot");
        if(epoch!=generation || (lastTick>=0 && tick<lastTick)) {
            clear();
            epoch=generation;
        }
        lastTick=tick;
        if(!eligible) {
            clear();
            epoch=generation;lastTick=tick;
            return null;
        }
        if(openAt<0) {
            openAt=tick;
            return null;
        }
        if(blocked || tick-openAt<openDelay)return null;
        if(pendingSource>=0) {
            if(priorSwapConfirmed) {
                pendingSource=-1;pendingTarget=-1;pendingAt=-1;
            } else {
                if(tick-pendingAt>=12)blocked=true;
                return null;
            }
        }
        if(lastSwap>=0 && tick-lastSwap<clickDelay)return null;
        // Use a rotating category cursor, avoiding repeated bias for sword.
        for(int offset=0;offset<4;offset++) {
            final int categoryIndex=(nextCategory+offset)%4;
            final int desiredKind=categoryIndex+1;
            final int destination=27+categoryIndex; // hotbar 0..3
            final Slot current=snapshot[destination];
            // Fill empty destinations by default. Optional upgrade must
            // replace only an item of the exact same category.
            if(current.kind!=EMPTY &&
               !(allowUpgrade && current.kind==desiredKind)) continue;
            int best=-1;
            double bestScore=current.kind==desiredKind ? current.quality : -1;
            for(int i=0;i<snapshot.length;i++) {
                if(i==destination)continue;
                // Never disturb the other three reserved hotbar targets.
                if(i>=27 && i<=30)continue;
                final Slot candidate=snapshot[i];
                if(candidate.kind!=desiredKind)continue;
                if(candidate.quality>bestScore+0.000001) {
                    best=i;bestScore=candidate.quality;
                }
            }
            if(best<0)continue;
            pendingSource=9+best;
            pendingTarget=categoryIndex;
            pendingAt=tick;lastSwap=tick;
            nextCategory=(categoryIndex+1)%4;
            return new Swap(pendingSource,pendingTarget);
        }
        return null;
    }
    public void blockWindow() {
        checkOwner();blocked=true;
    }
    public void reset() {checkOwner();epoch=-1;lastTick=-1;clear();}
    private void clear() {
        openAt=-1;lastSwap=-1;pendingAt=-1;
        pendingSource=-1;pendingTarget=-1;nextCategory=0;blocked=false;
    }
}
