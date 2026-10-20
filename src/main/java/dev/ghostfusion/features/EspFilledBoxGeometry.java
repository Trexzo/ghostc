package dev.ghostfusion.features;

/**
 * Original six-face translucent AABB geometry. Java 8 pure policy:
 * emits exactly 6 properly ordered quads / 24 world-space vertices.
 * Consumer owns OpenGL state and batch lifecycle.
 */
public final class EspFilledBoxGeometry {
    public interface Sink {
        void quad(double ax,double ay,double az,
                  double bx,double by,double bz,
                  double cx,double cy,double cz,
                  double dx,double dy,double dz);
    }
    private EspFilledBoxGeometry() {}
    public static boolean valid(double minX,double minY,double minZ,
                                double maxX,double maxY,double maxZ) {
        if(!Double.isFinite(minX)||!Double.isFinite(minY)||!Double.isFinite(minZ)||
           !Double.isFinite(maxX)||!Double.isFinite(maxY)||!Double.isFinite(maxZ))
            return false;
        double dx=maxX-minX,dy=maxY-minY,dz=maxZ-minZ;
        // Match the Corner Box plausible bounds: no inverted, degenerate or
        // enormous extents, and no nonfinite face coordinates.
        return Double.isFinite(dx)&&Double.isFinite(dy)&&Double.isFinite(dz) &&
            dx>=.02&&dy>=.02&&dz>=.02&&dx<=8&&dy<=8&&dz<=8;
    }
    public static boolean emit(double minX,double minY,double minZ,
                               double maxX,double maxY,double maxZ,Sink out) {
        if(out==null)throw new IllegalArgumentException("fill sink");
        if(!valid(minX,minY,minZ,maxX,maxY,maxZ))return false;
        // Opposing face normal directions; GL_CULL_FACE is disabled by the
        // caller and lighting is disabled, so transparency is symmetric.
        out.quad(minX,minY,minZ, minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ); // -X
        out.quad(maxX,minY,maxZ, maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ); // +X
        out.quad(minX,minY,maxZ, minX,minY,minZ, maxX,minY,minZ, maxX,minY,maxZ); // -Y
        out.quad(minX,maxY,minZ, minX,maxY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ); // +Y
        out.quad(maxX,minY,minZ, minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ); // -Z
        out.quad(minX,minY,maxZ, maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ); // +Z
        return true;
    }
}
