package dev.ghostfusion.features;

/**
 * Owner-thread, in-memory inventory identity admission for an existing
 * AutoRod/AutoSnowball SELECT -> USE -> RESTORE native transaction.
 *
 * Minecraft ItemStack references are opaque here. The Forge controller
 * separately verifies actual ItemFishingRod/ItemSnowball and local-world
 * player; this fence ensures an item replaced by an equivalent stack never
 * inherits consent to throw. No inventory objects or IDs are exported.
 *
 * The original hotbar slot must remain the same stack/count/item/damage.
 * Before USE, the projectile slot must also be exactly the selected stack,
 * with its original quantity and metadata. AFTER a successful native use,
 * the projectile may legitimately decrease or become null (last snowball),
 * but cannot become an unrelated replacement stack.
 */
public final class ProjectileInventoryFence {
    private final Thread owner=Thread.currentThread();
    private long epoch=-1,selectedTick=-1;
    private int sourceSlot=-1,projectileSlot=-1;
    private Object source,sourceItem,projectile,projectileItem;
    private int sourceCount,sourceDamage,projectileCount,projectileDamage;
    private boolean selected,checkedUse,approvedUse,used;

    private void check() {
        if(Thread.currentThread()!=owner)
            throw new IllegalStateException("projectile inventory owner thread");
    }
    private static boolean stackMatches(Object expected,Object expectedItem,
                                        int expectedSize,int expectedDamage,
                                        Object current,Object currentItem,
                                        int currentSize,int currentDamage) {
        return current==expected && currentItem==expectedItem &&
            currentSize==expectedSize && currentDamage==expectedDamage;
    }
    /** SELECT committed to native inventory before this call. */
    public void selected(long generation,long tick,int originalSlot,int selectedSlot,
                         Object originalStack,Object originalItem,int originalCount,int originalDamage,
                         Object selectedStack,Object selectedItem,int selectedCount,int selectedDamage) {
        check();
        if(generation<0||tick<0||originalSlot<0||originalSlot>8||
           selectedSlot<0||selectedSlot>8||originalSlot==selectedSlot||
           selectedStack==null||selectedItem==null||selectedCount<1||
           originalCount<0||(originalStack==null?originalCount!=0||originalItem!=null:
                               originalCount<1||originalItem==null))
            throw new IllegalArgumentException("invalid projectile inventory selection");
        if(selected)throw new IllegalStateException("projectile inventory already selected");
        epoch=generation;selectedTick=tick;
        sourceSlot=originalSlot;projectileSlot=selectedSlot;
        source=originalStack;sourceItem=originalItem;
        sourceCount=originalCount;sourceDamage=originalDamage;
        projectile=selectedStack;projectileItem=selectedItem;
        projectileCount=selectedCount;projectileDamage=selectedDamage;
        selected=true;checkedUse=approvedUse=used=false;
    }
    private boolean sourceMatches(Object current,Object item,int count,int damage) {
        return stackMatches(source,sourceItem,sourceCount,sourceDamage,
                            current,item,count,damage);
    }
    /** Exact next tick; consumes permission even if the inventory changed. */
    public boolean allowUse(long generation,long tick,int currentOriginalSlot,
                            int currentProjectileSlot,
                            Object currentSource,Object currentSourceItem,
                            int currentSourceCount,int currentSourceDamage,
                            Object currentProjectile,Object currentProjectileItem,
                            int currentProjectileCount,int currentProjectileDamage) {
        check();
        boolean allowed=selected && !checkedUse && epoch==generation &&
            tick>selectedTick && tick-selectedTick==1 &&
            currentOriginalSlot==sourceSlot && currentProjectileSlot==projectileSlot &&
            sourceMatches(currentSource,currentSourceItem,
                          currentSourceCount,currentSourceDamage) &&
            stackMatches(projectile,projectileItem,projectileCount,projectileDamage,
                         currentProjectile,currentProjectileItem,
                         currentProjectileCount,currentProjectileDamage);
        checkedUse=true;
        approvedUse=allowed;
        return allowed;
    }
    /** Mark only after genuine native sendUseItem returned success. */
    public void nativeUsed(long generation,long tick) {
        check();
        if(!selected||!checkedUse||!approvedUse||used||generation!=epoch||
           tick!=selectedTick+1)
            throw new IllegalStateException("projectile inventory unapproved native use");
        used=true;
    }
    /**
     * On RESTORE, do not overwrite user's changed source slot or an entirely
     * replaced projectile stack. Legitimate depletion after native use is OK.
     */
    public boolean mayRestore(long generation,int currentOriginalSlot,
                              int currentProjectileSlot,
                              Object currentSource,Object currentSourceItem,
                              int currentSourceCount,int currentSourceDamage,
                              Object currentProjectile) {
        check();
        return selected && generation==epoch &&
            currentOriginalSlot==sourceSlot &&
            currentProjectileSlot==projectileSlot &&
            sourceMatches(currentSource,currentSourceItem,currentSourceCount,currentSourceDamage) &&
            (currentProjectile==projectile||(used&&currentProjectile==null));
    }
    public void reset() {
        check();
        epoch=selectedTick=-1;
        sourceSlot=projectileSlot=-1;
        source=sourceItem=projectile=projectileItem=null;
        sourceCount=sourceDamage=projectileCount=projectileDamage=0;
        selected=checkedUse=approvedUse=used=false;
    }
}
