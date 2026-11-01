package dev.ghostfusion.tests;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.util.Random;

/** R3.73 owner-thread native recovery; no game/server effect is simulated. */
public final class NativePulseRecoveryTests {
    private static long checks;
    private static void ok(boolean b,String why) {
        checks++;if(!b)throw new AssertionError(why);
    }
    private static void rejects(Runnable r,String why) {
        boolean denied=false;
        try{r.run();}catch(RuntimeException expected){denied=true;}
        ok(denied,why);
    }
    private static void lifecycle() throws Exception {
        HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-r373-"));
        host.worldPresence(true);
        host.modules().setEnabled("combat.clicker",true);
        SessionServices s=host.services();
        NativePulseSettlement gate=new NativePulseSettlement();
        Object world=new Object(),player=new Object();
        long first=s.generation();
        ok(gate.admitNative(Lane.ATTACK,first,world,player),
            "exact original world/player identity admitted");
        gate.begin(Lane.ATTACK,first,s.tick());
        gate.nativeReturned(Lane.ATTACK,first,s.tick());
        gate.abandon(Lane.ATTACK,first,s.tick());
        ok(gate.phase(Lane.ATTACK,first)==Phase.UNCERTAIN,
            "partially committed native attack must be quarantined");
        PanicController.disableAll(host);
        ok(s.generation()==first,"Panic does not change native session");
        ok(!gate.admitNative(Lane.ATTACK,s.generation(),world,player),
            "Panic never clears partial native output");
        host.modules().setEnabled("combat.clicker",true);
        ok(!gate.admitNative(Lane.ATTACK,s.generation(),world,player),
            "manual reenable after Panic cannot replay native attack");
        host.inputFocus(true);
        host.inputFocus(false);
        ok(!gate.admitNative(Lane.ATTACK,s.generation(),world,player),
            "GUI/focus round trip cannot rearm");
        ok(!host.runFeature("combat.clicker",new Runnable(){
            public void run(){throw new IllegalStateException("injected fault");}
        }),"FeatureFaultGuard quarantines failing enabled module");
        host.modules().setEnabled("combat.clicker",true);
        ok(!gate.admitNative(Lane.ATTACK,s.generation(),world,player),
            "fault quarantine and explicit reenable cannot rearm");
        ok(!gate.admitNative(Lane.ATTACK,s.generation(),new Object(),player),
            "same-generation native identity change cannot rearm");
        host.worldPresence(false);
        host.worldPresence(true);
        long newGeneration=s.generation();
        ok(newGeneration>first,"transient leave/reenter grows session generation");
        ok(!gate.admitNative(Lane.ATTACK,newGeneration,world,player),
            "same native world/player across reconnect remains uncertain");
        ok(!gate.admit(Lane.ATTACK,newGeneration),
            "legacy policy caller cannot clear a native pinned uncertainty");
        ok(gate.admitNative(Lane.USE,newGeneration,world,player),
            "independent use lane remains available");
        ok(!gate.admitNative(Lane.ATTACK,newGeneration,new Object(),player),
            "same-epoch spoof cannot clear uncertainty");
        host.worldPresence(false);
        host.worldPresence(true);
        Object changedWorld=new Object();
        long admittedGeneration=s.generation();
        ok(gate.admitNative(Lane.ATTACK,admittedGeneration,changedWorld,player),
            "new generation and different native world prove recovery");
        ok(gate.admitNative(Lane.USE,admittedGeneration,changedWorld,player),
            "both lanes see verified identity transition");
        rejects(()->gate.admitNative(Lane.ATTACK,newGeneration,world,player),
            "old disconnect callback cannot downgrade native generation");
        host.close();
    }
    private static void randomized() {
        NativePulseSettlement gate=new NativePulseSettlement();
        Random r=new Random(3732026L);
        Object world=new Object(),player=new Object(),knownWorld=world,knownPlayer=player;
        long generation=4,tick=0;
        boolean[] uncertain={false,false};
        for(int i=0;i<90000;i++) {
            if(i%881==0) {
                generation++;
                if((i/881)%3==0)world=new Object();
                if((i/881)%5==0)player=new Object();
            }
            if(world!=knownWorld||player!=knownPlayer) {
                knownWorld=world;knownPlayer=player;
                uncertain[0]=uncertain[1]=false;
            }
            Lane lane=r.nextBoolean()?Lane.ATTACK:Lane.USE;
            int k=lane.ordinal();
            boolean admitted=gate.admitNative(lane,generation,world,player);
            ok(admitted==!uncertain[k],
                "90k identity and epoch oracle "+i);
            if(admitted&&r.nextInt(5)==0) {
                gate.begin(lane,generation,tick);
                int outcome=r.nextInt(5);
                if(outcome==0) {
                    gate.abandon(lane,generation,tick);
                    uncertain[k]=true;
                } else {
                    gate.nativeReturned(lane,generation,tick);
                    if(outcome==1) {
                        gate.abandon(lane,generation,tick);
                        uncertain[k]=true;
                    } else {
                        gate.committed(lane,generation,tick);
                        gate.abandon(lane,generation,tick);
                    }
                }
            }
            ok(gate.admitNative(lane,generation,world,player)==!uncertain[k],
                "90k no same-native-world replay of ambiguous key "+i);
            tick++;
        }
        ok(checks>=180000,"deterministic recovery matrix executed");
    }
    public static void main(String[] args) throws Exception {
        lifecycle();randomized();
        System.out.println("NATIVE_PULSE_RECOVERY_PASS assertions="+checks);
    }
}
