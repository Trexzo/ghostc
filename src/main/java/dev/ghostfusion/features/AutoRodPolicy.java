package dev.ghostfusion.features;

/**
 * AutoRod: owner-thread, epoch-fenced one-shot hotbar/right-click transaction.
 * No Minecraft dependencies. Admission alone NEVER changes a slot or uses an
 * item: the Forge owner must commit after each actual native action.
 *
 * ARMING/READY -> SELECT -> USE -> RESTORE -> COOLDOWN.
 * Manual selection away from our rod slot cancels ownership WITHOUT restoring
 * an old slot over the user's decision. New damage/target acquisition cannot
 * replay an interrupted old action.
 */
public final class AutoRodPolicy {
    public enum Action { NONE, SELECT, USE, RESTORE }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1,lastUse=-1,stageTick=-1;
    private boolean lastAttack,armed;
    private int original=-1,rod=-1;
    private Action next=Action.NONE;
    private Action pending=Action.NONE;
    private int cooldown=30;
    private boolean everUsed;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("AutoRod owner thread");
    }
    public boolean ownsHotbar() { check(); return original>=0 && rod>=0; }
    public int originalSlot() { check(); return original; }
    public int ownedRodSlot() { check(); return rod; }
    private void clearOwnership() {
        original=rod=-1;
        pending=next=Action.NONE;
        stageTick=-1;
    }

    /**
     * Runs exactly once per session tick. attackPhysical is a genuine
     * LWJGL physical hold, not KeyBinding synthetic state. active includes
     * current player, target, manual-use and world safety validations.
     */
    public Action decide(long generation,long tick,boolean attackPhysical,
                         boolean active,int currentSlot,int rodSlot,
                         int cooldownTicks) {
        check();
        if(generation<0||tick<0||currentSlot<0||currentSlot>8||
           rodSlot<-1||rodSlot>8||cooldownTicks<10||cooldownTicks>100)
            throw new IllegalArgumentException("invalid AutoRod sample");
        if(generation!=epoch) {
            clearOwnership();epoch=generation;lastTick=lastUse=-1;
            armed=false;lastAttack=attackPhysical;cooldown=cooldownTicks;
            return Action.NONE;
        }
        if(lastTick>=0&&tick<lastTick)
            throw new IllegalArgumentException("AutoRod backward tick");
        if(tick==lastTick)return Action.NONE;
        lastTick=tick;
        boolean edge=attackPhysical&&!lastAttack;
        lastAttack=attackPhysical;
        pending=Action.NONE;
        stageTick=-1;
        if(cooldownTicks!=cooldown) {
            // Changed settings cannot borrow an earlier press/cooldown.
            cooldown=cooldownTicks;armed=false;edge=false;
        }
        if(!attackPhysical)armed=true;

        if(ownsHotbar()) {
            if(currentSlot!=rod) {
                clearOwnership(); // manual hotbar override always wins
                return Action.NONE;
            }
            if(next==Action.USE && !active)next=Action.RESTORE;
            Action output=next;
            if(output!=Action.NONE) {
                pending=output;stageTick=tick;
            }
            return output;
        }
        if(!active || !armed || !edge || rodSlot<0 ||
           rodSlot==currentSlot || (lastUse>=0&&tick-lastUse<cooldownTicks))
            return Action.NONE;
        original=currentSlot;rod=rodSlot;
        next=Action.SELECT;pending=Action.SELECT;stageTick=tick;
        return Action.SELECT;
    }
    /** Must be called only after actual native hotbar or use method output. */
    public void committed(Action action,long generation,long tick) {
        check();
        if(action==null||action==Action.NONE || generation!=epoch ||
           tick!=stageTick || action!=pending)
            throw new IllegalStateException("unprepared AutoRod commit");
        if(action==Action.SELECT)next=Action.USE;
        else if(action==Action.USE) {
            lastUse=tick;everUsed=true;next=Action.RESTORE;
        } else if(action==Action.RESTORE) {
            clearOwnership();next=Action.NONE;
        }
        pending=Action.NONE;stageTick=-1;
    }
    /** A failed use still demands a safe restoration, not a hidden retry. */
    public void cancelPrepared() {
        check();
        if(pending==Action.SELECT)clearOwnership();
        else if(pending==Action.USE)next=Action.RESTORE;
        pending=Action.NONE;stageTick=-1;
    }
    /**
     * A native controller may safely restore on disable/GUI/focus loss only
     * if same-world player still holds OUR rod slot and no manual hotbar
     * action is in progress. Do not call this after a world swap.
     */
    public int relinquish(int currentSlot,boolean sameWorld,boolean manualHotbar) {
        check();
        int restore=ownsHotbar() && sameWorld && !manualHotbar &&
            currentSlot==rod ? original : -1;
        clearOwnership();armed=false;lastAttack=true;
        return restore;
    }
    public boolean castObserved() {check();return everUsed;}
    public void reset() {
        check();clearOwnership();epoch=lastTick=lastUse=-1;
        armed=false;lastAttack=true;everUsed=false;cooldown=30;
    }
}
