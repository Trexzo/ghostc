package dev.ghostfusion.tests;

import dev.ghostfusion.core.ReservedKeys;
import dev.ghostfusion.features.FixedClickScheduler;
import dev.ghostfusion.features.PhysicalInputGate;
import java.util.concurrent.atomic.AtomicBoolean;

/** R3.23: a live attack/use rebind must revoke the previous physical hold. */
public final class PhysicalInputBindingTests {
    private static int assertions;
    private static void ok(boolean yes, String why) {
        assertions++;
        if(!yes)throw new AssertionError(why);
    }
    private static boolean admitted(PhysicalInputGate gate,long epoch,
                                    boolean down,int attack,int use) {
        return gate.allowed(epoch,true,true,false,down,attack,use);
    }
    private static void rebindStress() {
        for(int i=0;i<4096;i++) {
            final long epoch=i+1;
            int oldAttack=-100-i, nextAttack=-10000-i;
            int oldUse=-20000-i, nextUse=-30000-i;
            PhysicalInputGate gate=new PhysicalInputGate();
            ok(!admitted(gate,epoch,false,oldAttack,oldUse),
               "release first acquires hold "+i);
            ok(admitted(gate,epoch,true,oldAttack,oldUse),
               "stable physical hold admits "+i);
            ok(!admitted(gate,epoch,true,nextAttack,oldUse),
               "attack rebind while held revokes synthetic press "+i);
            ok(!admitted(gate,epoch,true,nextAttack,oldUse),
               "repeated held remapped attack stays denied "+i);
            ok(!admitted(gate,epoch,false,nextAttack,oldUse),
               "release after attack rebind is observable "+i);
            ok(admitted(gate,epoch,true,nextAttack,oldUse),
               "fresh press on new attack mapping may admit "+i);
            ok(!admitted(gate,epoch,true,nextAttack,nextUse),
               "use mapping rebind revokes attack hold "+i);
            ok(!admitted(gate,epoch,true,nextAttack,nextUse),
               "held state after use remap remains denied "+i);
            ok(!admitted(gate,epoch,false,nextAttack,nextUse),
               "release after use remap observed "+i);
            ok(admitted(gate,epoch,true,nextAttack,nextUse),
               "fresh press after use remap admitted "+i);
            ok(!admitted(gate,epoch,true,nextUse,nextUse),
               "attack and use sharing a code always denied "+i);
            ok(!admitted(gate,epoch,true,nextAttack,nextUse),
               "removing collision without release cannot resume "+i);
            ok(!admitted(gate,epoch,false,nextAttack,nextUse),
               "release after collision observed "+i);
            ok(admitted(gate,epoch,true,nextAttack,nextUse),
               "new noncolliding press accepted "+i);
            ok(!admitted(gate,epoch,true,0,nextUse),
               "unbound attack must not admit "+i);
            ok(!admitted(gate,epoch,true,nextAttack,nextUse),
               "restoring attack code while held cannot resume "+i);
            ok(!admitted(gate,epoch,false,nextAttack,nextUse),
               "release after unbound phase observed "+i);
            ok(admitted(gate,epoch,true,nextAttack,nextUse),
               "post-unbound valid fresh press admitted "+i);
        }
    }
    private static void focusEpochAndReserved() {
        PhysicalInputGate gate=new PhysicalInputGate();
        ok(!admitted(gate,1,false,-100,-99),"initial release");
        ok(admitted(gate,1,true,-100,-99),"initial press");
        ok(!gate.allowed(1,true,false,false,true,-100,-99),
           "focus lost denies and invalidates hold");
        ok(!admitted(gate,1,true,-100,-99),
           "focus regained while held stays blocked");
        ok(!admitted(gate,1,false,-100,-99),"release after focus");
        ok(admitted(gate,1,true,-100,-99),"press after focus release");
        ok(!gate.allowed(1,true,true,true,true,-100,-99),
           "open GUI revokes physical hold");
        ok(!admitted(gate,1,true,-100,-99),
           "GUI closed while held stays denied");
        ok(!admitted(gate,1,false,-100,-99),"GUI release");
        ok(admitted(gate,1,true,-100,-99),"GUI fresh press");
        ok(!admitted(gate,2,true,-100,-99),"new world epoch revokes hold");
        ok(!admitted(gate,2,false,-100,-99),"new world release");
        ok(admitted(gate,2,true,-100,-99),"new world fresh press");
        ok(!admitted(gate,2,true,ReservedKeys.CLICK_GUI,-99),
           "F7 never acts as attack");
        ok(!admitted(gate,2,false,ReservedKeys.CLICK_GUI,-99),
           "reserved binding release");
        ok(!admitted(gate,2,true,ReservedKeys.CLICK_GUI,-99),
           "reserved binding still rejected after release");
        ok(!admitted(gate,2,true,-100,-99),
           "remapping from reserved without release stays denied");
        ok(!admitted(gate,2,false,-100,-99),"fresh release from F7");
        ok(admitted(gate,2,true,-100,-99),"valid remap after release");
        gate.reset();
        ok(!admitted(gate,2,true,-100,-99),"reset denies held input");
        ok(!admitted(gate,2,false,-100,-99),"reset release");
        ok(admitted(gate,2,true,-100,-99),"reset then fresh press");
        boolean rejected=false;
        try {gate.allowed(-1,true,true,false,false,-100,-99);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"negative world generation rejected");
    }
    private static void schedulerIntegration() {
        PhysicalInputGate gate=new PhysicalInputGate();
        FixedClickScheduler scheduler=new FixedClickScheduler();
        ok(!admitted(gate,3,false,-100,-99),"scheduler setup release");
        ok(admitted(gate,3,true,-100,-99),"scheduler initial physical hold");
        ok(!scheduler.due(0,true,true,10),"first held frame belongs to vanilla");
        ok(scheduler.due(120000000,true,true,10),
           "stable held frame schedules synthetic click");
        ok(!admitted(gate,3,true,-101,-99),"hot rebind denies held attack");
        ok(!scheduler.due(240000000,true,false,10),
           "rebind fence disarms scheduler immediately");
        ok(!admitted(gate,3,true,-101,-99),"held remap remains fenced");
        ok(!scheduler.due(360000000,true,false,10),
           "denied frames cannot resume synthetic pulses");
        ok(!admitted(gate,3,false,-101,-99),"new release seen");
        ok(admitted(gate,3,true,-101,-99),"new physical press admitted");
        ok(!scheduler.due(480000000,true,true,10),
           "fresh physical press never triggers immediate extra click");
        ok(scheduler.due(600000000,true,true,10),
           "stable new hold resumes scheduled clicks normally");
    }
    private static void ownership() throws Exception {
        PhysicalInputGate gate=new PhysicalInputGate();
        AtomicBoolean denied=new AtomicBoolean();
        Thread worker=new Thread(()->{
            try {gate.allowed(5,true,true,false,false,-100,-99);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();worker.join();
        ok(denied.get(),"gate remains owner-thread-only");
    }
    public static void main(String[] args) throws Exception {
        rebindStress();focusEpochAndReserved();schedulerIntegration();ownership();
        ok(assertions>73000,"substantial remapped physical input regression");
        System.out.println("PHYSICAL_INPUT_BINDING_PASS assertions="+assertions);
    }
}
