package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspNearestBudget;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.AimAssistPolicy;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.13: bounded nearest-256 selection and immutable HUD/aim input parity. */
public final class NearestSnapshotTests {
    private static int checks;
    private static void ok(boolean pass,String message) {
        checks++;if(!pass)throw new AssertionError(message);
    }
    private static WorldSnapshot build(List<WorldSnapshot.Entity> loaded,long tick) {
        EspNearestBudget<WorldSnapshot.Entity> selected =
            new EspNearestBudget<WorldSnapshot.Entity>(256);
        try {
            for(WorldSnapshot.Entity player:loaded) {
                if(!player.alive || player.health<=0)continue;
                double ds=player.x*player.x+player.y*player.y+player.z*player.z;
                selected.offer(player,player.id,ds);
            }
            List<WorldSnapshot.Entity> entities =
                new ArrayList<WorldSnapshot.Entity>(selected.size());
            for(int i=0;i<selected.size();i++)entities.add(selected.get(i));
            return new WorldSnapshot(1,tick,0,0,0,0,0,entities);
        } finally {selected.clear();}
    }
    private static void priorities() {
        List<WorldSnapshot.Entity> players=new ArrayList<WorldSnapshot.Entity>();
        for(int id=0;id<700;id++) {
            double z=id<600?1000+id:id-598;
            players.add(new WorldSnapshot.Entity(id,0,0,z,20,true,true,true));
        }
        WorldSnapshot base=build(players,1);
        ok(base.entities().size()==256,"snapshot bounded to 256");
        ok(base.entities().get(0).id==600,"nearby player admitted even though last loaded");
        ok(base.entities().get(1).id==601,"nearest-first stable order");
        ok(base.entities().get(99).id==699,"last 100 near players included");
        ok(new TargetHudPolicy().select(base,24,true).id==600,
           "Target HUD sees nearest target after capped admission");
        AimAssistPolicy.Suggestion suggestion=
            new AimAssistPolicy().suggest(base,1.62,1.25,6,75,10,true);
        ok(suggestion!=null && suggestion.entityId>=600,
           "aim policy gets nearby candidate despite 600 far decoys");

        Random r=new Random(2132026L);
        for(int seed=0;seed<128;seed++) {
            Collections.shuffle(players,r);
            WorldSnapshot candidate=build(players,seed+2);
            ok(candidate.entities().size()==256,"stable cap");
            for(int i=0;i<256;i++)
                ok(candidate.entities().get(i).id==base.entities().get(i).id,
                   "order independent of loaded entity list "+seed+"/"+i);
            ok(new TargetHudPolicy().select(candidate,24,true).id==600,
               "Target HUD remains stable across list shuffles "+seed);
        }
    }
    private static void invalidAndTies() {
        EspNearestBudget<String> budget=new EspNearestBudget<String>(256);
        ok(!budget.offer("nan",1,Double.NaN),"nonfinite distance ignored");
        ok(!budget.offer("negative",2,-4),"negative distance ignored");
        ok(!budget.offer(null,3,1),"null candidate ignored");
        for(int i=512;i>=0;i--)budget.offer("e"+i,i,i%9);
        ok(budget.size()==256,"candidate cap holds in crowded scene");
        for(int i=1;i<budget.size();i++) {
            double prev=budget.distanceAt(i-1),curr=budget.distanceAt(i);
            ok(prev<curr||(prev==curr&&budget.idAt(i-1)<budget.idAt(i)),
               "stable distance then id ties");
        }
        final AtomicBoolean refused=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try {budget.offer("other",2000,0);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        foreign.start();
        try{foreign.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(refused.get(),"owner-thread-only snapshot buffer");
        budget.clear();
        ok(budget.size()==0,"world switch releases all raw references");
        boolean outOfBounds=false;
        try{budget.get(0);}catch(IndexOutOfBoundsException expected){outOfBounds=true;}
        ok(outOfBounds,"old world object not exposed after clear");
        ok(budget.offer("new world",8000,1)&&budget.size()==1,
           "buffer reusable after world transition");
        budget.clear();
    }
    private static void churn() {
        EspNearestBudget<Integer> b=new EspNearestBudget<Integer>(256);
        Random r=new Random(20261020L);
        for(int frame=0;frame<250;frame++) {
            b.clear();
            for(int i=0;i<500;i++) {
                int id=i;
                double ds=r.nextDouble()*10000;
                b.offer(id,id,ds);
                ok(b.size()<=256,"no unbounded snapshots "+frame);
            }
            ok(b.size()==256,"nearest 256 filled");
        }
        b.clear();
        ok(b.size()==0,"buffer cleared after synthetic 250-tick churn");
    }
    public static void main(String[] args) {
        priorities();invalidAndTies();churn();
        ok(checks>150000,"substantial nearest snapshot replay");
        System.out.println("NEAREST_SNAPSHOT_PASS assertions="+checks);
    }
}
