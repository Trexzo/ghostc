package dev.ghostfusion.features;

/**
 * Passive provenance check for ONE original Minecraft 1.8.9 snowball use.
 * No packets, inventory writes, positions, player identities or remote ACKs.
 *
 * A valid local decrement is exactly one snowball on the original ItemStack
 * after the vanilla use method reported LOCAL stack mutation. Replacements,
 * larger corrections, changed metadata or changes after an unchanged native
 * return are ambiguous. They are never credited to a server-accepted throw.
 */
public final class ProjectileInventoryReconciliation {
    private ProjectileInventoryReconciliation() {}

    public static ProjectileObservationWindow.Signal classify(
            Object selectedStack,Object selectedItem,int beforeCount,int beforeDamage,
            boolean nativeLocalMutation,
            Object currentStack,Object currentItem,int currentCount,int currentDamage) {
        if(selectedStack==null||selectedItem==null||beforeCount<1)
            throw new IllegalArgumentException("missing original snowball selection");
        // Forge may observe a real empty slot; normalize its identity and
        // quantity, but no invalid, inconsistent sample may imply success.
        if(currentStack==null) {
            if(currentItem!=null||currentCount!=0||currentDamage!=0)
                return ProjectileObservationWindow.Signal.INVENTORY_REPLACED;
            return beforeCount==1&&nativeLocalMutation?
                ProjectileObservationWindow.Signal.SNOWBALL_COUNT_DECREASED:
                ProjectileObservationWindow.Signal.INVENTORY_REPLACED;
        }
        if(currentStack!=selectedStack||currentItem!=selectedItem||
           currentDamage!=beforeDamage)
            return ProjectileObservationWindow.Signal.INVENTORY_REPLACED;
        if(currentCount==beforeCount)
            return ProjectileObservationWindow.Signal.NONE;
        // One vanilla snowball use may only remove one from this stack.
        if(nativeLocalMutation&&currentCount==beforeCount-1&&
           currentCount>=0)
            return ProjectileObservationWindow.Signal.SNOWBALL_COUNT_DECREASED;
        return ProjectileObservationWindow.Signal.INVENTORY_REPLACED;
    }
}
