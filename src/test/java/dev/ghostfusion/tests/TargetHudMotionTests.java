package dev.ghostfusion.tests;
import dev.ghostfusion.features.TargetHudMotion;
import java.util.Random;
/** Deterministic Nebula motion, no dependency on actual Minecraft. */
public final class TargetHudMotionTests {
    private static int assertions;
    private static void ok(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}
    public static void main(String[] a) {
        TargetHudMotion m=new TargetHudMotion();
        Object one=new Object(),two=new Object();
        m.update(one,1_000_000L,0.75,true);
        ok(m.health()==0.75&&m.opacity()==0.4,"initial frame real health/soft entry");
        m.update(one,101_000_000L,0.25,true);
        ok(m.health()>0.25&&m.health()<0.75,"interpolated live health");
        ok(m.opacity()>0.4&&m.opacity()<1.0,"bounded fade");
        m.update(two,102_000_000L,0.9,true);
        ok(m.health()==0.9&&m.opacity()==0.4,"different identity snaps health");
        m.update(two,502_000_000L,0.3,false);
        ok(m.health()==0.3&&m.opacity()==1.0,"disabled animation is immediate");
        m.update(two,402_000_000L,Double.NaN,true);
        ok(m.health()>=0&&m.health()<=1.0,"invalid health and backwards time safe");
        m.reset();m.update(two,1_000_000L,0.6,true);
        ok(m.health()==0.6&&m.opacity()==0.4,"world reset clears continuity");
        ok(TargetHudMotion.fade(0xC0AABBCC,0.5)==0x60AABBCC,"ARGB fade");
        ok(TargetHudMotion.fade(0xC0AABBCC,Double.NaN)==0x00AABBCC,"nonfinite alpha");
        boolean rejected=false;
        try {m.update(null,1L,1.0,true);} catch(IllegalArgumentException x){rejected=true;}
        ok(rejected,"missing target rejected");
        Random rand=new Random(26102026L);long time=10L;
        for(int i=0;i<25000;i++){
            time+=rand.nextInt(12_000_000);
            m.update(i%273==0?one:two,time,rand.nextDouble()*3-1,i%107!=0);
            ok(m.health()>=0&&m.health()<=1.0&&
                m.opacity()>=0&&m.opacity()<=1.0,"bounded seeded frames "+i);
        }
        System.out.println("TARGET_HUD_MOTION_PASS assertions="+assertions);
    }
}
