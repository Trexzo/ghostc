package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimManualInputPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.52 native mouse-helper delta consumption policy tests (not gameplay). */
public final class AimManualInputTests {
    private static int checks;
    private static void ok(boolean yes,String why) {
        checks++;if(!yes)throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String why) {
        boolean refused=false;
        try{action.run();}catch(RuntimeException expected){refused=true;}
        ok(refused,why);
    }
    private static void exactTiming() throws Exception {
        AimManualInputPolicy p=new AimManualInputPolicy();
        ok(!p.suppressed(1,0,false,100,0,3,3),
            "default disabled preserves legacy Aim Assist camera output");
        ok(!p.suppressed(1,1,true,2,2,3,3),"subthreshold movement is ignored");
        ok(p.suppressed(1,2,true,3,0,3,3),"threshold movement starts override");
        ok(p.suppressed(1,3,true,0,0,3,3),"manual precedence persists for tick two");
        ok(p.suppressed(1,4,true,0,0,3,3),"manual precedence persists for tick three");
        ok(!p.suppressed(1,5,true,0,0,3,3),"exact pause deadline resumes Aim Assist");
        ok(p.suppressed(1,6,true,-3,0,3,3),"negative physical movement admitted");
        ok(p.suppressed(1,7,true,0,0,3,3),"negative-input hold");
        ok(!p.suppressed(1,8,false,0,0,3,3),"toggle off releases override immediately");
        ok(!p.suppressed(1,9,true,0,0,3,3),"reenable does not inherit stale hold");
        ok(p.suppressed(1,10,true,Integer.MIN_VALUE,0,3,3),
            "signed minimum delta cannot overflow abs and escape suppression");
        ok(!p.suppressed(1,11,true,0,0,4,3),
            "threshold setting edit revokes old suppression");
        ok(p.suppressed(1,12,true,4,0,4,3),"new threshold triggers correctly");
        ok(!p.suppressed(1,13,true,0,0,4,1),
            "pause length edit revokes older hold");
        ok(p.suppressed(1,14,true,0,-9,4,1),"vertical mouse movement takes precedence");
        ok(!p.suppressed(1,15,true,0,0,4,1),"one-tick pause has exact duration");
        ok(p.suppressed(2,0,true,0,10,4,3),"new world observes fresh physical mouse");
        ok(!p.suppressed(3,0,true,0,0,4,3),
            "new generation cannot inherit earlier manual hold");
        rejects(()->p.suppressed(3,-1,true,0,0,4,3),"negative tick rejected");
        rejects(()->p.suppressed(3,1,true,0,0,0,3),"invalid threshold refused");
        rejects(()->p.suppressed(3,1,true,0,0,4,21),"unbounded pause refused");
        p.reset();
        ok(!p.suppressed(3,1,true,0,0,4,3),
            "Panic/focus reset revokes all physical hold state");
        rejects(()->p.suppressed(3,0,true,0,0,4,3),
            "backwards owner tick fails closed");
        AtomicBoolean rejected=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{p.suppressed(3,2,true,999,999,4,3);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(rejected.get(),"foreign input/Netty thread denied owner mutation");
    }
    private static void independentReplay() {
        AimManualInputPolicy p=new AimManualInputPolicy();
        Random r=new Random(3522026L);
        long epoch=1,lastManual=-1;
        int oldThreshold=-1,oldPause=-1;
        for(int tick=0;tick<50000;tick++) {
            if(tick%909==0) {
                p.reset();epoch++;
                lastManual=-1;oldThreshold=oldPause=-1;
            }
            boolean enabled=r.nextInt(5)!=0;
            int dx=r.nextInt(17)-8,dy=r.nextInt(17)-8;
            int threshold=r.nextInt(7)+1,pause=r.nextInt(6)+1;
            // Keep settings for several frames to exercise real cooldown.
            threshold=3+(tick/199)%3;
            pause=2+(tick/263)%3;
            boolean expected;
            if(!enabled) {
                lastManual=-1;oldThreshold=oldPause=-1;
                expected=false;
            }else{
                if(threshold!=oldThreshold||pause!=oldPause) {
                    lastManual=-1;oldThreshold=threshold;oldPause=pause;
                }
                if(Math.abs((long)dx)>=threshold ||
                   Math.abs((long)dy)>=threshold)lastManual=tick;
                expected=lastManual>=0 && tick-lastManual<pause;
            }
            boolean result=p.suppressed(epoch,tick,enabled,dx,dy,threshold,pause);
            ok(result==expected,"50k seeded physical mouse precedence oracle "+tick);
        }
    }
    private static void profile() throws Exception {
        Path root=Files.createTempDirectory("ghostc-r352-mouse-");
        HostRuntime h=new HostRuntime(root);
        ok(!h.aimRespectMouse(),"legacy physical override defaults off");
        ok(h.aimMouseThreshold()==3 && h.aimMousePauseTicks()==3,
            "bounded 3-delta/3-tick defaults");
        h.worldPresence(true);
        h.uiActions().bool("combat.aimassist","respect_mouse");
        h.uiActions().fraction("combat.aimassist","mouse_delta_threshold",.5);
        h.uiActions().fraction("combat.aimassist","mouse_pause_ticks",.5);
        ok(h.aimRespectMouse() && h.aimMouseThreshold()>3 &&
           h.aimMousePauseTicks()>3,"real typed F7 controls change live values");
        int threshold=h.aimMouseThreshold(),pause=h.aimMousePauseTicks();
        h.close();
        HostRuntime reload=new HostRuntime(root);
        ok(reload.aimRespectMouse() && reload.aimMouseThreshold()==threshold &&
           reload.aimMousePauseTicks()==pause,
            "verified profile roundtrip stores physical manual override");
        reload.worldPresence(true);
        reload.uiActions().toggle("combat.aimassist");
        PanicController.disableAll(reload);
        reload.close();
        HostRuntime after=new HostRuntime(root);
        ok(after.aimRespectMouse() && after.aimMouseThreshold()==threshold &&
           after.aimMousePauseTicks()==pause,
            "F6 Panic preserves settings without enabling camera automation");
        after.close();
    }
    public static void main(String[] args) throws Exception {
        exactTiming();
        independentReplay();
        profile();
        ok(checks>50000,"manual precedence deterministic replay coverage");
        System.out.println("AIM_MANUAL_INPUT_PASS assertions="+checks);
    }
}
