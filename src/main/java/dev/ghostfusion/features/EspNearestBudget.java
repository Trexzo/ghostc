package dev.ghostfusion.features;

/**
 * Fixed-capacity, deterministic nearest-entity priority buffer.
 * ESP uses capacity 80; the shared local world snapshot uses 256.
 * Entries are ordered by distanceSq, then entity ID. A separate reusable
 * array is NOT created on every frame; clear() releases all entity references.
 *
 * The class has no Minecraft dependencies so it is testable with JDK8 alone.
 */
public final class EspNearestBudget<T> {
    private final Thread owner=Thread.currentThread();
    private final Object[] players;
    private final int[] ids;
    private final double[] distances;
    private int size;
    private int selectionLimit;
    public EspNearestBudget(int capacity) {
        if(capacity<1 || capacity>256)throw new IllegalArgumentException("nearest entity budget capacity");
        players=new Object[capacity];
        ids=new int[capacity];
        distances=new double[capacity];
        selectionLimit=capacity;
    }
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("ESP budget requires render owner thread");
    }
    /**
     * Reconfigure the nearest-N policy without allocating another buffer.
     * Shrinking discards farthest references immediately. To expand and still
     * consider all candidates for the current frame, clear() and rescan.
     * World snapshots retain their original 256 capacity unless configured.
     */
    public void setLimit(int desired) {
        check();
        if(desired<1 || desired>players.length)
            throw new IllegalArgumentException("nearest budget active limit");
        if(desired<size) {
            for(int i=desired;i<size;i++) players[i]=null;
            size=desired;
        }
        selectionLimit=desired;
    }
    public int limit(){check();return selectionLimit;}
    /**
     * Non-mutating admission hint for expensive renderer checks.
     * False means offer(id,distanceSq) could not change the selected
     * nearest-N set, even if the candidate passed visibility/geometry.
     * True is conservative: offer may still reject null objects.
     * O(capacity) duplicate detection, no allocations, owner-thread only.
     */
    public boolean couldEnter(int id,double distanceSq) {
        check();
        if(!Double.isFinite(distanceSq)||distanceSq<0)return false;
        if(size==selectionLimit) {
            int last=size-1;
            if(distanceSq>distances[last] ||
                (distanceSq==distances[last] && id>=ids[last]))
                return false;
        }
        for(int i=0;i<size;i++) {
            if(ids[i]==id)return distanceSq<distances[i];
        }
        return true;
    }
    public boolean offer(T player,int id,double distanceSq) {
        check();
        if(player==null || !Double.isFinite(distanceSq) || distanceSq<0)
            return false;
        for(int i=0;i<size;i++) {
            if(ids[i]!=id)continue;
            // Prefer the nearer valid duplicate regardless of source order.
            if(distanceSq>=distances[i])return false;
            removeAt(i);
            break;
        }
        int at=0;
        while(at<size && (distances[at]<distanceSq ||
            (distances[at]==distanceSq && ids[at]<id)))at++;
        if(at==selectionLimit)return false;
        int next=Math.min(size+1,selectionLimit);
        for(int j=next-1;j>at;j--) {
            players[j]=players[j-1];
            distances[j]=distances[j-1];
            ids[j]=ids[j-1];
        }
        players[at]=player;
        ids[at]=id;
        distances[at]=distanceSq;
        size=next;
        return true;
    }
    private void removeAt(int index) {
        for(int j=index;j<size-1;j++) {
            players[j]=players[j+1]; ids[j]=ids[j+1];
            distances[j]=distances[j+1];
        }
        players[--size]=null;
    }
    public boolean containsId(int id) {
        check();
        for(int i=0;i<size;i++)if(ids[i]==id)return true;
        return false;
    }
    public boolean removeId(int id) {
        check();
        for(int i=0;i<size;i++) {
            if(ids[i]==id) {removeAt(i);return true;}
        }
        return false;
    }
    public int size(){check();return size;}
    @SuppressWarnings("unchecked")
    public T get(int index){
        check();
        if(index<0||index>=size)throw new IndexOutOfBoundsException("ESP candidate");
        return (T)players[index];
    }
    public int idAt(int index) {
        check();
        if(index<0||index>=size)throw new IndexOutOfBoundsException("ESP candidate");
        return ids[index];
    }
    public double distanceAt(int index) {
        check();
        if(index<0||index>=size)throw new IndexOutOfBoundsException("ESP candidate");
        return distances[index];
    }
    public void clear() {
        check();
        for(int i=0;i<size;i++)players[i]=null;
        size=0;
    }
}
