package dev.ghostfusion.ui;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Appearance-only Slate switch animation. Stable module/Setting object
 * identities avoid allocating string keys every rendered frame. The caller
 * supplies a monotonic frame timestamp so motion does not depend on FPS.
 * This class never changes or persists a module or setting value.
 */
public final class GuiToggleMotion {
    public static final long DURATION_NANOS=160_000_000L;
    private static final class Track {
        double from, to;
        long started;
        Track(double target,long now){from=to=target;started=now;}
    }
    private final Map<Object,Track> tracks=new IdentityHashMap<Object,Track>();

    public void clear() {tracks.clear();}

    public double value(Object identity,boolean enabled,long now) {
        if(identity==null)throw new IllegalArgumentException("switch identity");
        double target=enabled?1.0:0.0;
        Track track=tracks.get(identity);
        if(track==null){
            track=new Track(target,now);
            tracks.put(identity,track);
            return target; // Initial render shows actual state, not a false toggle.
        }
        double current=position(track,now);
        if(track.to!=target) {
            track.from=current;
            track.to=target;
            track.started=now;
            return current; // Mid-flight reversals never teleport the knob.
        }
        return current;
    }

    private static double position(Track track,long now) {
        long elapsed=now-track.started;
        // A clock discontinuity must never produce an invalid fraction.
        if(elapsed<0L)elapsed=0L;
        double t=Math.min(1.0,(double)elapsed/DURATION_NANOS);
        double eased=1.0-(1.0-t)*(1.0-t)*(1.0-t);
        return track.from+(track.to-track.from)*eased;
    }

    /** Blend opaque/off and opaque/on switch colors without GL side effects. */
    public static int blendArgb(int off,int on,double t) {
        if(!Double.isFinite(t))throw new IllegalArgumentException("switch progress");
        t=Math.max(0.0,Math.min(1.0,t));
        int result=0;
        for(int shift=0;shift<=24;shift+=8) {
            int a=(off>>>shift)&255,b=(on>>>shift)&255;
            result|=((int)Math.round(a+(b-a)*t))<<shift;
        }
        return result;
    }
}
