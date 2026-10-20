package dev.ghostfusion.tests;

import dev.ghostfusion.features.EspDrawLimit;
import dev.ghostfusion.features.EspNearestBudget;
import dev.ghostfusion.features.EspDistanceFade;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.25 original nearest-80 membership, configurable drawing and ray gates. */
public final class EspDrawLimitTests {
    private static int checks;
    private static void ok(boolean success,String reason) {
        checks++;if(!success)throw new AssertionError(reason);
    }
    private static void reject(Runnable call,String reason) {
        boolean thrown=false;
        try{call.run();}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,reason);
    }
    private static void edges() {
        ok(EspDrawLimit.count(80,0)==0,"empty draw list");
        ok(EspDrawLimit.count(80,80)==80,"default existing 80 maximum");
        ok(EspDrawLimit.count(80,5)==5,"default does not omit valid players");
        ok(EspDrawLimit.count(1,80)==1,"smallest configured draw cap");
        ok(EspDrawLimit.count(24,80)==24,"partial nearest player cap");
        ok(EspDrawLimit.count(24,12)==12,"cap cannot exceed available");
        ok(EspDrawLimit.slot(0,1,true)==0,"single filled player");
        ok(EspDrawLimit.slot(0,4,true)==3,"filled far first within cap");
        ok(EspDrawLimit.slot(3,4,true)==0,"filled near last within cap");
        ok(EspDrawLimit.slot(0,4,false)==0,"wire near first");
        ok(EspDrawLimit.slot(3,4,false)==3,"wire far last");
        reject(()->EspDrawLimit.count(0,80),"zero configured invalid");
        reject(()->EspDrawLimit.count(81,80),"configured above physical budget");
        reject(()->EspDrawLimit.count(10,-1),"negative available rejected");
        reject(()->EspDrawLimit.count(10,81),"available above nearest-80 rejected");
        reject(()->EspDrawLimit.slot(-1,2,true),"negative slot rejected");
        reject(()->EspDrawLimit.slot(2,2,true),"out-of-range slot rejected");
        reject(()->EspDrawLimit.slot(0,0,false),"empty slot rejected");
        reject(()->EspDrawLimit.slot(0,81,false),"slot above budget rejected");
    }
    private static void randomized() {
        Random random=new Random(2252026L);
        for(int frame=0;frame<9000;frame++) {
            final int config=1+random.nextInt(80);
            final int n=random.nextInt(300);
            List<Integer> ids=new ArrayList<Integer>();
            for(int i=0;i<n;i++)ids.add(i);
            Collections.shuffle(ids,random);
            double[] distances=new double[n];
            for(int id=0;id<n;id++)
                distances[id]=((id*47)%73)+random.nextDouble();
            EspNearestBudget<Integer> chosen=new EspNearestBudget<Integer>(80);
            for(int id:ids)chosen.offer(id,id,distances[id]);
            final int take=EspDrawLimit.count(config,chosen.size());
            ok(take==Math.min(config,Math.min(80,n)),"count clamps correctly");
            for(boolean filled:new boolean[]{false,true}) {
                boolean[] seen=new boolean[n];
                int previousSlot=filled?take:-1;
                for(int i=0;i<take;i++){
                    int slot=EspDrawLimit.slot(i,take,filled);
                    ok(slot>=0&&slot<take,"slot always from nearest subset");
                    ok(filled?slot<previousSlot:slot>previousSlot,
                       "stable painter order by style");
                    previousSlot=slot;
                    int id=chosen.get(slot);
                    ok(!seen[id],"every target rendered at most once");
                    seen[id]=true;
                    ok(chosen.distanceAt(slot)==distances[id],
                       "render fade receives exactly admitted squared distance");
                    float fade=EspDistanceFade.factor(chosen.distanceAt(slot),
                        80,true,.5,.25);
                    ok(Float.isFinite(fade)&&fade>=.25f&&fade<=1f,
                       "read-only fade stays valid for selected budget");
                }
                for(int j=0;j<take;j++)ok(seen[chosen.idAt(j)],
                    "every selected top-N target included");
                for(int j=take;j<chosen.size();j++)ok(!seen[chosen.idAt(j)],
                    "nonselected farther player receives no GL work");
            }
            chosen.clear();
            ok(chosen.size()==0,"no retained players after render");
        }
    }
    private static void persistence()throws Exception{
        Path root=Files.createTempDirectory("ghostc-r225-draw-budget");
        HostRuntime h=new HostRuntime(root);
        ok(h.espMaxPlayers()==80,"old profile defaults to full 80");
        h.uiActions().fraction("render.esp","max_players",0);
        ok(h.espMaxPlayers()==1,"lowest slider value one");
        h.uiActions().fraction("render.esp","max_players",.5);
        ok(h.espMaxPlayers()>=40&&h.espMaxPlayers()<=41,
           "slider controls mid-sized budget");
        h.uiActions().fraction("render.esp","max_players",1);
        ok(h.espMaxPlayers()==80,"maximum returns to original");
        h.uiActions().fraction("render.esp","max_players",0);
        h.persist();h.close();
        HostRuntime loaded=new HostRuntime(root);
        ok(loaded.espMaxPlayers()==1,"budget persists without profile migration");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try{loaded.espMaxPlayers();}
            catch(IllegalStateException expected){blocked.set(true);}
        });
        other.start();other.join();
        ok(blocked.get(),"draw budget is owner-thread fenced");
        loaded.close();
    }
    public static void main(String[] args)throws Exception{
        edges();randomized();persistence();
        ok(checks>90000,"substantial randomized budget and painter order replay");
        System.out.println("ESP_DRAW_LIMIT_PASS assertions="+checks);
    }
}
