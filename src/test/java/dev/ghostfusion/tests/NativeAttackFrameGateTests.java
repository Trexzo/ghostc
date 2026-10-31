package dev.ghostfusion.tests;

import dev.ghostfusion.features.NativeAttackFrameGate;
import dev.ghostfusion.core.ReservedKeys;
import java.util.Random;

/**
 * R3.70 exact native output-fence traces. All results are read-only policy
 * decisions, not genuine input events or Minecraft server verification.
 */
public final class NativeAttackFrameGateTests {
    private static long checks;
    private static void ok(boolean good,String why) {
        checks++;if(!good)throw new AssertionError(why);
    }
    private static void exact() {
        Object p=new Object(),world=new Object(),enemy=new Object();
        ok(NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-100,-99,true,false,true),
           "stable physical mouse attack bindings and player/world");
        ok(!NativeAttackFrameGate.sameFrame(p,new Object(),world,world,
                -100,-99,-100,-99,true,false,true),
           "local player replacement rejects queued click");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,new Object(),
                -100,-99,-100,-99,true,false,true),
           "world replacement rejects queued click");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-101,-99,true,false,true),
           "attack key remap during frame never dispatches on new key");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-100,-98,true,false,true),
           "use key remap also invalidates click");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-100,-99,false,false,true),
           "lost focus blocks native pulse");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-100,-99,true,true,true),
           "opened GUI blocks native pulse");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-99,-100,-99,true,false,false),
           "despawned local player blocks native pulse");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                -100,-100,-100,-100,true,false,true),
           "overlapping attack and use binding denied");
        ok(!NativeAttackFrameGate.sameFrame(p,p,world,world,
                ReservedKeys.CLICK_GUI,-99,ReservedKeys.CLICK_GUI,-99,
                true,false,true),"reserved F7 attack binding denied");
        ok(NativeAttackFrameGate.safeUseBinding(-99),
           "native mouse use binding permitted");
        ok(!NativeAttackFrameGate.safeUseBinding(0),
           "unbound use key never emits Block Hit");
        ok(!NativeAttackFrameGate.safeUseBinding(ReservedKeys.CLICK_GUI),
           "reserved F7 use key never emits Block Hit");
        ok(NativeAttackFrameGate.attackState(false,true,true,false,false),
           "Clicker still requires continuous physical attack");
        ok(!NativeAttackFrameGate.attackState(false,true,false,false,false),
           "release between planning and output denies stale Clicker");
        ok(!NativeAttackFrameGate.attackState(false,true,true,true,false),
           "new manual use holds precedence over Clicker");
        ok(!NativeAttackFrameGate.attackState(false,true,true,false,true),
           "using item interrupts synthetic click");
        ok(NativeAttackFrameGate.attackState(true,false,false,false,false),
           "unheld TriggerBot may still dispatch after dwell");
        ok(!NativeAttackFrameGate.attackState(true,false,true,false,false),
           "new manual attack cancels TriggerBot before native pulse");
        ok(!NativeAttackFrameGate.attackState(true,true,false,false,false),
           "TriggerBot cannot inherit a sampled manual click");
        ok(NativeAttackFrameGate.target(enemy,enemy,enemy,p,false,20),
           "exact live enemy under crosshair");
        ok(!NativeAttackFrameGate.target(enemy,new Object(),enemy,p,false,20),
           "crosshair changed to new entity before attack output");
        ok(!NativeAttackFrameGate.target(enemy,enemy,new Object(),p,false,20),
           "entity registry reused ID with new object");
        ok(!NativeAttackFrameGate.target(enemy,enemy,enemy,p,true,20),
           "dead target cannot support entity-only Clicker/Block Hit");
        ok(!NativeAttackFrameGate.target(enemy,enemy,enemy,p,false,0),
           "zero-health target cannot support an entity-only native write");
    }

    private static void randomized() {
        Random rng=new Random(3702026L);
        Object p=new Object(),oldWorld=new Object(),enemy=new Object();
        for(int i=0;i<90000;i++) {
            int oldAttack=rng.nextInt(10)-5,oldUse=rng.nextInt(10)-5;
            int attack=rng.nextInt(3)==0?oldAttack:rng.nextInt(10)-5;
            int use=rng.nextInt(3)==0?oldUse:rng.nextInt(10)-5;
            boolean samePlayer=rng.nextBoolean(),sameWorld=rng.nextBoolean();
            boolean registered=rng.nextBoolean(),focused=rng.nextBoolean();
            boolean gui=rng.nextBoolean();
            boolean expectFrame=samePlayer&&sameWorld&&registered&&focused&&!gui&&
                oldAttack!=0&&oldAttack!=ReservedKeys.CLICK_GUI&&
                oldAttack!=oldUse&&oldAttack==attack&&oldUse==use;
            boolean actualFrame=NativeAttackFrameGate.sameFrame(p,
                samePlayer?p:new Object(),oldWorld,
                sameWorld?oldWorld:new Object(),oldAttack,oldUse,attack,use,
                focused,gui,registered);
            ok(actualFrame==expectFrame,"90k final-frame binding/identity oracle "+i);
            boolean trigger=rng.nextBoolean();
            boolean sampled=rng.nextBoolean(),output=rng.nextBoolean();
            boolean using=rng.nextBoolean(),useHeld=rng.nextBoolean();
            boolean expectedAttack=!using&&!useHeld&&
                (trigger?(!sampled&&!output):(sampled&&output));
            ok(NativeAttackFrameGate.attackState(trigger,sampled,output,useHeld,using)
                ==expectedAttack,"90k final physical-key oracle "+i);
            boolean sameTarget=rng.nextBoolean(),sameRegistry=rng.nextBoolean();
            boolean dead=rng.nextInt(9)==0;
            double health=rng.nextInt(20)==0?0:20;
            boolean expectedTarget=sameTarget&&sameRegistry&&!dead&&health>0;
            ok(NativeAttackFrameGate.target(enemy,
                sameTarget?enemy:new Object(),
                sameRegistry?enemy:new Object(),p,dead,health)
                ==expectedTarget,"90k final crosshair object/health oracle "+i);
        }
        ok(checks>270000,"large deterministic native output regression");
    }
    public static void main(String[] args) {
        exact();
        randomized();
        System.out.println("NATIVE_ATTACK_FRAME_GATE_PASS assertions="+checks);
    }
}
