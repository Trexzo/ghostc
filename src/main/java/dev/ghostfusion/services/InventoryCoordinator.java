package dev.ghostfusion.services;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Single-flight inventory transaction intents. No Minecraft inventory mutation here.
 * The host must explicitly acknowledge the active token, not infer success from time.
 */
public final class InventoryCoordinator {
    public enum State { QUEUED, ACTIVE, CONFIRMED, REJECTED, EXPIRED, CANCELLED }
    public static final class Transaction {
        public final long id, generation, start, expiry; public final String owner;
        public final int slot, priority; public final State state;
        private Transaction(long id,long generation,long start,long expiry,String owner,int slot,int priority,State state) {
            this.id=id; this.generation=generation; this.start=start; this.expiry=expiry;
            this.owner=owner; this.slot=slot; this.priority=priority; this.state=state;
        }
        Transaction state(State next) { return new Transaction(id,generation,start,expiry,owner,slot,priority,next); }
    }
    private final Thread thread=Thread.currentThread();
    private final LinkedHashMap<Long,Transaction> pending=new LinkedHashMap<Long,Transaction>();
    private final int capacity;
    private Transaction active;
    private long nextId, rejected;
    private boolean stopped;
    public InventoryCoordinator(int capacity) {
        if(capacity<1 || capacity>256) throw new IllegalArgumentException("capacity");
        this.capacity=capacity;
    }
    private void check() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("inventory owner thread");
        if(stopped) throw new IllegalStateException("inventory stopped");
    }
    public long submit(String owner,int slot,int priority,long tick,int ttl,long generation) {
        check(); ServiceValidation.request(owner,tick,ttl,generation);
        if(slot<0 || slot>44) throw new IllegalArgumentException("slot");
        if(pending.size()+(active==null?0:1)>=capacity) { rejected++; return -1; }
        // Never wrap a token into a negative/sentinel or reused ID.
        if(nextId==Long.MAX_VALUE)throw new IllegalStateException("inventory token overflow");
        long id=++nextId;
        pending.put(id,new Transaction(id,generation,tick,tick+ttl,owner,slot,priority,State.QUEUED));
        return id;
    }
    /** Called once per owner tick. At most one ACTIVE transaction at a time. */
    public Transaction poll(long tick,long generation) {
        check(); if(tick<0 || generation<0) throw new IllegalArgumentException("time");
        if(active!=null && (active.generation!=generation || tick>=active.expiry)) active=null;
        // Prune old work and select an eligible candidate in one pass.
        // Do not copy the entire pending collection on each owner tick.
        if(pending.isEmpty())return active;
        Transaction best=null;
        Iterator<Transaction> iterator=pending.values().iterator();
        while(iterator.hasNext()) {
            Transaction t=iterator.next();
            if(t.generation!=generation || tick>=t.expiry) {
                iterator.remove();
                continue;
            }
            // Requests scheduled in a later tick must remain QUEUED.
            if(active!=null || tick<t.start)continue;
            if(best==null || t.priority>best.priority ||
               (t.priority==best.priority && t.id<best.id))best=t;
        }
        if(active!=null)return active;
        if(best==null)return null;
        pending.remove(best.id);
        active=best.state(State.ACTIVE);
        return active;
    }
    public Transaction acknowledge(long id,boolean accepted,long generation) {
        check(); if(active==null || active.id!=id || active.generation!=generation) return null;
        Transaction done=active.state(accepted?State.CONFIRMED:State.REJECTED);
        active=null; return done;
    }
    public int cancelOwner(String owner) {
        check(); ServiceValidation.owner(owner); int n=0;
        Iterator<Transaction> iterator=pending.values().iterator();
        while(iterator.hasNext()) {
            if(iterator.next().owner.equals(owner)) {
                iterator.remove();
                n++;
            }
        }
        if(active!=null && active.owner.equals(owner)) { active=null; n++; }
        return n;
    }
    public int queued() { check(); return pending.size(); }
    public boolean active() { check(); return active!=null; }
    public long rejected() { check(); return rejected; }
    public void reset() { check(); pending.clear(); active=null; }
    public void shutdown() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("inventory owner thread");
        if(stopped) return; reset(); stopped=true;
    }
}
