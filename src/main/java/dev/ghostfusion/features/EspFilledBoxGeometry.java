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
        // Original fixed-order emission retained for backward compatibility.
        for(int i=0;i<6;i++)emitFace(i,minX,minY,minZ,maxX,maxY,maxZ,out);
        return true;
    }

    /**
     * Alpha-compositing friendly order for the original six AABB faces.
     * With disabled depth writes, the caller must draw distant faces before
     * closer faces. Six face-center distances are sorted by bounded scans:
     * no temporary array, map or per-player allocation.
     *
     * Returns false without emitting anything for invalid box or camera pose.
     * Equal-distance face centers break ties by original face index.
     */
    public static boolean emitBackToFront(double minX,double minY,double minZ,
                                          double maxX,double maxY,double maxZ,
                                          double cameraX,double cameraY,double cameraZ,
                                          Sink out) {
        if(out==null)throw new IllegalArgumentException("fill sink");
        if(!valid(minX,minY,minZ,maxX,maxY,maxZ) ||
           !Double.isFinite(cameraX)||!Double.isFinite(cameraY)||
           !Double.isFinite(cameraZ))return false;
        // All six distances must be checked before starting any output.
        for(int face=0;face<6;face++) {
            if(!Double.isFinite(faceDistanceSq(face,minX,minY,minZ,
                maxX,maxY,maxZ,cameraX,cameraY,cameraZ)))return false;
        }
        int emitted=0;
        while(emitted!=63) {
            int selected=-1;
            double largest=-1;
            for(int face=0;face<6;face++) {
                if((emitted&(1<<face))!=0)continue;
                double distance=faceDistanceSq(face,minX,minY,minZ,
                    maxX,maxY,maxZ,cameraX,cameraY,cameraZ);
                if(selected<0 || distance>largest) {
                    selected=face;
                    largest=distance;
                }
            }
            emitFace(selected,minX,minY,minZ,maxX,maxY,maxZ,out);
            emitted|=(1<<selected);
        }
        return true;
    }
    private static double faceDistanceSq(int face,double minX,double minY,double minZ,
                                         double maxX,double maxY,double maxZ,
                                         double cameraX,double cameraY,double cameraZ) {
        double midX=minX+(maxX-minX)*.5;
        double midY=minY+(maxY-minY)*.5;
        double midZ=minZ+(maxZ-minZ)*.5;
        double x=(face==0)?minX:(face==1)?maxX:midX;
        double y=(face==2)?minY:(face==3)?maxY:midY;
        double z=(face==4)?minZ:(face==5)?maxZ:midZ;
        double dx=x-cameraX,dy=y-cameraY,dz=z-cameraZ;
        return dx*dx+dy*dy+dz*dz;
    }
    private static void emitFace(int face,double minX,double minY,double minZ,
                                 double maxX,double maxY,double maxZ,Sink out) {
        switch(face) {
            case 0:
                out.quad(minX,minY,minZ, minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ);
                return;
            case 1:
                out.quad(maxX,minY,maxZ, maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ);
                return;
            case 2:
                out.quad(minX,minY,maxZ, minX,minY,minZ, maxX,minY,minZ, maxX,minY,maxZ);
                return;
            case 3:
                out.quad(minX,maxY,minZ, minX,maxY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ);
                return;
            case 4:
                out.quad(maxX,minY,minZ, minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ);
                return;
            case 5:
                out.quad(minX,minY,maxZ, maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ);
                return;
            default: throw new IllegalArgumentException("AABB face index");
        }
    }
}
