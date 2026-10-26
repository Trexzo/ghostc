package dev.ghostfusion.features;

/**
 * Owner-thread chest-transfer admission only. The real Forge adapter owns
 * GuiChest and vanilla windowClick; this class never edits an inventory.
 *
 * One source slot per acknowledged local change. A click that fails to alter
 * its source slot within 12 client ticks blocks the entire window instead of
 * silently retrying/replaying against server-authoritative inventory state.
 */
public final class ChestStealerPolicy {
    private final Thread owner=Thread.currentThread();
    private long generation=-1,lastTick=-1,openTick=-1,lastClick=-1,pendingSince=-1;
    private int windowId=-1,pendingSlot=-1,nextIndex;
    private boolean blocked;
    private void checkOwner() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("chest stealer owner thread");
    }
    public int next(boolean eligible,int currentWindow,int sourceSlots,
                    int openingDelay,int clickDelay,boolean[] transferable,
                    boolean previousSourceChanged,long tick,long epoch) {
        checkOwner();
        if(tick<0 || epoch<0 || currentWindow<0 || currentWindow>255 ||
           sourceSlots<0 || sourceSlots>54 || openingDelay<2 || openingDelay>20 ||
           clickDelay<2 || clickDelay>20 || transferable==null ||
           transferable.length!=sourceSlots)
            throw new IllegalArgumentException("invalid chest/window timing");
        if(generation!=epoch || lastTick>tick) {
            resetState();
            generation=epoch;
        }
        lastTick=tick;
        if(!eligible) {
            resetState();
            generation=epoch;
            lastTick=tick;
            return -1;
        }
        if(windowId!=currentWindow || openTick<0) {
            resetState();
            generation=epoch;
            lastTick=tick;
            windowId=currentWindow;
            openTick=tick;
            return -1;
        }
        if(blocked || tick-openTick<openingDelay)return -1;
        if(pendingSlot>=0) {
            if(previousSourceChanged) {
                pendingSlot=-1;
                pendingSince=-1;
            } else {
                if(tick-pendingSince>=12)blocked=true;
                return -1;
            }
        }
        if(lastClick>=0 && tick-lastClick<clickDelay)return -1;
        if(sourceSlots==0)return -1;
        for(int offset=0;offset<sourceSlots;offset++) {
            int index=(nextIndex+offset)%sourceSlots;
            if(!transferable[index])continue;
            pendingSlot=index;
            pendingSince=tick;
            lastClick=tick;
            nextIndex=(index+1)%sourceSlots;
            return index;
        }
        return -1;
    }
    /** Temporarily refuse an observed window when an attempted native click
     * throws or the server-side contents fail to change. */
    public void blockWindow() {
        checkOwner();
        blocked=true;
    }
    public void reset() {
        checkOwner();
        generation=-1;
        lastTick=-1;
        resetState();
    }
    private void resetState() {
        windowId=-1;openTick=-1;lastClick=-1;pendingSince=-1;
        pendingSlot=-1;nextIndex=0;blocked=false;
    }
}
