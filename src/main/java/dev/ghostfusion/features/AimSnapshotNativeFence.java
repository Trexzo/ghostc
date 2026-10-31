package dev.ghostfusion.features;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owner-thread, same-client-END-tick witness for an immutable WorldSnapshot.
 * The snapshot remains a small, reference-free copy of primitive positions.
 *
 * Only entities that actually entered the published snapshot may be witnessed.
 * A camera candidate must be the EXACT object that produced that snapshot
 * row, still in the same world and under the original local player/session.
 * A new object with the same entity ID, UUID and coordinates cannot inherit
 * the earlier snapshot's camera authority.
 *
 * Native references are transient: clear immediately after the combat phase,
 * on any world transition, or at the start of the next END tick. No packets,
 * camera/input writes, identity export, or automatic target acquisition.
 */
public final class AimSnapshotNativeFence {
    public static final int MAX_ROWS=514;
    private static final class Row {
        final UUID uuid;
        final Object entity;
        Row(UUID uuid,Object entity) {this.uuid=uuid;this.entity=entity;}
    }
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Row> rows=new HashMap<Integer,Row>();
    private long generation=-1,tick=-1;
    private Object sourceWorld,sourcePlayer;
    private boolean active;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("Aim snapshot witness owner thread");
    }
    public void begin(long epoch,long frame,Object world,Object player) {
        check();
        clear();
        if(epoch<0||frame<0||world==null||player==null)
            throw new IllegalArgumentException("missing native snapshot session");
        generation=epoch;tick=frame;sourceWorld=world;sourcePlayer=player;
        active=true;
    }
    /** Called only after the corresponding WorldSnapshot.Entity is added. */
    public void record(int id,UUID uuid,Object exactEntity) {
        check();
        if(!active||id<0||uuid==null||exactEntity==null||
           exactEntity==sourcePlayer||rows.size()>=MAX_ROWS||
           rows.containsKey(id)) {
            // Any inconsistent snapshot construction invalidates all
            // references rather than keeping a partial trusted witness.
            clear();
            throw new IllegalArgumentException("invalid native snapshot row");
        }
        rows.put(id,new Row(uuid,exactEntity));
    }
    /** Read-only source-to-actual-camera boundary; never a new grant. */
    public boolean matches(long epoch,long frame,int id,UUID uuid,
                           Object entity,Object world,Object player,
                           Object registeredEntity) {
        check();
        if(!active||epoch!=generation||frame!=tick||
           world==null||world!=sourceWorld||
           player==null||player!=sourcePlayer||
           entity==null||registeredEntity!=entity||uuid==null)
            return false;
        Row row=rows.get(id);
        return row!=null&&row.entity==entity&&row.uuid.equals(uuid);
    }
    public int size() {check();return rows.size();}
    public void clear() {
        check();
        rows.clear();sourceWorld=sourcePlayer=null;
        generation=tick=-1;active=false;
    }
}
