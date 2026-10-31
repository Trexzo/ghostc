package dev.ghostfusion.features;

import dev.ghostfusion.core.ReservedKeys;

/**
 * Pure final-boundary evidence for optional native attack or Block Hit key
 * pulses. A scheduled intent is NEVER enough: both input bindings and the
 * exact Minecraft player/world must still match the admitted frame.
 *
 * No world, entity, input, timing, or packet writes happen here.
 */
public final class NativeAttackFrameGate {
    private NativeAttackFrameGate() {}

    public static boolean sameFrame(Object observedPlayer,Object livePlayer,
                                    Object observedWorld,Object liveWorld,
                                    int oldAttack,int oldUse,int newAttack,
                                    int newUse,boolean focused,
                                    boolean guiOpen,boolean playerRegistered) {
        return observedPlayer!=null && observedPlayer==livePlayer &&
            observedWorld!=null && observedWorld==liveWorld &&
            playerRegistered && focused && !guiOpen &&
            oldAttack!=0 && oldAttack!=ReservedKeys.CLICK_GUI &&
            oldAttack!=oldUse &&
            oldAttack==newAttack && oldUse==newUse;
    }

    public static boolean attackState(boolean trigger,
                                      boolean attackAtSample,
                                      boolean attackAtOutput,
                                      boolean useAtOutput,
                                      boolean usingItem) {
        return !useAtOutput && !usingItem &&
            (trigger ? !attackAtSample && !attackAtOutput :
                attackAtSample && attackAtOutput);
    }

    public static boolean safeUseBinding(int code) {
        return code!=0 && code!=ReservedKeys.CLICK_GUI;
    }

    /** Exact cached crosshair target must remain the live registered object. */
    public static boolean target(Object firstTarget,Object currentCrosshair,
                                 Object registered,Object player,
                                 boolean dead,double health) {
        return firstTarget!=null && firstTarget==currentCrosshair &&
            LiveCombatTargetGate.eligible(firstTarget,registered,player,dead,health);
    }
}
