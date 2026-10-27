package dev.ghostfusion.features;

/**
 * Stateless Minecraft 1.8.9 S12/S27 velocity-vector math.
 * Intended to run on the Netty inbound thread without touching game state.
 * 100%/100% is an exact no-op, and non-finite/excessive vectors are rejected.
 */
public final class VelocityPacketMath {
    private VelocityPacketMath() {}
    private static void fractions(double h,double v) {
        if(!Double.isFinite(h)||!Double.isFinite(v)||
           h<0||h>100||v<0||v>100)
            throw new IllegalArgumentException("invalid velocity percentage");
    }
    public static int[] s12(int x,int y,int z,double h,double v) {
        fractions(h,v);
        if(x<-32768||x>32767||y<-32768||y>32767||
           z<-32768||z>32767)
            throw new IllegalArgumentException("out-of-range S12 fixed velocity");
        return new int[]{(int)Math.round(x*h/100.0),
            (int)Math.round(y*v/100.0),(int)Math.round(z*h/100.0)};
    }
    public static float[] s27(float x,float y,float z,double h,double v) {
        fractions(h,v);
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||
           Math.abs(x)>4 || Math.abs(y)>4 || Math.abs(z)>4)
            throw new IllegalArgumentException("unsafe explosion vector");
        return new float[]{(float)(x*h/100.0),
            (float)(y*v/100.0),(float)(z*h/100.0)};
    }
    /** Prevents accidentally modifying S12 packets for other entities. */
    public static boolean localS12(int packetEntityId,int localEntityId) {
        return packetEntityId>=0 && localEntityId>=0 &&
            packetEntityId==localEntityId;
    }
}
