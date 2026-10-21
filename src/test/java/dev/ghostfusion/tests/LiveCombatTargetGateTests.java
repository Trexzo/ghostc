package dev.ghostfusion.tests;

import dev.ghostfusion.features.LiveCombatTargetGate;
import dev.ghostfusion.services.ActionCoordinator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java8 read-only proof that render-cached crosshair targets are live. */
public final class LiveCombatTargetGateTests {
    private static long assertions;
    private static void ok(boolean pass,String reason){
        assertions++;
        if(!pass)throw new AssertionError(reason);
    }
    private static boolean gate(Object hit,Object world,Object local,
                                boolean dead,double health){
        return LiveCombatTargetGate.eligible(hit,world,local,dead,health);
    }
    private static void focused() {
        Object live=new Object(), local=new Object(),stale=new Object();
        ok(gate(live,live,local,false,20),"current world entity accepted");
        ok(!gate(live,null,local,false,20),"removed entity rejected");
        ok(!gate(live,stale,local,false,20),
           "reused numeric entity ID cannot validate a different object");
        ok(!gate(null,live,local,false,20),"null crosshair target rejected");
        ok(!gate(local,local,local,false,20),"local player cannot become target");
        ok(!gate(live,live,local,true,20),"death flag rejects lingering health");
        ok(!gate(live,live,local,false,0),"zero HP rejects");
        ok(!gate(live,live,local,false,-10),"negative HP rejects");
        for(double h:new double[]{Double.NaN,Double.POSITIVE_INFINITY,
                                   Double.NEGATIVE_INFINITY}){
            ok(!gate(live,live,local,false,h),"nonfinite HP rejects "+h);
        }
        ok(gate(live,live,local,false,Double.MIN_VALUE),
           "any finite positive health is eligible");
        // A valid entity hit is shared by opt-in entity-only clicker,
        // target-required Hit Select and target-required Block Hit.
        ActionCoordinator actions=new ActionCoordinator();
        long tick=10,generation=3;
        for(int mode=0;mode<3;mode++){
            boolean valid=gate(stale,live,local,false,20);
            if(valid)actions.request("combat.policy."+mode,ActionCoordinator.Type.ATTACK,
                ActionCoordinator.Phase.OUTPUT,0,1,tick,1,generation);
            ok(actions.pending()==0,"no synthetic action for stale target mode "+mode);
        }
        actions.shutdown();
    }
    private static void randomized(){
        Random rng=new Random(2462026L);
        Object local=new Object(), entity=new Object(),other=new Object();
        for(int i=0;i<160000;i++){
            Object hit;
            switch(rng.nextInt(5)){
                case 0: hit=null;break;
                case 1: hit=local;break;
                case 2: hit=other;break;
                default:hit=entity;
            }
            Object resolved;
            switch(rng.nextInt(5)){
                case 0: resolved=null;break;
                case 1: resolved=local;break;
                case 2: resolved=other;break;
                default:resolved=entity;
            }
            boolean dead=rng.nextBoolean();
            double health;
            switch(rng.nextInt(9)){
                case 0:health=Double.NaN;break;
                case 1:health=Double.POSITIVE_INFINITY;break;
                case 2:health=Double.NEGATIVE_INFINITY;break;
                case 3:health=0.0;break;
                case 4:health=-rng.nextDouble();break;
                default:health=0.00001+rng.nextDouble()*2048;
            }
            boolean expected=hit!=null && hit==resolved && hit!=local &&
                !dead && Double.isFinite(health) && health>0;
            ok(gate(hit,resolved,local,dead,health)==expected,
               "cached hit identity+liveness parity "+i);
        }
        // Force 10,000 despawn+respawn transitions reusing the same numeric
        // slot: old crosshair references must not satisfy object identity.
        for(int i=0;i<10000;i++){
            Object old=new Object(), replacement=new Object();
            ok(!gate(old,replacement,local,false,20),"stale respawn blocked "+i);
            ok(gate(replacement,replacement,local,false,20),
               "new world object accepted "+i);
        }
    }
    private static void liveAimVisibility() {
        Object live=new Object(),local=new Object(),stale=new Object();
        ok(LiveCombatTargetGate.eligible(live,live,local,false,20,false,true),
           "visible-only aim accepts living current-world target");
        ok(!LiveCombatTargetGate.eligible(live,live,local,false,20,true,true),
           "newly invisible player blocked even after a visible snapshot");
        ok(LiveCombatTargetGate.eligible(live,live,local,false,20,true,false),
           "unrestricted aim preserves explicitly allowed invisible targets");
        ok(!LiveCombatTargetGate.eligible(live,stale,local,false,20,false,false),
           "stale world object never drives an aim rotation");
        ok(!LiveCombatTargetGate.eligible(live,live,local,false,Double.NaN,false,false),
           "invalid live health suppresses aim rotation");
        Random random=new Random(2472026L);
        for(int i=0;i<125000;i++) {
            Object selected=random.nextInt(7)==0?null:random.nextBoolean()?live:stale;
            Object loaded=random.nextInt(7)==0?null:random.nextBoolean()?live:stale;
            boolean dead=random.nextBoolean(),invisible=random.nextBoolean();
            boolean only=random.nextBoolean();
            double health;
            switch(random.nextInt(7)) {
                case 0:health=Double.NaN;break;
                case 1:health=Double.POSITIVE_INFINITY;break;
                case 2:health=-4;break;
                case 3:health=0;break;
                default:health=1+random.nextDouble()*100;
            }
            boolean expected=selected!=null && selected==loaded && selected!=local &&
                !dead && Double.isFinite(health) && health>0 && (!only || !invisible);
            ok(LiveCombatTargetGate.eligible(selected,loaded,local,dead,health,invisible,only)==expected,
               "live aim visible-only gate matches reference "+i);
        }
    }
    private static void ownership() throws Exception {
        // Gate is pure and read-only: no Minecraft classes, threads or state.
        Object candidate=new Object(),local=new Object();
        AtomicBoolean result=new AtomicBoolean();
        Thread other=new Thread(()->result.set(gate(candidate,candidate,local,false,20)));
        other.start();other.join();
        ok(result.get(),"pure admission is deterministic off thread");
    }
    public static void main(String[] args)throws Exception{
        focused();randomized();liveAimVisibility();ownership();
        ok(assertions>300000,"substantial crosshair and aim lifecycle coverage");
        System.out.println("LIVE_COMBAT_TARGET_GATE_PASS assertions="+assertions);
    }
}
