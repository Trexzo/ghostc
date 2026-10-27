package dev.ghostfusion.host;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Owner-thread bounded action-outcome ledger. One pending local action per
 * resource lane. Fixed enums only: no player, target, server, slot, packet,
 * chat, item name or timing/identity data is stored or exported.
 *
 * LOCAL_CHANGE: native source/target slot mutation observed, NOT server ACK.
 * LOCAL_COMPLETE: locally requested WTap lifecycle ended, NOT server effect.
 * DISPATCHED: vanilla key/sprint API invoked, NOT an accepted hit/packet.
 */
public final class ModuleActionTelemetry {
    public enum Lane { CHEST, SORTER, ARMOR, WTAP, CLICKER, BLOCKHIT }
    public enum Event {
        ATTEMPT, LOCAL_CHANGE, TIMEOUT, CANCELLED, REJECTED,
        DISPATCHED, LOCAL_COMPLETE
    }
    private final Thread owner=Thread.currentThread();
    private final EnumMap<Lane,EnumMap<Event,Long>> totals=
        new EnumMap<Lane,EnumMap<Event,Long>>(Lane.class);
    private final EnumMap<Lane,Boolean> pending=
        new EnumMap<Lane,Boolean>(Lane.class);
    private Lane lastLane;
    private Event lastEvent;
    public ModuleActionTelemetry() {
        for(Lane lane:Lane.values()) {
            EnumMap<Event,Long> group=new EnumMap<Event,Long>(Event.class);
            for(Event event:Event.values())group.put(event,0L);
            totals.put(lane,group);pending.put(lane,false);
        }
    }
    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("action telemetry owner thread");
    }
    /**
     * A pending action accepts exactly one terminal event. Attempted native
     * operations and key/sprint pulses have different meanings.
     */
    public boolean record(Lane lane,Event event) {
        check();
        if(lane==null||event==null)
            throw new IllegalArgumentException("action lane/event");
        boolean active=pending.get(lane);
        switch(event) {
            case ATTEMPT:
                if(active)return false;pending.put(lane,true);break;
            case LOCAL_CHANGE:case LOCAL_COMPLETE:
            case TIMEOUT:case CANCELLED:
                if(!active)return false;
                pending.put(lane,false);break;
            case DISPATCHED:
                // A sprint reset can dispatch twice while pending. Combat
                // clicks are independent pulses, never a pending transaction.
                break;
            case REJECTED:
                // Rejection of a candidate native action is meaningful even
                // before an attempt has started.
                if(active)pending.put(lane,false);
                break;
            default:throw new IllegalArgumentException("action event");
        }
        long previous=totals.get(lane).get(event);
        totals.get(lane).put(event,previous==Long.MAX_VALUE?
            Long.MAX_VALUE:previous+1L);
        lastLane=lane;lastEvent=event;
        return true;
    }
    public boolean pending(Lane lane) {
        check();
        if(lane==null)throw new IllegalArgumentException("lane");
        return pending.get(lane);
    }
    public long count(Lane lane,Event event) {
        check();
        if(lane==null||event==null)throw new IllegalArgumentException("lane/event");
        return totals.get(lane).get(event);
    }
    public Map<Event,Long> totals(Lane lane) {
        check();
        if(lane==null)throw new IllegalArgumentException("lane");
        return Collections.unmodifiableMap(
            new EnumMap<Event,Long>(totals.get(lane)));
    }
    public String latest() {
        check();
        return lastLane==null?"Action: no observed native activity":
            "Action: "+lastLane.name()+" "+lastEvent.name();
    }
    /** On focus/world/Panic transitions, make stale events impossible.
     * Counts remain valid opt-in aggregate diagnostics for this session. */
    public void cancelAll() {
        check();
        for(Lane lane:Lane.values())if(pending.get(lane))
            record(lane,Event.CANCELLED);
    }
}
