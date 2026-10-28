package dev.ghostfusion.features;

/**
 * AutoSnowball: owner-thread, epoch-fenced one-shot hotbar/right-click transaction.
 * No Minecraft dependencies. Admission alone NEVER changes a slot or uses an
 * item: the Forge owner must commit after each actual native action.
 *
 * ARMING/READY -> SELECT -> USE -> RESTORE -> COOLDOWN.
 * Manual selection away from our snowball slot cancels ownership WITHOUT restoring
 * an old slot over the user's decision. A new physical attack edge cannot
 * replay an interrupted old action.
 */
public final class AutoSnowballPolicy {
    public enum Action { NONE, SELECT, USE, RESTORE }
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,lastTick=-1,lastUse=-1,stageTick=-1;
    private boolean lastAttack,armed;
    private int original=-1,snowball=-1;
    private Action next=Action.NONE;
    private Action pending=Action.NONE;
    private int cooldown=20;
    private boolean everUsed;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("AutoSnowball owner thread");
    }
    public boolean ownsHotbar() { check(); return original>=0 && snowball>=0; }
    public int originalSlot() { check(); return original; }
    public int ownedSnowballSlot() { check(); return snowball; }
    private void clearOwnership() {
        original=snowball=-1;everUsed=false;
        pending=next=Action.NONE;
        stageTick=-1;
    }

    /**
     * Runs exactly once per session tick. attackPhysical is a genuine
     * LWJGL physical hold, not KeyBinding synthetic state. active includes
     * current player, target, manual-use and world safety validations.
     */
    public Action decide(long generation,long tick,boolean attackPhysical,
                         boolean active,int currentSlot,int snowballSlot,
                         int cooldownTicks) {
        check();
        if(generation<0||tick<0||currentSlot<0||currentSlot>8||
           snowballSlot<-1||snowballSlot>8||cooldownTicks<10||cooldownTicks>100)
            throw new IllegalArgumentException("invalid AutoSnowball sample");
        if(generation!=epoch) {
            clearOwnership();epoch=generation;lastTick=lastUse=-1;
            // An observed RELEASE on the first world tick is sufficient
            // to arm the NEXT genuine rising physical press. A held key
            // at entry remains disarmed until an actual release is seen.
            armed=!attackPhysical;lastAttack=attackPhysical;cooldown=cooldownTicks;
            return Action.NONE;
        }
        if(lastTick>=0&&tick<lastTick)
            throw new IllegalArgumentException("AutoSnowball backward tick");
        if(tick==lastTick)return Action.NONE;
        lastTick=tick;
        boolean edge=attackPhysical&&!lastAttack;
        lastAttack=attackPhysical;
        pending=Action.NONE;
        stageTick=-1;
        if(cooldownTicks!=cooldown) {
            // Live configuration edits cancel an in-progress snowball use and
            // request a safe restoration instead of inheriting old consent.
            cooldown=cooldownTicks;armed=false;edge=false;
            if(ownsHotbar())next=Action.RESTORE;
        }
        if(!attackPhysical)armed=true;

        if(ownsHotbar()) {
            if(currentSlot!=snowball) {
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
        if(!active || !armed || !edge || snowballSlot<0 ||
           snowballSlot==currentSlot || (lastUse>=0&&tick-lastUse<cooldownTicks))
            return Action.NONE;
        original=currentSlot;snowball=snowballSlot;everUsed=false;
        next=Action.SELECT;pending=Action.SELECT;stageTick=tick;
        return Action.SELECT;
    }
    /** Must be called only after actual native hotbar or use method output. */
    public void committed(Action action,long generation,long tick) {
        check();
        if(action==null||action==Action.NONE || generation!=epoch ||
           tick!=stageTick || action!=pending)
            throw new IllegalStateException("unprepared AutoSnowball commit");
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
     * if same-world player still holds OUR snowball slot and no manual hotbar
     * action is in progress. Do not call this after a world swap.
     */
    public int relinquish(int currentSlot,boolean sameWorld,boolean manualHotbar) {
        check();
        int restore=ownsHotbar() && sameWorld && !manualHotbar &&
            currentSlot==snowball ? original : -1;
        clearOwnership();armed=false;lastAttack=true;
        return restore;
    }
    public boolean castObserved() {check();return everUsed;}
    public void reset() {
        check();clearOwnership();epoch=lastTick=lastUse=-1;
        armed=false;lastAttack=true;everUsed=false;cooldown=20;
    }
}
