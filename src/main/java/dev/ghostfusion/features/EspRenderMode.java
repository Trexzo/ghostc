package dev.ghostfusion.features;

/**
 * One authoritative ESP-style interpretation shared by the module settings,
 * Forge renderer and headless tests. No implicit "unknown => draw everything".
 */
public enum EspRenderMode {
    BOX("box",true,false,false),
    TRACER("tracer",false,true,false),
    BOTH("both",true,true,false),
    SKELETON("skeleton",false,false,true),
    BOX_SKELETON("box_skeleton",true,false,true);

    public final String id;
    public final boolean boxes, tracers, skeleton;
    EspRenderMode(String id, boolean boxes, boolean tracers, boolean skeleton) {
        this.id=id; this.boxes=boxes; this.tracers=tracers; this.skeleton=skeleton;
    }
    public static EspRenderMode of(String id) {
        for(EspRenderMode mode:values()) if(mode.id.equals(id)) return mode;
        throw new IllegalArgumentException("Invalid ESP style");
    }
}
