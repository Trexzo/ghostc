package dev.ghostfusion.features;

/**
 * Optional world-space player ESP health bar, camera-facing in the XZ plane.
 * This is a first-party schematic overlay, not a texture or a model-rig hook.
 * Pure geometry; no Minecraft types or per-player allocation.
 */
public final class EspHealthBarGeometry {
    public interface Sink {
        void segment(boolean filled,double fraction,
                     double x1,double y1,double z1,double x2,double y2,double z2);
    }
    private EspHealthBarGeometry() { }
    public static boolean valid(double x,double y,double z,double height,double width,
                                double viewerX,double viewerZ,
                                double current,double maximum) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
           !Double.isFinite(height)||!Double.isFinite(width)||
           !Double.isFinite(viewerX)||!Double.isFinite(viewerZ)||
           !Double.isFinite(current)||!Double.isFinite(maximum)||
           height<0.5||height>3.0||width<0.1||width>3.0||
           current<0||maximum<=0)return false;
        double span=Math.hypot(viewerX-x,viewerZ-z);
        return Double.isFinite(span)&&span>0.000001;
    }
    /**
     * Background covers the full height; health fill grows UP from the bottom.
     * Invalid health/position suppresses only this optional decoration.
     * A player with health above nominal max is clamped to a full bar.
     */
    public static boolean emit(double x,double y,double z,double height,double width,
                               double viewerX,double viewerZ,
                               double current,double maximum,Sink sink) {
        if(sink==null)throw new IllegalArgumentException("health line sink");
        if(!valid(x,y,z,height,width,viewerX,viewerZ,current,maximum))return false;
        double dx=viewerX-x,dz=viewerZ-z;
        double horizontal=Math.hypot(dx,dz);
        double fraction=Math.max(0.0,Math.min(1.0,current/maximum));
        double offset=width*0.5+0.18;
        double px=x+dz/horizontal*offset;
        double pz=z-dx/horizontal*offset;
        double bottom=y+height*0.04;
        double top=y+height*0.96;
        if(!Double.isFinite(px)||!Double.isFinite(pz)||
           !Double.isFinite(bottom)||!Double.isFinite(top)||
           !(top>bottom))return false;
        sink.segment(false,fraction,px,bottom,pz,px,top,pz);
        if(fraction>0) sink.segment(true,fraction,px,bottom,pz,
                                    px,bottom+(top-bottom)*fraction,pz);
        return true;
    }
}
