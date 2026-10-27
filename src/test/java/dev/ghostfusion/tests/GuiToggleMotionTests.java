package dev.ghostfusion.tests;

import dev.ghostfusion.ui.GuiToggleMotion;

/** Exact-contract, Minecraft-independent Slate switch transition tests. */
public final class GuiToggleMotionTests {
    private static int checks;
    private static void ok(boolean condition,String reason){
        checks++;if(!condition)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        GuiToggleMotion motion=new GuiToggleMotion();
        Object module=new Object(),setting=new Object();
        long start=1_000_000_000L;
        ok(motion.value(module,false,start)==0.0,"first disabled frame is off");
        ok(motion.value(setting,true,start)==1.0,"first enabled frame is on");
        ok(motion.value(module,true,start)==0.0,"toggle starts without a jump");
        double mid=motion.value(module,true,start+80_000_000L);
        ok(mid>0.5&&mid<1.0,"transition eases without using frame counts");
        ok(motion.value(module,false,start+80_000_000L)==mid,
            "reversal continues at current animated position");
        double reversed=motion.value(module,false,start+120_000_000L);
        ok(reversed>=0.0&&reversed<mid,"reverse animation moves toward off");
        ok(motion.value(module,false,start+300_000_000L)==0.0,
            "fully off after the reverse duration");
        ok(motion.value(setting,true,start+300_000_000L)==1.0,
            "independent setting identity stays enabled");
        ok(motion.value(module,true,start-1)==0.0,
            "backward time cannot produce out-of-range pixels");
        ok(motion.value(module,true,start+300_000_000L)>=0.0,
            "recovered monotonic time is bounded");
        ok(GuiToggleMotion.blendArgb(0xFF000000,0xFFFFFFFF,0.5)==0xFF808080,
            "ARGB blending retains predictable midpoint colors");
        ok(GuiToggleMotion.blendArgb(0xFF123456,0xFFABCDEF,-1)==0xFF123456,
            "progress clamps to off color");
        ok(GuiToggleMotion.blendArgb(0xFF123456,0xFFABCDEF,2)==0xFFABCDEF,
            "progress clamps to on color");
        motion.clear();
        ok(motion.value(module,true,start)==1.0,
            "GUI reinitialization snaps to the actual state");
        boolean rejected=false;
        try{motion.value(null,true,start);}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"missing identity is refused");
        rejected=false;
        try{GuiToggleMotion.blendArgb(0,1,Double.NaN);}
        catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"invalid fraction rejected");

        GuiToggleMotion stress=new GuiToggleMotion();
        Object[] identities=new Object[24];
        for(int i=0;i<identities.length;i++)identities[i]=new Object();
        for(int frame=0;frame<20000;frame++) {
            long now=3_000_000_000L+frame*15_000_000L;
            for(int i=0;i<identities.length;i++) {
                double value=stress.value(identities[i],((frame/7+i)%2)==0,now);
                ok(Double.isFinite(value)&&value>=0&&value<=1,
                    "every rendered switch remains bounded");
            }
        }
        System.out.println("GUI_TOGGLE_MOTION_PASS checks="+checks);
    }
}
