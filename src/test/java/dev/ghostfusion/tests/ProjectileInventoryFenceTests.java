package dev.ghostfusion.tests;

import dev.ghostfusion.features.ProjectileInventoryFence;
import dev.ghostfusion.features.AutoRodPolicy;
import dev.ghostfusion.features.AutoSnowballPolicy;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone owner-thread inventory evidence; game not launched here. */
public final class ProjectileInventoryFenceTests {
    private static long assertions;
    private static void ok(boolean v,String reason) {
        assertions++;
        if(!v)throw new AssertionError(reason);
    }
    private static void rejects(Runnable r,String reason) {
        boolean denied=false;
        try{r.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,reason);
    }
    private static void selected(ProjectileInventoryFence f,long g,long t,
                                 Object source,Object sourceItem,int sourceQty,int sourceDamage,
                                 Object projectile,Object projItem,int qty,int dmg) {
        f.selected(g,t,2,6,source,sourceItem,sourceQty,sourceDamage,
                   projectile,projItem,qty,dmg);
    }
    private static boolean use(ProjectileInventoryFence f,long g,long t,
                               Object src,Object srcItem,int srcQty,int srcDamage,
                               Object proj,Object projItem,int qty,int dmg) {
        return f.allowUse(g,t,2,6,src,srcItem,srcQty,srcDamage,
                          proj,projItem,qty,dmg);
    }
    private static void boundaries() throws Exception {
        ProjectileInventoryFence f=new ProjectileInventoryFence();
        Object source=new Object(),sourceItem=new Object(),snow=new Object(),snowItem=new Object();
        Object replacement=new Object(),otherItem=new Object();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        rejects(()->selected(f,1,5,source,sourceItem,1,0,replacement,snowItem,4,0),
                "second selection cannot replace pending original projectile");
        ok(!use(f,1,6,source,sourceItem,1,0,replacement,snowItem,4,0),
           "same-item replacement stack with same amount is not original object");
        rejects(()->f.nativeUsed(1,6),"denied inventory cannot claim successful native use");
        ok(!use(f,1,6,source,sourceItem,1,0,snow,snowItem,4,0),
           "one-shot rejected USE cannot be retried");
        ok(f.mayRestore(1,2,6,source,sourceItem,1,0,snow),
           "unchanged original + still owned projectile can safely restore");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,source,sourceItem,1,0,snow,snowItem,3,0),
           "depleted stack BEFORE USE denies cast");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,source,sourceItem,1,0,snow,snowItem,4,1),
           "changed projectile metadata denies cast");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,source,sourceItem,1,0,snow,otherItem,4,0),
           "changed projectile item identity denies cast");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,replacement,sourceItem,1,0,snow,snowItem,4,0),
           "replaced original sword slot denies projectile use");
        ok(!f.mayRestore(1,2,6,replacement,sourceItem,1,0,snow),
           "replaced original sword also denies stale hotbar restoration");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,source,sourceItem,0,0,snow,snowItem,4,0),
           "depleted original stack denies cast");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,6,source,sourceItem,1,1,snow,snowItem,4,0),
           "modified original stack item damage denies cast");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,2,6,source,sourceItem,1,0,snow,snowItem,4,0),
           "world generation cannot inherit original hotbar stack");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,7,source,sourceItem,1,0,snow,snowItem,4,0),
           "late USE cannot replay a changed inventory");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(!use(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0),
           "same END tick cannot select and use a stack");
        f.reset();
        selected(f,1,5,source,sourceItem,1,0,snow,snowItem,4,0);
        ok(use(f,1,6,source,sourceItem,1,0,snow,snowItem,4,0),
           "exact original and selected stacks allow next-tick use");
        rejects(()->f.nativeUsed(1,7),"native use acknowledgement wrong tick refused");
        f.nativeUsed(1,6);
        rejects(()->f.nativeUsed(1,6),"native use success cannot be doubled");
        ok(f.mayRestore(1,2,6,source,sourceItem,1,0,null),
           "last snowball legitimately consumed after native use allows restore");
        ok(f.mayRestore(1,2,6,source,sourceItem,1,0,snow),
           "partially consumed stack keeps same instance and can restore");
        ok(!f.mayRestore(1,2,6,source,sourceItem,1,0,replacement),
           "another stack replacing depleted projectile forbids restore");
        ok(!f.mayRestore(1,2,6,source,sourceItem,0,0,null),
           "original slot depletion after throw forbids stale restoration");
        ok(!f.mayRestore(2,2,6,source,sourceItem,1,0,null),
           "world swap forbids restoration even when the old item objects remain");
        f.reset();
        selected(f,1,5,null,null,0,0,snow,snowItem,1,0);
        ok(use(f,1,6,null,null,0,0,snow,snowItem,1,0),
           "optional require_sword=false permits genuinely empty original slot");
        f.nativeUsed(1,6);
        ok(f.mayRestore(1,2,6,null,null,0,0,null),
           "empty original slot plus depleted final snowball restores safely");
        f.reset();
        rejects(()->selected(f,1,5,source,sourceItem,1,0,null,snowItem,1,0),
                "null projectile never qualifies");
        rejects(()->selected(f,1,5,source,sourceItem,1,0,snow,snowItem,0,0),
                "empty projectile never qualifies");
        rejects(()->f.selected(1,5,2,2,source,sourceItem,1,0,snow,snowItem,1,0),
                "selected slot must be different from original slot");
        AtomicBoolean blocked=new AtomicBoolean();
        Thread other=new Thread(()->{
            try{f.mayRestore(1,2,6,source,sourceItem,1,0,snow);}
            catch(IllegalStateException expected){blocked.set(true);}
        });other.start();other.join();
        ok(blocked.get(),"foreign thread cannot inspect inventory owner");
    }
    private static void policyIntegration() {
        Object sword=new Object(), swordType=new Object();
        Object rodItem=new Object(),rodStack=new Object();
        Object snowItem=new Object(),snowStack=new Object();
        ProjectileInventoryFence fence=new ProjectileInventoryFence();
        AutoRodPolicy rod=new AutoRodPolicy();
        ok(rod.decide(1,0,false,true,2,6,30)==AutoRodPolicy.Action.NONE,
           "rod initial physical release arms next attack");
        ok(rod.decide(1,1,true,true,2,6,30)==AutoRodPolicy.Action.SELECT,
           "rod selects one item on physical attack edge");
        rod.committed(AutoRodPolicy.Action.SELECT,1,1);
        selected(fence,1,1,sword,swordType,1,0,rodStack,rodItem,1,0);
        ok(rod.decide(1,2,true,true,6,6,30)==AutoRodPolicy.Action.USE,
           "rod can request use only after original native select");
        ok(!use(fence,1,2,sword,swordType,1,0,new Object(),rodItem,1,0),
           "replacement fishing rod with same type and count cannot be used");
        rod.cancelPrepared();
        ok(rod.decide(1,3,true,false,6,6,30)==AutoRodPolicy.Action.RESTORE,
           "denied rod use goes to restore without fake cast");
        ok(fence.mayRestore(1,2,6,sword,swordType,1,0,rodStack),
           "only unchanged original rod scenario can restore");
        rod.committed(AutoRodPolicy.Action.RESTORE,1,3);
        ok(!rod.castObserved()&&!rod.ownsHotbar(),"rod never reports denied use");

        AutoSnowballPolicy snow=new AutoSnowballPolicy();
        fence.reset();
        ok(snow.decide(2,0,false,true,2,6,20)==AutoSnowballPolicy.Action.NONE,
           "snowball starts in released state");
        ok(snow.decide(2,1,true,true,2,6,20)==AutoSnowballPolicy.Action.SELECT,
           "snowball selects original native stack");
        snow.committed(AutoSnowballPolicy.Action.SELECT,2,1);
        selected(fence,2,1,sword,swordType,1,0,snowStack,snowItem,1,0);
        ok(snow.decide(2,2,true,true,6,6,20)==AutoSnowballPolicy.Action.USE,
           "one-use snowball native stage admitted");
        ok(use(fence,2,2,sword,swordType,1,0,snowStack,snowItem,1,0),
           "exact same snowball stack eligible");
        snow.committed(AutoSnowballPolicy.Action.USE,2,2);
        fence.nativeUsed(2,2);
        ok(snow.decide(2,3,false,true,6,6,20)==AutoSnowballPolicy.Action.RESTORE,
           "last snowball consumed but must release temporary hotbar owner");
        ok(fence.mayRestore(2,2,6,sword,swordType,1,0,null),
           "last snowball depletion does not block original sword restoration");
        snow.committed(AutoSnowballPolicy.Action.RESTORE,2,3);
        ok(!snow.ownsHotbar(),"snowball owner released");
    }
    private static void seeded() {
        Random r=new Random(360189L);
        ProjectileInventoryFence f=new ProjectileInventoryFence();
        Object swordType=new Object(), projectileItem=new Object();
        long approved=0,rejected=0;
        for(int i=0;i<60000;i++) {
            f.reset();
            Object sword=new Object(),projectile=new Object();
            long gen=1+i/500;
            long tick=i+1;
            int amount=1+r.nextInt(64);
            f.selected(gen,tick,2,6,sword,swordType,1,0,
                projectile,projectileItem,amount,0);
            int mode=r.nextInt(12);
            Object currentSword=mode==4?new Object():sword;
            int sourceCount=mode==5?2:1,sourceDamage=mode==10?1:0;
            Object currentProjectile=mode==1?new Object():mode==11?null:projectile;
            Object currentProjectileItem=mode==8?new Object():projectileItem;
            int currentAmount=mode==2?amount+1:amount;
            int currentDamage=mode==3?1:0;
            int sourceSlot=mode==9?3:2;
            long useGen=mode==6?gen+1:gen;
            long useTick=mode==7?tick+2:tick+1;
            boolean expected=mode==0;
            boolean result=f.allowUse(useGen,useTick,sourceSlot,6,
                currentSword,swordType,sourceCount,sourceDamage,
                currentProjectile,currentProjectileItem,currentAmount,currentDamage);
            ok(result==expected,
               "60k independent identity/count/damage/epoch/slot oracle "+i);
            ok(!f.allowUse(gen,tick+1,2,6,sword,swordType,1,0,
                           projectile,projectileItem,amount,0),
               "60k denied-or-allowed USE is one-shot "+i);
            if(result) {
                f.nativeUsed(gen,tick+1);
                ok(f.mayRestore(gen,2,6,sword,swordType,1,0,null),
                   "60k approved last-item depletion can safely restore "+i);
                approved++;
            } else {
                rejects(()->f.nativeUsed(gen,tick+1),
                        "60k denied inventory use cannot be counted as native cast");
                rejected++;
            }
        }
        ok(approved>4500&&rejected>50000,
           "all 12 inventory mutation/identity cases exercised");
        ok(assertions>120000,"60k inventory replay independently verified");
    }
    public static void main(String[] args) throws Exception {
        boundaries();policyIntegration();seeded();
        System.out.println("PROJECTILE_INVENTORY_FENCE_PASS assertions="+assertions);
    }
}
