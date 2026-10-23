package dev.ghostfusion.services;

import dev.ghostfusion.features.EspNearestBudget;

/**
 * Two independent nearest-256 partitions guarantee that visible-only consumers
 * cannot lose their nearest visible candidates to nearer occluded entities.
 * Combined output is distance+ID sorted, not grouped by visibility.
 * Crosshair-mode HUD may pin one additional current-tick player outside
 * the normal nearest limits, without displacing other consumers.
 * No raw candidate survives clear(). All access is owner-thread confined.
 */
public final class WorldSnapshotAdmission<T> {
    public interface Visitor<T> {
        void visit(T player,boolean visible);
    }
    private final Thread owner=Thread.currentThread();
    private final EspNearestBudget<T> visible=new EspNearestBudget<T>(256);
    private final EspNearestBudget<T> hidden=new EspNearestBudget<T>(256);
    // One optional reticle target is kept separately from both nearest-256
    // budgets. It does not displace ordinary HUD/Aim Assist candidates.
    private T pinned;
    private int pinnedId;
    private double pinnedDistanceSq;
    private boolean pinnedVisible;
    // Visitor code is owner-thread code but must not mutate/reenter the
    // live ordered candidate buffers while a stream is being published.
    private boolean emitting;
    private void assertWritable() {
        check();
        if(emitting)throw new IllegalStateException("snapshot admission is emitting");
    }
    private void check(){
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("snapshot admission owner thread");
    }
    public boolean offer(T player,int id,double squaredDistance,boolean isVisible) {
        assertWritable();
        if(player==null || !Double.isFinite(squaredDistance) || squaredDistance<0)
            return false;
        if(isVisible) {
            boolean admitted=visible.offer(player,id,squaredDistance);
            if(admitted)hidden.removeId(id);
            return admitted;
        }
        // Never publish two identical entity IDs: visible wins the tie.
        if(visible.containsId(id))return false;
        return hidden.offer(player,id,squaredDistance);
    }
    /** Pin at most one already-eligible, current-tick player for crosshair HUD.
     * Does not disturb the normal nearest-256 visibility partitions. */
    public boolean pin(T player,int id,double squaredDistance,boolean isVisible) {
        assertWritable();
        if(player==null || !Double.isFinite(squaredDistance) || squaredDistance<0)
            return false;
        pinned=player;
        pinnedId=id;
        pinnedDistanceSq=squaredDistance;
        pinnedVisible=isVisible;
        return true;
    }
    private boolean needsPinnedSlot() {
        return pinned!=null && !visible.containsId(pinnedId) && !hidden.containsId(pinnedId);
    }
    public int sizeWithPinned(){
        check();
        return visible.size()+hidden.size()+(needsPinnedSlot()?1:0);
    }
    public int size(){check();return visible.size()+hidden.size();}
    public int visibleSize(){check();return visible.size();}
    public int hiddenSize(){check();return hidden.size();}
    /** Streaming two-way merge; does not materialize a temporary merged array. */
    public void forEach(Visitor<T> sink) {
        emit(sink,false);
    }
    /**
     * Merge the optional pinned target into the stable distance/ID stream.
     * If it is already in a nearest partition, publish that entry once.
     * The maximum published candidate count is 256+256+1.
     */
    public void forEachWithPinned(Visitor<T> sink) {
        emit(sink,true);
    }
    private void emit(Visitor<T> sink,boolean includePinned) {
        check();
        if(sink==null)throw new IllegalArgumentException("snapshot visitor");
        if(emitting)throw new IllegalStateException("recursive snapshot emission");
        emitting=true;
        try {
        boolean extra=includePinned && needsPinnedSlot();
        int a=0,b=0;
        while(a<visible.size() || b<hidden.size() || extra){
            if(a==visible.size() && b==hidden.size()) {
                sink.visit(pinned,pinnedVisible);
                extra=false;
                continue;
            }
            boolean pickVisible;
            if(a==visible.size()) pickVisible=false;
            else if(b==hidden.size())pickVisible=true;
            else {
                int distance=Double.compare(visible.distanceAt(a),hidden.distanceAt(b));
                pickVisible=distance<0 || (distance==0 && visible.idAt(a)<hidden.idAt(b));
            }
            if(extra) {
                double nextDistance=pickVisible?visible.distanceAt(a):hidden.distanceAt(b);
                int nextId=pickVisible?visible.idAt(a):hidden.idAt(b);
                int ordering=Double.compare(pinnedDistanceSq,nextDistance);
                if(ordering<0 || (ordering==0 && pinnedId<nextId)) {
                    sink.visit(pinned,pinnedVisible);
                    extra=false;
                    continue;
                }
            }
            if(pickVisible)sink.visit(visible.get(a++),true);
            else sink.visit(hidden.get(b++),false);
        }
        } finally {
            emitting=false;
        }
    }
    public void clear(){
        assertWritable();
        visible.clear();hidden.clear();
        pinned=null; // Never retain a prior-world Minecraft entity.
        pinnedDistanceSq=0;
        pinnedId=0;
        pinnedVisible=false;
    }
}
