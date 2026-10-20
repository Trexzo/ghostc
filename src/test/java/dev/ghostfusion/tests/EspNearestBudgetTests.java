package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspNearestBudget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fixed-memory nearest-player selection independent of Minecraft types. */
public final class EspNearestBudgetTests {
    private static int checks;
    private static void ok(boolean value,String label){
        checks++;if(!value)throw new AssertionError(label);
    }
    private static void rejects(Runnable action,String label) {
        boolean threw=false;
        try {action.run();}catch(RuntimeException expected){threw=true;}
        ok(threw,label);
    }
    private static void basics() {
        rejects(()->new EspNearestBudget<String>(0),"zero capacity invalid");
        rejects(()->new EspNearestBudget<String>(257),"unbounded capacity invalid");
        ok(new EspNearestBudget<String>(256).size()==0,
            "256 snapshot capacity is legal without changing 80 ESP cap");
        EspNearestBudget<String> budget=new EspNearestBudget<String>(3);
        ok(budget.size()==0,"empty selection");
        ok(!budget.offer(null,1,3),"null player ignored");
        ok(!budget.offer("nan",2,Double.NaN),"nan distance ignored");
        ok(!budget.offer("negative",3,-1),"negative distance ignored");
        ok(!budget.offer("infinite",4,Double.POSITIVE_INFINITY),"infinite distance ignored");
        ok(budget.offer("far",4,40),"first player accepted");
        ok(budget.offer("near",6,1),"nearer player inserted ahead");
        ok(budget.offer("mid",2,10),"third player accepted");
        ok(!budget.offer("distant",20,41),"farther candidate rejected");
        ok(!budget.offer("dupe",6,0),"duplicate player ID rejected");
        ok(budget.offer("nearest",1,0),"nearest displaces last");
        ok(budget.size()==3,"bounded capacity");
        ok("nearest".equals(budget.get(0))&&"near".equals(budget.get(1))&&
            "mid".equals(budget.get(2)),"nearest order and eviction");
        ok(budget.distanceAt(0)==0&&budget.distanceAt(1)==1&&
            budget.distanceAt(2)==10,"distance ordering");
        rejects(()->budget.get(3),"bounds check");
        budget.clear();
        ok(budget.size()==0,"clear erases all admitted players");
        rejects(()->budget.get(0),"cleared reference cannot be retrieved");
        ok(budget.offer("fresh world",40,2) && budget.size()==1,
           "new world starts without previous candidates");
        budget.clear();
        budget.offer("id5",5,7);
        budget.offer("id1",1,7);
        budget.offer("id3",3,7);
        ok(budget.idAt(0)==1&&budget.idAt(1)==3&&budget.idAt(2)==5,
           "equal-distance tie-break deterministic by entity ID");
        final AtomicBoolean blocked=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try{budget.clear();}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(blocked.get(),"off-thread mutation rejected");
    }
    private static void orderIndependence() {
        final int CAP=80;
        Random rng=new Random(2122026L);
        for(int seed=0;seed<280;seed++) {
            List<Integer> ids=new ArrayList<Integer>();
            for(int i=0;i<320;i++)ids.add(i);
            // Stable distance profile with many ties and ID collisions in distance.
            final double[] distances=new double[ids.size()];
            for(int i=0;i<distances.length;i++)
                distances[i]=(i*17%53)+((i%5==0)?.5:0);
            EspNearestBudget<Integer> a=new EspNearestBudget<Integer>(CAP);
            for(int id:ids) a.offer(id,id,distances[id]);
            ok(a.size()==CAP,"capacity full "+seed);
            for(int i=1;i<a.size();i++) {
                double prev=a.distanceAt(i-1),cur=a.distanceAt(i);
                ok(prev<cur || (prev==cur && a.idAt(i-1)<a.idAt(i)),
                   "ordered with stable ties "+seed);
            }
            List<Integer> chosen=new ArrayList<Integer>();
            for(int i=0;i<a.size();i++)chosen.add(a.idAt(i));
            Collections.shuffle(ids,new Random(rng.nextLong()));
            EspNearestBudget<Integer> b=new EspNearestBudget<Integer>(CAP);
            for(int id:ids)b.offer(id,id,distances[id]);
            for(int i=0;i<CAP;i++)ok(b.idAt(i)==chosen.get(i),
               "same nearest players independent of loaded list ordering "+seed);
            b.clear();
            ok(b.size()==0,"clear after each frame "+seed);
        }
    }
    private static void stress() {
        EspNearestBudget<Integer> b=new EspNearestBudget<Integer>(80);
        Random random=new Random(801212L);
        for(int frame=0;frame<1000;frame++){
            b.clear();
            for(int i=0;i<180;i++){
                int id=i;
                double distance=random.nextInt(1000)+random.nextDouble();
                b.offer(id,id,distance);
                ok(b.size()<=80,"hard maximum across repeated worlds");
            }
            ok(b.size()==80,"all frames select 80");
            for(int i=1;i<b.size();i++)
                ok(b.distanceAt(i-1)<=b.distanceAt(i),"sorted frame");
        }
        b.clear();
        ok(b.size()==0,"no retained candidate after soak");
    }
    public static void main(String[] args){
        basics();orderIndependence();stress();
        ok(checks>220000,"substantial nearest player replay");
        System.out.println("ESP_NEAREST_BUDGET_PASS assertions="+checks);
    }
}
