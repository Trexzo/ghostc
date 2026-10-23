package dev.ghostfusion.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport-independent packet intentions; only a future adapter can supply a Transport.
 * Every accepted submission receives exactly one terminal completion, including cancellation.
 * Not connected to Netty or Minecraft networking in R0.4.
 */
public final class PacketCoordinator {
    public enum Direction { INBOUND, OUTBOUND }
    public enum Result { SENT, CANCELLED, STALE_SESSION, TRANSPORT_FAILED, REJECTED, SHUTDOWN }
    public interface Completion { void complete(Result result); }
    public interface Transport { void deliver(Direction direction, byte[] payload) throws Exception; }
    public static final class Metrics {
        public final int queued; public final long rejected, delivered, cancelled, failed, callbackFailures;
        Metrics(int queued,long rejected,long delivered,long cancelled,long failed,long callbackFailures) {
            this.queued=queued; this.rejected=rejected; this.delivered=delivered;
            this.cancelled=cancelled; this.failed=failed; this.callbackFailures=callbackFailures;
        }
    }
    private static final class Pending {
        final long id, generation; final String owner;
        final Direction direction; final byte[] payload; final Completion completion;
        Pending(long id,long generation,String owner,Direction direction,byte[] payload,Completion completion) {
            this.id=id; this.generation=generation; this.owner=owner;
            this.direction=direction; this.payload=payload; this.completion=completion;
        }
    }
    private final Thread thread=Thread.currentThread();
    private final int capacity;
    private final LinkedHashMap<Long, Pending> queue=new LinkedHashMap<Long, Pending>();
    private long idSequence, rejected, delivered, cancelled, failed, callbackFailures;
    private boolean stopped, resetting, flushing;
    public PacketCoordinator(int capacity) {
        if(capacity<1 || capacity>4096) throw new IllegalArgumentException("capacity");
        this.capacity=capacity;
    }
    private void check() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("packet owner thread");
        if(stopped) throw new IllegalStateException("packet stopped");
        if(resetting) throw new IllegalStateException("packet session reset in progress");
    }
    private void callback(Completion completion,Result result) {
        try { completion.complete(result); }
        catch(Throwable ignored) { callbackFailures++; }
    }
    /** Returns a unique positive ID or -1 on capacity rejection; never stores caller's byte array. */
    public long submit(String owner,Direction direction,byte[] payload,long generation,Completion callback) {
        check(); ServiceValidation.owner(owner);
        if(direction==null || payload==null || payload.length==0 || payload.length>32768 ||
           generation<0 || callback==null) throw new IllegalArgumentException("packet");
        if(queue.size()>=capacity) {
            rejected++; callback(callback,Result.REJECTED); return -1;
        }
        // Positive, never-reused cancellation tokens; -1 is reserved for
        // ordinary capacity rejection and must never be a real packet ID.
        if(idSequence==Long.MAX_VALUE)
            throw new IllegalStateException("packet token overflow");
        long id=++idSequence;
        queue.put(id,new Pending(id,generation,owner,direction,payload.clone(),callback));
        return id;
    }
    public boolean cancel(long id) {
        check();
        Pending p=queue.remove(id);
        if(p==null) return false;
        cancelled++; callback(p.completion,Result.CANCELLED); return true;
    }
    /** Detach the entire original owner batch before invoking arbitrary
     * completion code. A callback may reset, shut down or submit new work;
     * none of those actions can interrupt settlement of this batch or
     * accidentally cancel the newly submitted work. */
    public int cancelOwner(String owner) {
        check(); ServiceValidation.owner(owner);
        ArrayList<Pending> detached=new ArrayList<Pending>();
        java.util.Iterator<Pending> iterator=queue.values().iterator();
        while(iterator.hasNext()) {
            Pending p=iterator.next();
            if(p.owner.equals(owner)) {
                detached.add(p);
                iterator.remove();
            }
        }
        cancelled+=detached.size();
        for(Pending p:detached)callback(p.completion,Result.CANCELLED);
        return detached.size();
    }
    public int flush(Transport transport,long generation,int budget) {
        check();
        if(transport==null || generation<0 || budget<1 || budget>capacity)
            throw new IllegalArgumentException("flush");
        // A Transport or Completion callback is user code and can synchronously
        // call flush again. Recursion would bypass the caller's budget and can
        // exhaust the stack. Other operations (submit/cancel/reset) remain legal.
        if(flushing) throw new IllegalStateException("packet flush already in progress");
        flushing=true;
        try {
            int n=0;
            for(Pending p:new ArrayList<Pending>(queue.values())) {
                if(n>=budget) break;
                if(queue.remove(p.id)==null) continue;
                n++;
                if(p.generation!=generation) {
                    cancelled++; callback(p.completion,Result.STALE_SESSION);
                    continue;
                }
                // The request is already detached. A transport is external
                // code and may throw an Error, not just an Exception: settle
                // the accepted request exactly once before propagating it.
                try {
                    transport.deliver(p.direction,p.payload.clone());
                } catch(Throwable failure) {
                    failed++; callback(p.completion,Result.TRANSPORT_FAILED);
                    if(failure instanceof Error)throw (Error)failure;
                    continue;
                }
                delivered++; callback(p.completion,Result.SENT);
            }
            return n;
        } finally {
            flushing=false;
        }
    }
    public void reset() {
        check();
        // Detach every old-generation request BEFORE invoking any untrusted
        // completion. Re-entrant submissions from a stale-session callback
        // must not create work which survives into the next world.
        resetting=true;
        try {
            ArrayList<Pending> stale=new ArrayList<Pending>(queue.values());
            queue.clear();
            for(Pending p:stale) {
                cancelled++;
                callback(p.completion,Result.STALE_SESSION);
            }
        } finally {
            resetting=false;
        }
    }
    public Metrics metrics() {
        check(); return new Metrics(queue.size(),rejected,delivered,cancelled,failed,callbackFailures);
    }
    public void shutdown() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("packet owner thread");
        if(stopped) return;
        if(resetting) throw new IllegalStateException("packet session reset in progress");
        // Mark stopped before callbacks: they are user code and can try to
        // submit new work. No accepted request may survive shutdown.
        stopped=true;
        ArrayList<Pending> remaining=new ArrayList<Pending>(queue.values());
        queue.clear();
        for(Pending p:remaining) {
            cancelled++;
            callback(p.completion,Result.SHUTDOWN);
        }
    }
}
