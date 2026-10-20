package dev.ghostfusion.features;

/**
 * Schematic world-space 3D box corners. Each of eight corners emits three
 * inward-pointing segments (X,Y,Z), not full edges; exactly 24 GL lines.
 * No Minecraft or OpenGL dependencies and no allocation per line/player.
 */
public final class EspCornerBoxGeometry {
    public interface Sink {
        void line(double x1,double y1,double z1,
                  double x2,double y2,double z2);
    }
    private EspCornerBoxGeometry() { }
    public static boolean valid(double minX,double minY,double minZ,
                                double maxX,double maxY,double maxZ,double fraction) {
        if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||
           !Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ)||
           !Double.isFinite(fraction)||fraction<0.10||fraction>0.50)
            return false;
        double dx=maxX-minX,dy=maxY-minY,dz=maxZ-minZ;
        // Check dimensions before emission. Overlarge or degenerate extents
        // cannot create an invalid GL vertex or occupy the nearest-80 budget.
        return Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz) &&
            dx>=0.02&&dy>=0.02&&dz>=0.02 &&
            dx<=8.0&&dy<=8.0&&dz<=8.0;
    }
    public static boolean emit(double minX,double minY,double minZ,
                               double maxX,double maxY,double maxZ,
                               double fraction,Sink sink) {
        if(sink==null)throw new IllegalArgumentException("corner sink");
        if(!valid(minX,minY,minZ,maxX,maxY,maxZ,fraction))return false;
        final double lx=(maxX-minX)*fraction;
        final double ly=(maxY-minY)*fraction;
        final double lz=(maxZ-minZ)*fraction;
        for(int i=0;i<8;i++){
            boolean bx=(i&1)!=0,by=(i&2)!=0,bz=(i&4)!=0;
            double x=bx?maxX:minX,y=by?maxY:minY,z=bz?maxZ:minZ;
            sink.line(x,y,z,bx?x-lx:x+lx,y,z);
            sink.line(x,y,z,x,by?y-ly:y+ly,z);
            sink.line(x,y,z,x,y,bz?z-lz:z+lz);
        }
        return true;
    }
}
