package dev.ghostfusion.tests;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.host.RuntimeDiagnostics.Counter;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.74: local warnings and opt-in counters, not server acceptance. */
public final class NativePulseVisibilityTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static void lifecycle() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r374-"));
        h.worldPresence(true);
        NativePulseSettlement gate=new NativePulseSettlement();
        Object w=new Object(),p=new Object();
        long epoch=h.services().generation();
        ok(gate.reconcileNative(epoch,w,p),"first context observation");
        ok(gate.uncertainMask()==0,"initially clear");
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.nativePulseWarning().isEmpty(),"no fabricated safety warning");
        gate.begin(Lane.ATTACK,epoch,0);
        gate.nativeReturned(Lane.ATTACK,epoch,0);
        gate.abandon(Lane.ATTACK,epoch,0);
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.nativePulseWarning().contains("ATTACK paused"),
            "ATTACK warning works when diagnostics disabled");
        ok(h.diagnostics().count(Counter.COMBAT_PULSE_ATTACK_UNCERTAIN)==0,
            "no opt-out telemetry");
        h.modules().setEnabled("hud.diagnostics",true);
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.diagnostics().count(Counter.COMBAT_PULSE_ATTACK_UNCERTAIN)==0,
            "no retroactive synthetic transition");
        gate.begin(Lane.USE,epoch,1);
        gate.abandon(Lane.USE,epoch,1);
        h.nativePulseStatus(gate.uncertainMask());
        ok(gate.uncertainMask()==3 &&
            h.nativePulseWarning().contains("ATTACK + USE"),
            "both quarantined lanes explained");
        for(int j=0;j<200;j++)h.nativePulseStatus(gate.uncertainMask());
        ok(h.diagnostics().count(Counter.COMBAT_PULSE_USE_UNCERTAIN)==1,
            "steady blocked state counts once");
        h.modules().setEnabled("combat.clicker",true);
        PanicController.disableAll(h);
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.nativePulseWarning().contains("ATTACK + USE"),
            "Panic does not hide safety warning");
        h.inputFocus(true);
        h.inputFocus(false);
        h.modules().setEnabled("combat.clicker",true);
        ok(h.nativePulseWarning().contains("ATTACK + USE"),
            "module reenable and focus cannot dismiss quarantine");
        h.worldPresence(false);
        h.worldPresence(true);
        epoch=h.services().generation();
        ok(gate.reconcileNative(epoch,w,p),"same native object reentry observed");
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.nativePulseWarning().contains("ATTACK + USE"),
            "same-world reconnect still visibly quarantined");
        ok(!gate.reconcileNative(epoch,new Object(),p),
            "same-generation native swap cannot recover warning");
        h.worldPresence(false);
        h.worldPresence(true);
        epoch=h.services().generation();
        Object w2=new Object();
        ok(gate.reconcileNative(epoch,w2,p),"new world and generation prove recovery");
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.nativePulseWarning().isEmpty(),"verified recovery clears warning");

        h.modules().setEnabled("hud.diagnostics",true);
        gate.begin(Lane.ATTACK,epoch,2);
        gate.abandon(Lane.ATTACK,epoch,2);
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.diagnostics().count(Counter.COMBAT_PULSE_ATTACK_UNCERTAIN)==1,
            "new opted-in ATTACK uncertainty counts once");
        h.worldPresence(false);
        h.worldPresence(true);
        epoch=h.services().generation();
        ok(gate.reconcileNative(epoch,new Object(),p),"second identity proof");
        h.nativePulseStatus(gate.uncertainMask());
        ok(h.diagnostics().count(Counter.COMBAT_PULSE_RECOVERED)==1,
            "recovery recorded once while opted in");
        boolean invalid=false;
        try{h.nativePulseStatus(4);}catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid && h.nativePulseWarning().isEmpty(),
            "invalid state cannot fabricate a warning");
        AtomicBoolean foreign=new AtomicBoolean();
        Thread t=new Thread(()->{
            try{h.nativePulseStatus(1);}
            catch(IllegalStateException expected){foreign.set(true);}
        });
        t.start();t.join();
        ok(foreign.get(),"owner thread required");
        h.close();
    }
    private static void randomized() throws Exception {
        HostRuntime h=new HostRuntime(Files.createTempDirectory("ghostc-r374-random-"));
        h.worldPresence(true);
        NativePulseSettlement gate=new NativePulseSettlement();
        Random r=new Random(3742026L);
        Object world=new Object(),player=new Object();
        Object knownWorld=world,knownPlayer=player;
        long epoch=h.services().generation(),tick=0;
        boolean[] unknown={false,false};
        for(int i=0;i<90000;i++) {
            if(i>0 && i%881==0) {
                h.worldPresence(false);
                h.worldPresence(true);
                epoch=h.services().generation();
                if((i/881)%3==0)world=new Object();
                if((i/881)%5==0)player=new Object();
            }
            if(world!=knownWorld||player!=knownPlayer) {
                knownWorld=world;knownPlayer=player;
                unknown[0]=unknown[1]=false;
            }
            gate.reconcileNative(epoch,world,player);
            int n=r.nextInt(2);
            Lane lane=n==0?Lane.ATTACK:Lane.USE;
            if(!unknown[n] && r.nextInt(23)==0) {
                gate.begin(lane,epoch,tick);
                if(r.nextBoolean()) {
                    gate.nativeReturned(lane,epoch,tick);
                    gate.committed(lane,epoch,tick);
                }
                gate.abandon(lane,epoch,tick);
                if(gate.phase(lane,epoch)==Phase.UNCERTAIN)unknown[n]=true;
            }
            int expected=(unknown[0]?1:0)|(unknown[1]?2:0);
            int actual=gate.uncertainMask();
            ok(actual==expected,"90k independent quarantine oracle "+i);
            h.nativePulseStatus(actual);
            String label=h.nativePulseWarning();
            ok((actual==0)==label.isEmpty(),"90k no silent blocked lane "+i);
            if(actual==1)ok(label.contains("ATTACK paused"),"ATTACK-only "+i);
            if(actual==2)ok(label.contains("USE paused"),"USE-only "+i);
            if(actual==3)ok(label.contains("ATTACK + USE"),"combined "+i);
            tick++;
        }
        h.close();
        ok(checks>=180000,"90k native transition traces");
    }
    public static void main(String[] args) throws Exception {
        lifecycle();
        randomized();
        System.out.println("NATIVE_PULSE_VISIBILITY_PASS assertions="+checks);
    }
}
