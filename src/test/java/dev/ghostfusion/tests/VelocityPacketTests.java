package dev.ghostfusion.tests;

import dev.ghostfusion.features.VelocityPacketMath;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The 1.8.9 S12 (short * 8000) and S27 (float) vectors have separate
 * semantics. These are stateless math+profile tests, not proof that native
 * pipeline reflection was successfully installed in a running game.
 */
public final class VelocityPacketTests {
    private static int checks;
    private static void ok(boolean result,String reason) {
        checks++;
        if(!result)throw new AssertionError(reason);
    }
    private static void rejects(Runnable action,String reason) {
        boolean denied=false;
        try{action.run();}catch(IllegalArgumentException expected){denied=true;}
        ok(denied,reason);
    }
    private static void exact() {
        ok(VelocityPacketMath.localS12(42,42),
            "local entity ID admits only matching player S12");
        ok(!VelocityPacketMath.localS12(42,41) &&
           !VelocityPacketMath.localS12(-1,-1),
            "other players, unknown identities excluded");
        int[] a=VelocityPacketMath.s12(8000,-12000,-4000,25,50);
        ok(a[0]==2000 && a[1]==-6000 && a[2]==-1000,
            "S12 short fixed-point scales signed components independently");
        a=VelocityPacketMath.s12(32000,0,-32000,0,0);
        ok(a[0]==0 && a[1]==0 && a[2]==0,
            "0% optionally eliminates just intended knockback components");
        a=VelocityPacketMath.s12(-32768,32767,12345,100,100);
        ok(a[0]==-32768 && a[1]==32767 && a[2]==12345,
            "100% S12 is exact no-op at signed short extremes");
        float[] b=VelocityPacketMath.s27(1.5f,-0.8f,-0.5f,40,25);
        ok(Math.abs(b[0]-0.6f)<1e-6 &&
           Math.abs(b[1]-(-0.2f))<1e-6 &&
           Math.abs(b[2]-(-0.2f))<1e-6,
            "S27 explosion additive impulse gets percentage scaling");
        b=VelocityPacketMath.s27(-0.75f,0.375f,0.11f,100,100);
        ok(Float.floatToRawIntBits(b[0])==Float.floatToRawIntBits(-0.75f) &&
           Float.floatToRawIntBits(b[1])==Float.floatToRawIntBits(0.375f) &&
           Float.floatToRawIntBits(b[2])==Float.floatToRawIntBits(0.11f),
            "100% S27 is exact bit-identical, not a roundoff edit");
        rejects(()->VelocityPacketMath.s12(32768,0,0,50,50),
            "invalid high packet short cannot be edited");
        rejects(()->VelocityPacketMath.s12(-32769,0,0,50,50),
            "invalid negative packet short cannot be edited");
        rejects(()->VelocityPacketMath.s27(Float.NaN,0,0,50,50),
            "S27 nonfinite float rejected");
        rejects(()->VelocityPacketMath.s27(Float.POSITIVE_INFINITY,0,0,50,50),
            "explosion infinity cannot be propagated");
        rejects(()->VelocityPacketMath.s27(4.01f,0,0,50,50),
            "absurd explosion impulse rejected");
        rejects(()->VelocityPacketMath.s12(1,2,3,-1,100),
            "negative multiplier rejected");
        rejects(()->VelocityPacketMath.s27(1,2,3,101,100),
            "out of range multiplier rejected");
        rejects(()->VelocityPacketMath.s12(1,2,3,50,Double.NaN),
            "invalid percentage rejected before modifying native packet");
    }
    private static void replay() throws Exception {
        final Random r=new Random(3412027L);
        for(int i=0;i<45000;i++) {
            int x=r.nextInt(65536)-32768;
            int y=r.nextInt(65536)-32768;
            int z=r.nextInt(65536)-32768;
            double horizontal=r.nextInt(101),vertical=r.nextInt(101);
            int[] result=VelocityPacketMath.s12(x,y,z,horizontal,vertical);
            ok(result.length==3 && Math.abs(result[0])<=Math.abs(x) &&
               Math.abs(result[1])<=Math.abs(y) &&
               Math.abs(result[2])<=Math.abs(z),
                "45k S12 packet scaling never amplifies momentum");
            ok(Math.abs(result[0]-x*horizontal/100.0)<=0.500001 &&
               Math.abs(result[1]-y*vertical/100.0)<=0.500001 &&
               Math.abs(result[2]-z*horizontal/100.0)<=0.500001,
                "45k expected signed-rounding semantics");
            float fx=(r.nextFloat()-0.5f)*7.5f;
            float fy=(r.nextFloat()-0.5f)*7.5f;
            float fz=(r.nextFloat()-0.5f)*7.5f;
            float[] f=VelocityPacketMath.s27(fx,fy,fz,horizontal,vertical);
            ok(f.length==3 && Math.abs(f[0])<=Math.abs(fx)+1e-6f &&
               Math.abs(f[1])<=Math.abs(fy)+1e-6f &&
               Math.abs(f[2])<=Math.abs(fz)+1e-6f,
                "45k bounded S27 never increases an explosion component");
            ok(Math.abs(f[0]-fx*horizontal/100.0)<1e-6 &&
               Math.abs(f[1]-fy*vertical/100.0)<1e-6 &&
               Math.abs(f[2]-fz*horizontal/100.0)<1e-6,
                "45k S27 independent horizontal/vertical fractions");
            ok(VelocityPacketMath.localS12(i%300,i%300) &&
               !VelocityPacketMath.localS12(i%300,(i%300)+1),
                "45k strictly local S12 identity checks");
        }
        final AtomicBoolean concurrent=new AtomicBoolean(true);
        Thread worker=new Thread(()->{
            for(int j=0;j<1000;j++) {
                int[] a=VelocityPacketMath.s12(-4000,4000,1000,25,75);
                if(a[0]!=-1000||a[1]!=3000||a[2]!=250)
                    concurrent.set(false);
            }
        });
        worker.start();worker.join();
        ok(concurrent.get(),
            "pure packet math is immutable and safe for Netty thread use");
    }
    private static void profiles() throws Exception {
        Path path=Files.createTempDirectory("ghostc-r341-velocity-packet-");
        HostRuntime host=new HostRuntime(path);
        ok("hurt".equals(host.velocityMode()),
            "legacy hurt-time mode stays default on all new profiles");
        host.worldPresence(true);
        host.uiActions().cycle("combat.velocity","mode");
        host.uiActions().fraction("combat.velocity","horizontal_percent",0.30);
        host.uiActions().fraction("combat.velocity","vertical_percent",0.60);
        host.uiActions().toggle("combat.velocity");
        ok("packet".equals(host.velocityMode()) &&
           host.featureEnabled("combat.velocity") &&
           host.velocityHorizontalPercent()==30 &&
           host.velocityVerticalPercent()==60,
            "actual F7 module sets explicit S12/S27 packet mode");
        host.close();
        HostRuntime restored=new HostRuntime(path);
        restored.worldPresence(true);
        ok("packet".equals(restored.velocityMode()) &&
           restored.velocityHorizontalPercent()==30 &&
           restored.velocityVerticalPercent()==60 &&
           restored.featureEnabled("combat.velocity"),
            "packet mode persisted with original range-safe percentages");
        restored.uiActions().cycle("combat.velocity","mode");
        ok("hurt".equals(restored.velocityMode()),
            "legacy hurt mode remains selectable after packet mode");
        restored.close();
    }
    public static void main(String[] args) throws Exception {
        exact();
        replay();
        profiles();
        ok(checks>200000,
            "45k randomized S12+S27+entity identity trace contract");
        System.out.println("VELOCITY_PACKET_PASS assertions="+checks);
    }
}
