package dev.ghostfusion.tests;
import dev.ghostfusion.ui.ModuleListMotion;
import java.util.Random;

/** Live-only membership, FPS-independent ease and stable ID reuse. */
public final class ModuleListMotionTests {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        ModuleListMotion motion=new ModuleListMotion();
        motion.beginFrame(1_000_000_000L);
        ok(motion.observe("combat.aimassist")==0,"first enabled frame fades in");
        motion.endFrame();
        motion.beginFrame(1_090_000_000L);
        double half=motion.observe("combat.aimassist");
        ok(half>0.5&&half<1,"90ms is midway in 180ms ease");
        motion.endFrame();
        motion.beginFrame(1_200_000_000L);
        ok(motion.observe("combat.aimassist")==1,"fully visible after 180ms");
        motion.endFrame();
        motion.beginFrame(1_300_000_000L);
        motion.endFrame();
        ok(motion.activeCount()==0,"disabled item immediately removed");
        motion.beginFrame(1_350_000_000L);
        motion.observe("hud.temporary");
        motion.endFrame();
        ok(motion.activeCount()==1,"temporary entry tracked");
        motion.reset();
        ok(motion.activeCount()==0,"world/HUD reset evicts live identity");
        motion.beginFrame(1_400_000_000L);
        ok(motion.observe("combat.aimassist")==0,
            "re-enabled module begins fresh transition");
        ok(motion.observe("movement.sprint")==0,
            "different module IDs retain independent timelines");
        motion.endFrame();
        boolean invalid=false;
        try{motion.observe(null);}catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid,"null IDs refused");
        ok(ModuleListMotion.fade(0xC0FFFFFF,0.5)==0x60FFFFFF,
            "alpha changes while RGB remains constant");
        Random rng=new Random(27102026L);
        ModuleListMotion stress=new ModuleListMotion();
        for(int i=0;i<20000;i++){
            stress.beginFrame(1_000_000_000L+i*20_000_000L);
            int n=rng.nextInt(25);
            for(int j=0;j<n;j++){
                double progress=stress.observe("module."+j);
                ok(progress>=0&&progress<=1,"bounded interpolation "+i);
            }
            stress.endFrame();
            ok(stress.activeCount()==n,"no stale live rows "+i);
        }
        System.out.println("MODULE_LIST_MOTION_PASS checks="+checks);
    }
}
