package dev.ghostfusion.tests;

import dev.ghostfusion.features.ProjectileInventoryReconciliation;
import dev.ghostfusion.features.ProjectileObservationWindow.Signal;
import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import dev.ghostfusion.features.PhysicalInputGate;
import dev.ghostfusion.features.FixedClickScheduler;
import java.util.Random;

/**
 * Deterministic passive inventory reconciliation and safe combat handoff.
 * Does not claim any server inventory/packet acknowledgement or real gameplay.
 */
public final class ProjectileReconciliationTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;
        if(!v)throw new AssertionError(why);
    }
    private static Signal classify(Object stack,Object item,int before,
                                   boolean nativeChange,
                                   Object now,Object nowItem,int current,int damage) {
        return ProjectileInventoryReconciliation.classify(
            stack,item,before,0,nativeChange,now,nowItem,current,damage);
    }
    private static void basics() {
        Object stack=new Object(),item=new Object(),other=new Object();
        ok(classify(stack,item,8,true,stack,item,7,0)==Signal.SNOWBALL_COUNT_DECREASED,
           "one original snowball consumed after vanilla local mutation");
        ok(classify(stack,item,1,true,null,null,0,0)==Signal.SNOWBALL_COUNT_DECREASED,
           "last original snowball removed only when native local mutation occurred");
        ok(classify(stack,item,8,true,null,null,0,0)==Signal.INVENTORY_REPLACED,
           "eight-count stack disappearing cannot be attributed to a single throw");
        ok(classify(stack,item,8,false,null,null,0,0)==Signal.INVENTORY_REPLACED,
           "server correction / external deletion without native local change is ambiguous");
        ok(classify(stack,item,8,false,stack,item,7,0)==Signal.INVENTORY_REPLACED,
           "later decrement after unchanged local use cannot be attributed to that use");
        ok(classify(stack,item,8,true,stack,item,6,0)==Signal.INVENTORY_REPLACED,
           "two-count correction is not one client snowball");
        ok(classify(stack,item,8,true,stack,item,8,0)==Signal.NONE,
           "unchanged original stack produces no false positive");
        ok(classify(stack,item,8,false,stack,item,8,0)==Signal.NONE,
           "creative-mode unchanged stack produces no false positive");
        ok(classify(stack,item,8,true,other,item,7,0)==Signal.INVENTORY_REPLACED,
           "equivalent same-item replacement is ambiguous");
        ok(classify(stack,item,8,true,stack,other,7,0)==Signal.INVENTORY_REPLACED,
           "same object but changed item identity is ambiguous");
        ok(classify(stack,item,8,true,stack,item,7,1)==Signal.INVENTORY_REPLACED,
           "changed metadata invalidates decrement provenance");
        ok(classify(stack,item,8,true,stack,item,9,0)==Signal.INVENTORY_REPLACED,
           "server stack refill cannot be called a projectile use");
        ok(classify(stack,item,8,true,null,item,0,0)==Signal.INVENTORY_REPLACED,
           "inconsistent null inventory snapshot is ambiguous");
        ok(classify(stack,item,1,true,stack,item,0,0)==Signal.SNOWBALL_COUNT_DECREASED,
           "same object with zero count from last use still signals local decrement");
        boolean bad=false;
        try {ProjectileInventoryReconciliation.classify(
            null,item,1,0,true,null,null,0,0);}
        catch(IllegalArgumentException expected){bad=true;}
        ok(bad,"missing original selection is rejected before classification");
    }
    private static void combatHandoff() {
        // ForgeEventAdapter suppresses optional combat while projectile
        // owns SELECT/USE/RESTORE. After reset, PhysicalInputGate explicitly
        // requires a REAL release before re-admitting a held synthetic click.
        PhysicalInputGate input=new PhysicalInputGate();
        FixedClickScheduler scheduler=new FixedClickScheduler();
        long epoch=7;
        int attack=-100,use=-99;
        input.allowed(epoch,true,true,false,false,attack,use);
        ok(input.allowed(epoch,true,true,false,true,attack,use),
           "valid fresh physical press can run normal combat before projectile");
        AutoRodPolicy rod=new AutoRodPolicy();
        rod.decide(epoch,0,false,true,2,6,30);
        ok(rod.decide(epoch,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
           "rod takes physical press and selects temporary slot");
        rod.committed(AutoRodPolicy.Action.SELECT,epoch,1);
        ok(rod.decide(epoch,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
           "rod owns native item-use tick");
        rod.committed(AutoRodPolicy.Action.USE,epoch,2);
        ok(rod.decide(epoch,3,true,true,6,6,30)==AutoRodPolicy.Action.RESTORE,
           "rod retains exclusive hotbar ownership through restoration");
        rod.committed(AutoRodPolicy.Action.RESTORE,epoch,3);
        ok(!rod.ownsHotbar(),"projectile releases native hotbar owner on restore");
        input.reset();scheduler.reset(); // same effect as combat.reset() while owned
        ok(!input.allowed(epoch,true,true,false,true,attack,use),
           "same held attack may NOT inherit the prior clicker authorization");
        ok(!scheduler.due(1000000L,true,false,10),
           "click scheduler disarmed during projectile ownership");
        ok(!input.allowed(epoch,true,true,false,false,attack,use),
           "physical release clears handoff quarantine without synthetic attack");
        ok(input.allowed(epoch,true,true,false,true,attack,use),
           "fresh physical press re-admits normal combat after restoration");
        ok(!scheduler.due(2000000L,true,true,10),
           "no immediate catch-up attack on re-arming");
        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        snow.decide(epoch,4,false,true,2,5,20);
        ok(snow.decide(epoch,5,true,true,2,5,20)==AutoSnowballPolicy.Action.SELECT,
           "snowball new physical press selects without colliding rod owner");
        snow.committed(AutoSnowballPolicy.Action.SELECT,epoch,5);
        ok(snow.decide(epoch,6,true,false,5,5,20)==AutoSnowballPolicy.Action.RESTORE,
           "snowball target loss skips use and enters safe RESTORE");
        snow.committed(AutoSnowballPolicy.Action.RESTORE,epoch,6);
        ok(!snow.castObserved()&&!snow.ownsHotbar(),
           "no phantom snowball cast or retained ownership after target loss");
    }
    private static void seeded() {
        Random rng=new Random(363189L);
        long changed=0,ambiguous=0,unchanged=0;
        for(int i=0;i<80000;i++) {
            Object initial=new Object(),item=new Object(),another=new Object();
            int before=1+rng.nextInt(64);
            int variant=i%10;
            boolean nativeChange=variant!=6&&variant!=7;
            Object now=initial,nowItem=item;
            int count=before,damage=0;
            if(variant==0)count=before-1; // exactly one consumed
            if(variant==1) {count=before-1;nativeChange=false;}
            if(variant==2) {now=null;nowItem=null;count=0;}
            if(variant==3) {now=another;count=before-1;}
            if(variant==4) {nowItem=another;count=before-1;}
            if(variant==5) {count=before+1;}
            if(variant==6) count=before;
            if(variant==7) {count=before-1;damage=1;}
            if(variant==8)count=Math.max(0,before-2);
            if(variant==9) {now=initial;count=before;}
            Signal actual=classify(initial,item,before,nativeChange,
                                   now,nowItem,count,damage);
            boolean exactDecrement=variant==0||
                (variant==2 && before==1 && nativeChange)||
                (variant==8 && before==1 && nativeChange);
            // variant 8 with before=1 decrements to zero, valid once.
            Signal expected=exactDecrement?Signal.SNOWBALL_COUNT_DECREASED:
                variant==6||variant==9?Signal.NONE:
                Signal.INVENTORY_REPLACED;
            ok(actual==expected,"80k independent snowball inventory oracle "+i);
            if(actual==Signal.SNOWBALL_COUNT_DECREASED)changed++;
            else if(actual==Signal.INVENTORY_REPLACED)ambiguous++;
            else unchanged++;
        }
        ok(changed>7000&&ambiguous>45000&&unchanged>14000,
           "80k original/deleted/replaced/refilled/metadata cases exercised");
        ok(checks>80000,"80k reconciliation frames executed");
    }
    public static void main(String[] args) {
        basics();combatHandoff();seeded();
        System.out.println("PROJECTILE_RECONCILIATION_PASS assertions="+checks);
    }
}
