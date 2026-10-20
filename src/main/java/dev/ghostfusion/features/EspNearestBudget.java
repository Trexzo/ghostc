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
    public EspNearestBudget(int capacity) {
        if(capacity<1 || capacity>256)throw new IllegalArgumentException("nearest entity budget capacity");
        players=new Object[capacity];
        ids=new int[capacity];
        distances=new double[capacity];
    }
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("ESP budget requires render owner thread");
    }
    public boolean offer(T player,int id,double distanceSq) {
        check();
        if(player==null || !Double.isFinite(distanceSq) || distanceSq<0)
            return false;
        for(int i=0;i<size;i++)if(ids[i]==id)return false;
        int at=0;
        while(at<size && (distances[at]<distanceSq ||
            (distances[at]==distanceSq && ids[at]<id)))at++;
        if(at==players.length)return false;
        int next=Math.min(size+1,players.length);
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
