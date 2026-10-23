package dev.ghostfusion.services;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Frame-bounded input intents: at most one resolved owner per exclusive action type/tick. */
public final class ActionCoordinator {
    public enum Type { ATTACK, USE, SWING, HOTBAR_SELECT }
    public enum Phase { PRE_INPUT, SIMULATE, OUTPUT }
    public static final class Decision {
        public final String owner; public final Type type; public final Phase phase;
        public final int argument; public final long tick, generation;
        Decision(String owner,Type type,Phase phase,int argument,long tick,long generation) {
            this.owner=owner; this.type=type; this.phase=phase;
            this.argument=argument; this.tick=tick; this.generation=generation;
        }
    }
    private static final class Request {
        final String owner; final Type type; final Phase phase; final int argument, priority;
        final long start, expiry, generation, ordinal;
        Request(String owner,Type type,Phase phase,int argument,int priority,
                long start,long expiry,long generation,long ordinal) {
            this.start=start; this.owner=owner; this.type=type; this.phase=phase; this.argument=argument;
            this.priority=priority; this.expiry=expiry; this.generation=generation; this.ordinal=ordinal;
        }
    }
    private final Thread thread=Thread.currentThread();
    // Request identity includes phase: one feature can propose the same
    // action for distinct processing phases without overwriting either.
    // The consumedTicks fence below still allows at most one execution
    // per action TYPE per session tick, regardless of request phase.
    private final Map<String,Request> requests=new LinkedHashMap<String,Request>();
    private static String key(String owner,Type type,Phase phase) {
        return owner+"#"+type.name()+"#"+phase.name();
    }
    private final EnumMap<Type,Long> consumedTicks=new EnumMap<Type,Long>(Type.class);
    private final EnumMap<Type,Long> consumedGenerations=new EnumMap<Type,Long>(Type.class);
    private long ordinal;
    private boolean stopped;
    private void check() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("action owner thread");
        if(stopped) throw new IllegalStateException("action stopped");
    }
    public void request(String owner,Type type,Phase phase,int argument,
                        int priority,long tick,int ttl,long generation) {
        check(); ServiceValidation.request(owner,tick,ttl,generation);
        if(type==null || phase==null || (type==Type.HOTBAR_SELECT && (argument<0 || argument>8)))
            throw new IllegalArgumentException("action");
        String key=key(owner,type,phase);
        if(requests.size()>=256 && !requests.containsKey(key))
            throw new IllegalStateException("action capacity exceeded");
        // Equal-priority FIFO is ordered by a positive, monotonic ordinal.
        // Never wrap to Long.MIN_VALUE and silently reverse that ordering.
        if(ordinal==Long.MAX_VALUE)
            throw new IllegalStateException("action ordinal overflow");
        requests.put(key, new Request(owner,type,phase,argument,
            priority,tick,tick+ttl,generation,++ordinal));
    }
    public Decision consume(Type type,Phase phase,long tick,long generation) {
        check();
        if(type==null || phase==null || tick<0 || generation<0) throw new IllegalArgumentException("consume");
        Long consumed=consumedTicks.get(type), epoch=consumedGenerations.get(type);
        if(consumed!=null && epoch!=null && consumed==tick && epoch==generation) return null;
        Request best=null;
        for(Request r:requests.values()) {
            if(r.type!=type || r.phase!=phase || r.generation!=generation || tick<r.start || tick>=r.expiry) continue;
            if(best==null || r.priority>best.priority ||
                (r.priority==best.priority && r.ordinal<best.ordinal)) best=r;
        }
        if(best==null) return null;
        consumedTicks.put(type,tick); consumedGenerations.put(type,generation);
        requests.remove(key(best.owner,best.type,best.phase));
        return new Decision(best.owner,best.type,best.phase,best.argument,tick,generation);
    }
    public void cancelOwner(String owner) {
        check(); ServiceValidation.owner(owner);
        for(Type type:Type.values())
            for(Phase phase:Phase.values())
                requests.remove(key(owner,type,phase));
    }
    /** Remove stale or expired intents each owner tick; no unbounded backlog. */
    public int prune(long tick, long generation) {
        check();
        if(tick<0 || generation<0) throw new IllegalArgumentException("prune time");
        int removed=0;
        java.util.Iterator<Request> iterator=requests.values().iterator();
        while(iterator.hasNext()) {
            Request request=iterator.next();
            if(request.generation!=generation || tick>=request.expiry) {
                iterator.remove(); removed++;
            }
        }
        return removed;
    }
    public int pending() { check(); return requests.size(); }
    public void reset() {
        check(); requests.clear(); consumedTicks.clear(); consumedGenerations.clear();
        // All competing requests are gone. A new session can safely reuse
        // small internal ordering ordinals without colliding with survivors.
        ordinal=0;
    }
    public void shutdown() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("action owner thread");
        if(stopped) return;
        reset(); stopped=true;
    }
}
