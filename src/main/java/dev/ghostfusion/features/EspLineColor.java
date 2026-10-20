package dev.ghostfusion.features;

/**
 * Typed first-party ESP tint. Shared by Box, Tracer and Skeleton wire lines;
 * optional health gauge retains its semantic red/green health gradient.
 * Immutable validated normalized channels for direct Forge GL binding.
 */
public final class EspLineColor {
    public final float red,green,blue,opacity;
    private EspLineColor(float r,float g,float b,float a){
        red=r;green=g;blue=b;opacity=a;
    }
    private static float channel(double value,boolean alpha){
        if(!Double.isFinite(value)||value<(alpha?0.1:0.0)||value>1.0)
            throw new IllegalArgumentException("Invalid ESP line color channel");
        return (float)value;
    }
    public static EspLineColor of(double red,double green,double blue,double opacity){
        return new EspLineColor(channel(red,false),channel(green,false),
                                channel(blue,false),channel(opacity,true));
    }
}
