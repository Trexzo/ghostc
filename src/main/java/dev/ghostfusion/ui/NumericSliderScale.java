package dev.ghostfusion.ui;

/**
 * Finite numeric settings mapped to/from GUI pixels without overflow.
 * Handles constant ranges and finite endpoints whose subtraction would
 * overflow Double.MAX_VALUE. Does not change any setting's min/max contract.
 */
public final class NumericSliderScale {
    private NumericSliderScale() {}

    private static void bounds(double min,double max) {
        if(!Double.isFinite(min) || !Double.isFinite(max) || min>max)
            throw new IllegalArgumentException("numeric slider bounds");
    }

    /** Invariant: the returned value is finite and within [min,max]. */
    public static double value(double min,double max,double fraction) {
        bounds(min,max);
        if(!Double.isFinite(fraction))
            throw new IllegalArgumentException("numeric slider fraction");
        double t=Math.max(0.0,Math.min(1.0,fraction));
        if(t==0.0 || min==max)return min;
        if(t==1.0)return max;
        double span=max-min;
        double result;
        if(Double.isFinite(span)) {
            result=min+span*t;
        } else {
            // Convex combination avoids subtraction overflow across signs.
            // Each term is bounded by its finite endpoint's magnitude.
            result=(1.0-t)*min+t*max;
        }
        if(!Double.isFinite(result))
            throw new IllegalStateException("numeric slider overflow");
        return Math.max(min,Math.min(max,result));
    }

    /** Fraction used by the drawn fill bar; constant ranges show 0%. */
    public static double fraction(double current,double min,double max) {
        bounds(min,max);
        if(!Double.isFinite(current))
            throw new IllegalArgumentException("numeric slider current value");
        if(min==max || current<=min)return 0.0;
        if(current>=max)return 1.0;
        double span=max-min;
        double t;
        if(Double.isFinite(span)) {
            t=(current-min)/span;
        } else {
            // Divide first to keep differences representable at +/-MAX.
            double scale=Math.max(Math.abs(min),Math.abs(max));
            t=((current/scale)-(min/scale))/((max/scale)-(min/scale));
        }
        if(!Double.isFinite(t))
            throw new IllegalStateException("numeric slider fill overflow");
        return Math.max(0.0,Math.min(1.0,t));
    }
}
