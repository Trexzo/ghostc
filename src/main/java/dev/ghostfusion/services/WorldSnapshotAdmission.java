package dev.ghostfusion.services;

import dev.ghostfusion.features.EspNearestBudget;

/**
 * Two independent nearest-256 partitions guarantee that visible-only consumers
 * cannot lose their nearest visible candidates to nearer occluded entities.
 * Combined output is distance+ID sorted, not grouped by visibility.
 * No raw candidate survives clear(). All access is owner-thread confined.
 */
public final class WorldSnapshotAdmission<T> {
    public interface Visitor<T> {
        void visit(T player,boolean visible);
    }
    private final Thread owner=Thread.currentThread();
    private final EspNearestBudget<T> visible=new EspNearestBudget<T>(256);
    private final EspNearestBudget<T> hidden=new EspNearestBudget<T>(256);
    private void check(){
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("snapshot admission owner thread");
    }
    public boolean offer(T player,int id,double squaredDistance,boolean isVisible) {
        check();
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
    public int size(){check();return visible.size()+hidden.size();}
    public int visibleSize(){check();return visible.size();}
    public int hiddenSize(){check();return hidden.size();}
    /** Streaming two-way merge; does not materialize a temporary merged array. */
    public void forEach(Visitor<T> sink) {
        check();
        if(sink==null)throw new IllegalArgumentException("snapshot visitor");
        int a=0,b=0;
        while(a<visible.size() || b<hidden.size()){
            boolean pickVisible;
            if(a==visible.size()) pickVisible=false;
            else if(b==hidden.size())pickVisible=true;
            else {
                int distance=Double.compare(visible.distanceAt(a),hidden.distanceAt(b));
                pickVisible=distance<0 || (distance==0 && visible.idAt(a)<hidden.idAt(b));
            }
            if(pickVisible)sink.visit(visible.get(a++),true);
            else sink.visit(hidden.get(b++),false);
        }
    }
    public void clear(){
        check();
        visible.clear();hidden.clear();
    }
}
