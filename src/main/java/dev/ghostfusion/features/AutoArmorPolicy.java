package dev.ghostfusion.features;

/**
 * Owner-thread AutoArmor transaction planner for vanilla ContainerPlayer.
 * Armor kind 0..3 corresponds to helmet/chestplate/leggings/boots and to
 * ContainerPlayer slots 5..8. Storage slots 0..35 map to container 9..44.
 *
 * Emits only one native vanilla quick-move intent at a time. An occupied
 * armor slot is first moved to free storage and its removal OBSERVED before
 * a candidate is equipped. No confirmation => block this GUI session.
 */
public final class AutoArmorPolicy {
    public static final int EMPTY=-1, OTHER=4;
    public enum Step { REMOVE, EQUIP }
    public static final class Piece {
        public final int kind;
        public final double score;
        public Piece(int kind,double score) {
            if(kind<EMPTY || kind>OTHER || !Double.isFinite(score) ||
               score<0 || (kind==EMPTY && score!=0))
                throw new IllegalArgumentException("invalid armor");
            this.kind=kind;this.score=score;
        }
    }
    public static final class Action {
        public final Step step;
        public final int containerSlot, armorKind, candidateSlot;
        private Action(Step phase,int slot,int kind,int source) {
            step=phase;containerSlot=slot;armorKind=kind;candidateSlot=source;
        }
    }
    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1,openedAt=-1,lastActionAt=-1,pendingAt=-1;
    private Step pending;
    private int pendingKind=-1,followSource=-1,followKind=-1,nextKind=0;
    private double followScore;
    private boolean blocked;

    private void owner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("auto armor owner thread");
    }
    public Action next(boolean eligible,Piece[] storage,Piece[] worn,
                       boolean upgrade,int openingDelay,int clickDelay,
                       boolean pendingObserved,long tick,long epoch) {
        owner();
        if(storage==null || storage.length!=36 ||
           worn==null || worn.length!=4 ||
           openingDelay<2 || openingDelay>20 ||
           clickDelay<2 || clickDelay>20 || tick<0 || epoch<0)
            throw new IllegalArgumentException("invalid armor snapshot/clock");
        for(Piece p:storage)if(p==null)
            throw new IllegalArgumentException("null storage piece");
        for(Piece p:worn)if(p==null)
            throw new IllegalArgumentException("null equipped piece");
        if(epoch!=generation) {
            clear();
            // Tick numbering may restart with a new session/world epoch.
            // The old owner's monotonic clock cannot fence the new world.
            lastTick=-1;
            generation=epoch;
        }
        if(lastTick>=0 && tick<lastTick)
            throw new IllegalArgumentException("nonmonotonic armor tick");
        lastTick=tick;
        if(!eligible) {
            clear();
            generation=epoch;lastTick=tick;
            return null;
        }
        if(openedAt<0) {
            openedAt=tick;
            return null;
        }
        if(blocked)return null;
        if(pending!=null) {
            if(!pendingObserved) {
                if(tick-pendingAt>=12)blocked=true;
                return null;
            }
            pending=null;pendingAt=-1;pendingKind=-1;
        }
        if(tick-openedAt<openingDelay ||
           lastActionAt>=0 && tick-lastActionAt<clickDelay)
            return null;
        // Complete an already-observed armor removal FIRST. Never select a
        // different candidate while the former armor item is in storage.
        if(followSource>=0) {
            final int src=followSource-9;
            if(src<0 || src>=36 || followKind<0 || followKind>3 ||
               worn[followKind].kind!=EMPTY ||
               storage[src].kind!=followKind ||
               storage[src].score+0.000001<followScore) {
                blocked=true;
                return null;
            }
            return emit(Step.EQUIP,followSource,followKind,followSource,tick);
        }
        int vacant=0;
        for(Piece p:storage)if(p.kind==EMPTY)vacant++;
        for(int offset=0;offset<4;offset++) {
            final int kind=(nextKind+offset)%4;
            final Piece equipped=worn[kind];
            if(equipped.kind!=EMPTY && equipped.kind!=kind)
                continue;
            final boolean filled=equipped.kind==kind;
            if(filled && (!upgrade || vacant==0))continue;
            int best=-1;
            final double threshold=filled?equipped.score:-1;
            double score=threshold;
            for(int i=0;i<storage.length;i++) {
                Piece item=storage[i];
                if(item.kind!=kind || item.score<=score+0.000001)
                    continue;
                best=i;score=item.score;
            }
            if(best<0)continue;
            followSource=9+best;followKind=kind;followScore=score;
            nextKind=(kind+1)%4;
            if(filled) {
                // A completely free storage slot is required before moving
                // off existing equipped armor; never drop or throw anything.
                return emit(Step.REMOVE,5+kind,kind,followSource,tick);
            }
            return emit(Step.EQUIP,followSource,kind,followSource,tick);
        }
        return null;
    }
    private Action emit(Step step,int clickSlot,int kind,int candidate,long tick) {
        pending=step;
        pendingAt=tick;
        pendingKind=kind;
        lastActionAt=tick;
        // After EQUIP, only confirmation can authorize another category.
        // If it fails, the session is blocked instead of retrying.
        if(step==Step.EQUIP) {
            followSource=-1;
            followKind=-1;
            followScore=0;
        }
        return new Action(step,clickSlot,kind,candidate);
    }
    public void blockSession() {owner();blocked=true;}
    public void reset() {
        owner();generation=-1;lastTick=-1;clear();
    }
    private void clear() {
        openedAt=-1;lastActionAt=-1;pendingAt=-1;
        pending=null;pendingKind=-1;followSource=-1;followKind=-1;
        followScore=0;nextKind=0;blocked=false;
    }
}
