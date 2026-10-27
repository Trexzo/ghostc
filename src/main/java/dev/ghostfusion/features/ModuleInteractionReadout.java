package dev.ghostfusion.features;

import dev.ghostfusion.features.ModuleInteractionStateMachine.Denial;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Frame;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Resource;

/**
 * Fixed-vocabulary status for an explicitly opted-in diagnostic HUD.
 * Reports permissions/denials, never claims native actions occurred.
 * Contains no coordinates, player names, server, chat or packet data.
 */
public final class ModuleInteractionReadout {
    private ModuleInteractionReadout() {}

    public static String ownership(Frame f) {
        if(f==null)return "Grants: unavailable";
        return "Grants: "+f.phase.name()+
            " | sprint "+f.sprintOwner.name()+
            " | inventory "+f.inventoryOwner.name();
    }
    public static String suppression(Frame f) {
        if(f==null)return "Held: no live state";
        if(f.denial(Resource.AUTO_SPRINT)==Denial.WTAP_RESERVED)
            return "Held: AutoSprint (WTap owns sprint)";
        if(f.denial(Resource.INVENTORY_MANAGER)==Denial.ARMOR_PRIORITY)
            return "Held: Inventory Manager (AutoArmor priority)";
        // Combat can be disabled while Bridge, Velocity, WTap or AutoSprint
        // are enabled. Never hide their focus/GUI denial by examining
        // combat's aggregate grant alone.
        for(Resource resource:new Resource[]{
            Resource.COMBAT,Resource.BRIDGE,Resource.VELOCITY,
            Resource.WTAP,Resource.AUTO_SPRINT
        }) {
            Denial reason=f.denial(resource);
            if(reason==Denial.WINDOW_INACTIVE)
                return "Held: gameplay (window inactive)";
            if(reason==Denial.WORLD_UNAVAILABLE)
                return "Held: gameplay (no live world)";
            if(reason==Denial.WRONG_SCREEN)
                return "Held: gameplay (GUI open)";
        }
        if(f.denial(Resource.CHEST_STEALER)==Denial.WRONG_SCREEN &&
           f.denial(Resource.AUTO_ARMOR)==Denial.WRONG_SCREEN &&
           f.denial(Resource.INVENTORY_MANAGER)==Denial.WRONG_SCREEN)
            return "Held: inventory (wrong screen)";
        return "Held: none by module arbitration";
    }
}
