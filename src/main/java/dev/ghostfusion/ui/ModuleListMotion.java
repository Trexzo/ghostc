package dev.ghostfusion.ui;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Bounded animation of live, enabled module labels keyed by their stable IDs.
 * No exit ghost: a disabled/faulted module is removed during endFrame().
 * Frame-time-based ease-out runs without changing server/gameplay state.
 */
public final class ModuleListMotion {
    public static final long DURATION_NANOS=180_000_000L;
    private static final class State {
        final long first;
        long seen;
        State(long now,long generation){first=now;seen=generation;}
    }
    private final Map<String,State> live=new HashMap<String,State>();
    private long generation;
    private long frameTime;
    public void beginFrame(long now) {
        if(generation==Long.MAX_VALUE){live.clear();generation=0;}
        generation++;
        frameTime=now;
    }
    public double observe(String stableId) {
        if(stableId==null||stableId.isEmpty())
            throw new IllegalArgumentException("Module List identity");
        State state=live.get(stableId);
        if(state==null) {
            state=new State(frameTime,generation);
            live.put(stableId,state);
        }
        state.seen=generation;
        long elapsed=frameTime>=state.first?frameTime-state.first:0L;
        if(elapsed<0)elapsed=Long.MAX_VALUE;
        double time=Math.min(1.0,(double)elapsed/DURATION_NANOS);
        double inverse=1.0-time;
        return 1.0-inverse*inverse*inverse;
    }
    public void endFrame(){
        Iterator<Map.Entry<String,State>> it=live.entrySet().iterator();
        while(it.hasNext())if(it.next().getValue().seen!=generation)it.remove();
    }
    /** Forget previous-world/disabled-HUD labels before the next appearance. */
    public void reset() {
        live.clear();
        generation=0L;
        frameTime=0L;
    }
    public int activeCount(){return live.size();}
    public static int fade(int argb,double opacity) {
        if(!Double.isFinite(opacity)||opacity<0||opacity>1)
            throw new IllegalArgumentException("Fade opacity");
        int a=(int)Math.round((argb>>>24)*opacity);
        return (a<<24)|(argb&0xFFFFFF);
    }
}
