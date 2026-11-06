package dev.ghostfusion.tests;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import java.nio.file.Files;
import java.util.Random;

/**
 * R3.87 owner-thread dispatch/finally publication regression.
 * Native KeyBinding delivery and gameplay are NOT simulated here: this tests
 * the real settlement and HostRuntime warning under injected dispatch faults.
 */
public final class NativePulseFinallyPublicationTests {
    private static long checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    // Mirror the actual ForgeEventAdapter's try/finally publication boundary;
    // test_forge_wiring.py independently asserts that live adapter wiring.
    private static void frame(HostRuntime host,NativePulseSettlement gate,
            Lane lane,long epoch,long tick,int stage) {
        try {
            if(stage==0)return; // refused admission, inactive or suspended
            gate.begin(lane,epoch,tick);
            try {
                if(stage==1)throw new IllegalStateException("injected pre-return");
                gate.nativeReturned(lane,epoch,tick);
                if(stage==2)throw new AssertionError("injected post-return");
                gate.committed(lane,epoch,tick);
                if(stage==3)throw new IllegalStateException("injected after commit");
            } finally {
                gate.abandon(lane,epoch,tick);
            }
        } finally {
            host.nativePulseStatus(gate.uncertainMask());
        }
    }
    private static boolean fault(HostRuntime host,NativePulseSettlement gate,
            Lane lane,long epoch,long tick,int stage) {
        try {frame(host,gate,lane,epoch,tick,stage);}
        catch(RuntimeException|AssertionError expected) {return true;}
        return false;
    }
    private static void exact() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r387-"));
        h.worldPresence(true);
        NativePulseSettlement g=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        long epoch=h.services().generation();
        ok(g.reconcileNative(epoch,world,player),"game identity pinned");
        ok(fault(h,g,Lane.ATTACK,epoch,0,1),"native pre-return fault propagated");
        ok(g.uncertainMask()==1 && h.nativePulseWarning().contains("ATTACK paused"),
            "ATTACK visible immediately after abort");
        ok(!g.admitNative(Lane.ATTACK,epoch,world,player),
            "ATTACK cannot replay after failed dispatch");
        // USE is independently protected even if ATTACK is already unknown.
        ok(fault(h,g,Lane.USE,epoch,1,2),"native post-return Error propagated");
        ok(g.uncertainMask()==3 &&
            h.nativePulseWarning().contains("ATTACK + USE"),
            "both lanes visible before next END tick");
        PanicController.disableAll(h);
        frame(h,g,Lane.ATTACK,epoch,2,0);
        ok(h.nativePulseWarning().contains("ATTACK + USE"),
            "Panic, inactive dispatch and finally do not clear quarantine");
        h.worldPresence(false);
        h.worldPresence(true);
        epoch=h.services().generation();
        ok(g.reconcileNative(epoch,world,player),"same-object reconnect observed");
        frame(h,g,Lane.USE,epoch,3,0);
        ok(g.uncertainMask()==3 && !g.admitNative(Lane.USE,epoch,world,player),
            "same-world reconnect cannot rearm USE");
        h.worldPresence(false);
        h.worldPresence(true);
        epoch=h.services().generation();
        world=new Object();
        ok(g.reconcileNative(epoch,world,player),"new native identity and epoch");
        frame(h,g,Lane.ATTACK,epoch,4,0);
        ok(g.uncertainMask()==0 && h.nativePulseWarning().isEmpty(),
            "identity-verified recovery clears warning");
        ok(fault(h,g,Lane.ATTACK,epoch,5,3),
            "postcommit telemetry exception propagated");
        ok(g.uncertainMask()==0 && h.nativePulseWarning().isEmpty(),
            "postcommit fault cannot invent an uncertain pulse");
        ok(g.admitNative(Lane.ATTACK,epoch,world,player),
            "later-tick attack remains eligible after full commit");
        frame(h,g,Lane.ATTACK,epoch,6,4);
        ok(g.uncertainMask()==0 && h.nativePulseWarning().isEmpty(),
            "normal committed frame remains clear");
        h.close();
    }
    private static void randomized() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r387-random-"));
        h.worldPresence(true);
        NativePulseSettlement g=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        long epoch=h.services().generation();
        g.reconcileNative(epoch,world,player);
        Random r=new Random(3872026L);
        boolean[] uncertain={false,false};
        for(int i=0;i<30000;i++) {
            if(i>0 && i%701==0) {
                h.worldPresence(false);
                h.worldPresence(true);
                epoch=h.services().generation();
                if((i/701)%2==0) {
                    world=new Object();
                    uncertain[0]=uncertain[1]=false;
                }
                ok(g.reconcileNative(epoch,world,player),
                    "valid epoch and exact native world observed");
            }
            int n=r.nextInt(2),stage=1+r.nextInt(4);
            Lane lane=n==0?Lane.ATTACK:Lane.USE;
            boolean allowed=g.admitNative(lane,epoch,world,player);
            if(allowed) {
                boolean thrown=fault(h,g,lane,epoch,i,stage);
                ok(thrown==(stage<=3),"fault propagated only when injected");
                if(stage<=2)uncertain[n]=true;
            } else frame(h,g,lane,epoch,i,0);
            int expected=(uncertain[0]?1:0)|(uncertain[1]?2:0);
            ok(g.uncertainMask()==expected,"no accidental ATTACK/USE replay");
            ok(h.nativePulseWarning().isEmpty()==(expected==0),
                "finally publication matches unresolved state each frame");
        }
        h.close();
        ok(checks>=60000,"large deterministic fault-frame matrix");
    }
    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("NATIVE_PULSE_FINALLY_PUBLICATION_PASS assertions="+checks);
    }
}
