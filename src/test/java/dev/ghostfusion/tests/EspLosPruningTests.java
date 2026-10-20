package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspNearestBudget;
import dev.ghostfusion.features.EspDrawLimit;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original nearest-80 oracle vs. LOS-pruned candidate admission. */
public final class EspLosPruningTests {
    private static int checks;
    private static void ok(boolean pass,String why) {
        checks++;
        if(!pass)throw new AssertionError(why);
    }
    private static final class Player {
        final int id;
        final double distance;
        final boolean visible,geometry;
        Player(int id,double distance,boolean visible,boolean geometry){
            this.id=id;this.distance=distance;this.visible=visible;this.geometry=geometry;
        }
    }
    private static final class Trial {
        final EspNearestBudget<Player> accepted=new EspNearestBudget<Player>(80);
        int rays,geometryTests;
        void offer(Player p,boolean pruning,double maxSq){
            // Mirrors the real Forge pre-LOS and geometry admission contracts.
            if(!Double.isFinite(p.distance)||p.distance<0||p.distance>maxSq)return;
            if(pruning && !accepted.couldEnter(p.id,p.distance))return;
            rays++;
            if(!p.visible)return;
            geometryTests++;
            if(!p.geometry)return;
            accepted.offer(p,p.id,p.distance);
        }
    }
    private static void equal(Trial original,Trial optimized,String label) {
        ok(original.accepted.size()==optimized.accepted.size(),
            label+" selected nearest player count");
        for(int i=0;i<original.accepted.size();i++){
            ok(original.accepted.idAt(i)==optimized.accepted.idAt(i),
               label+" stable selected ID/order");
            ok(original.accepted.distanceAt(i)==optimized.accepted.distanceAt(i),
               label+" exact squared distance");
            ok(original.accepted.get(i)==optimized.accepted.get(i),
               label+" same original source object");
        }
        ok(optimized.rays<=original.rays,label+" never more LOS rays");
        ok(optimized.geometryTests<=original.geometryTests,
           label+" never more geometry checks");
        // A reduced render draw limit must still return the closest subset,
        // including reversed filled-style painter order.
        for(int configured:new int[]{1,5,24,80}){
            int draws=EspDrawLimit.count(configured,original.accepted.size());
            for(int i=0;i<draws;i++)for(boolean filled:new boolean[]{false,true}){
                int index=EspDrawLimit.slot(i,draws,filled);
                ok(original.accepted.get(index)==optimized.accepted.get(index),
                   label+" selected draw budget parity");
            }
        }
    }
    private static void guaranteedBenefit(){
        Trial old=new Trial(),reduced=new Trial();
        for(int i=0;i<300;i++) {
            Player p=new Player(i,1+i,true,true);
            old.offer(p,false,8000);
            reduced.offer(p,true,8000);
        }
        equal(old,reduced,"sorted crowded scene");
        ok(old.rays==300&&reduced.rays==80,
           "LOS on 220 impossible faraway players eliminated");
        old.accepted.clear();reduced.accepted.clear();
        ok(old.accepted.size()==0&&reduced.accepted.size()==0,
           "budget releases references at end of render");
    }
    private static void randomized(){
        Random rng=new Random(2272026L);
        int totalOriginalRays=0,totalPrunedRays=0;
        for(int frame=0;frame<5000;frame++){
            int count=80+rng.nextInt(241);
            List<Player> scene=new ArrayList<Player>();
            for(int i=0;i<count;i++) {
                // Every ninth input uses a repeated entity ID; duplicate
                // ordering and nearer replacement remain source-dependent.
                int id=i%9==0?i/9:i;
                double dist=(i%29==0?((i*11)%61):
                    rng.nextInt(85)+rng.nextDouble());
                if(i%101==0)dist=Double.NaN;
                if(i%127==0)dist=Double.POSITIVE_INFINITY;
                if(i%137==0)dist=-1;
                boolean visible=i%7!=0;
                boolean geometry=i%11!=0;
                scene.add(new Player(id,dist,visible,geometry));
            }
            Collections.shuffle(scene,new Random(rng.nextLong()));
            Trial old=new Trial(),filtered=new Trial();
            for(Player player:scene){
                old.offer(player,false,80.0);
                filtered.offer(player,true,80.0);
            }
            equal(old,filtered,"randomized scene "+frame);
            totalOriginalRays+=old.rays;
            totalPrunedRays+=filtered.rays;
            old.accepted.clear();filtered.accepted.clear();
        }
        ok(totalPrunedRays<=totalOriginalRays,
           "realistic crowded LOS count never increases");
    }
    private static void owner(){
        final EspNearestBudget<String> b=new EspNearestBudget<String>(80);
        b.offer("existing",2,4);
        final AtomicBoolean rejected=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try{b.couldEnter(1,1);}
            catch(IllegalStateException expected){rejected.set(true);}
        });
        other.start();
        try{other.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(rejected.get(),"owner thread fence for new read-only preflight");
        ok(b.size()==1,"blocked cross-thread read has no side effects");
    }
    public static void main(String[] args) {
        guaranteedBenefit();randomized();owner();
        ok(checks>400000,"seeded original-vs-pruned selection coverage");
        System.out.println("ESP_LOS_PRUNING_PASS assertions="+checks);
    }
}
