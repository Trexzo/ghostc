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
        final long expiry, generation, ordinal;
        Request(String owner,Type type,Phase phase,int argument,int priority,
                long expiry,long generation,long ordinal) {
            this.owner=owner; this.type=type; this.phase=phase; this.argument=argument;
            this.priority=priority; this.expiry=expiry; this.generation=generation; this.ordinal=ordinal;
        }
    }
    private final Thread thread=Thread.currentThread();
    private final Map<String,Request> requests=new LinkedHashMap<String,Request>();
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
        if(requests.size()>=256 && !requests.containsKey(owner+"#"+type))
            throw new IllegalStateException("action capacity exceeded");
        requests.put(owner+"#"+type, new Request(owner,type,phase,argument,
            priority,tick+ttl,generation,++ordinal));
    }
    public Decision consume(Type type,Phase phase,long tick,long generation) {
        check();
        if(type==null || phase==null || tick<0 || generation<0) throw new IllegalArgumentException("consume");
        Long consumed=consumedTicks.get(type), epoch=consumedGenerations.get(type);
        if(consumed!=null && epoch!=null && consumed==tick && epoch==generation) return null;
        Request best=null;
        for(Request r:requests.values()) {
            if(r.type!=type || r.phase!=phase || r.generation!=generation || tick>=r.expiry) continue;
            if(best==null || r.priority>best.priority ||
                (r.priority==best.priority && r.ordinal<best.ordinal)) best=r;
        }
        if(best==null) return null;
        consumedTicks.put(type,tick); consumedGenerations.put(type,generation);
        requests.remove(best.owner+"#"+type);
        return new Decision(best.owner,best.type,best.phase,best.argument,tick,generation);
    }
    public void cancelOwner(String owner) {
        check(); ServiceValidation.owner(owner);
        for(Type type:Type.values()) requests.remove(owner+"#"+type);
    }
    public int pending() { check(); return requests.size(); }
    public void reset() {
        check(); requests.clear(); consumedTicks.clear(); consumedGenerations.clear();
    }
    public void shutdown() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("action owner thread");
        if(stopped) return;
        reset(); stopped=true;
    }
}
