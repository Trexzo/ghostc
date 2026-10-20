package dev.ghostfusion.features;

/**
 * One authoritative ESP-style interpretation shared by the module settings,
 * Forge renderer and headless tests. No implicit "unknown => draw everything".
 */
public enum EspRenderMode {
    BOX("box",true,false,false,false,false),
    TRACER("tracer",false,true,false,false,false),
    BOTH("both",true,true,false,false,false),
    SKELETON("skeleton",false,false,true,false,false),
    BOX_SKELETON("box_skeleton",true,false,true,false,false),
    CORNER_BOX("corner_box",false,false,false,true,false),
    CORNER_SKELETON("corner_skeleton",false,false,true,true,false),
    FILLED_BOX("filled_box",true,false,false,false,true),
    FILLED_SKELETON("filled_skeleton",true,false,true,false,true);

    public final String id;
    public final boolean boxes, tracers, skeleton, corners, fill;
    EspRenderMode(String id, boolean boxes, boolean tracers,
                  boolean skeleton,boolean corners,boolean fill) {
        this.id=id; this.boxes=boxes; this.tracers=tracers;
        this.skeleton=skeleton;this.corners=corners;this.fill=fill;
    }
    public static EspRenderMode of(String id) {
        for(EspRenderMode mode:values()) if(mode.id.equals(id)) return mode;
        throw new IllegalArgumentException("Invalid ESP style");
    }
}
