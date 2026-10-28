package dev.ghostfusion.forge189;

import dev.ghostfusion.features.AimTriggerHandoff;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import java.util.Random;

/**
 * Actual Minecraft 1.8.9 AxisAlignedBB / Vec3 native ray boundaries for
 * TriggerBot camera handoff. Pure native geometry, not a launched game.
 */
public final class NativeCrosshairRayTests {
    private static int checks;
    private static void ok(boolean pass,String why) {
        checks++; if(!pass)throw new AssertionError(why);
    }
    private static boolean ray(AxisAlignedBB box,Vec3 eye,Vec3 end) {
        MovingObjectPosition result=box.calculateIntercept(eye,end);
        return result!=null && result.hitVec!=null;
    }
    public static void main(String[] args) {
        AxisAlignedBB target=new AxisAlignedBB(-.3,0,2.0,.3,1.8,2.6);
        Vec3 eye=new Vec3(0,1.62,0);
        ok(ray(target,eye,new Vec3(0,1.62,3.1)),
            "actual native player AABB admits centered valid line of sight");
        ok(!ray(target,eye,new Vec3(1,1.62,3.1)),
            "same range with displaced camera ray must miss");
        ok(!ray(target,eye,new Vec3(0,1.62,1.9)),
            "shorter reach does not intersect target");
        ok(!ray(target,eye,new Vec3(0,2.4,3.1)),
            "look ray above the player's body must miss");
        AxisAlignedBB border=target.expand(.1,.1,.1);
        ok(ray(border,eye,new Vec3(.36,1.62,3.1)),
            "native expanded collision border may admit near-edge view");
        ok(!AimTriggerHandoff.yieldCamera(true,true,false,false,true,true),
            "native ray alone never preempts physical held attack");
        ok(AimTriggerHandoff.yieldCamera(true,false,false,false,true,true),
            "valid native ray and unheld player can yield camera");
        Random rand=new Random(350189L);
        for(int i=0;i<12000;i++) {
            double x=rand.nextDouble()*2.0-1.0;
            Vec3 end=new Vec3(x,1.62,3.1);
            boolean hit=ray(target,eye,end);
            // End point x at z=2.0 is about 0.645 of the final x.
            // When |x| < .3 the ray must hit; when |x| > .6 it must miss.
            if(Math.abs(x)<.29)
                ok(hit,"native center ray stable "+i);
            if(Math.abs(x)>.61)
                ok(!hit,"native distant off-axis ray rejected "+i);
        }
        ok(checks>2000,"native Forge 1.8.9 ray probe exercised real AABB logic");
        System.out.println("NATIVE_CROSSHAIR_RAY_PASS assertions="+checks);
    }
}
