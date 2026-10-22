package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.TargetHudTeamPin;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.services.WorldSnapshotAdmission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/** R2.68: prevent crowded teammates from evicting every HUD enemy. */
public final class TargetHudCrowdedTeamPinTests {
    private static long checks;
    private static void ok(boolean pass,String label) {
        checks++;if(!pass)throw new AssertionError(label);
    }
    private static final class Player {
        final int id;
        final double distance;
        final boolean visible,teammate;
        Player(int id,double distance,boolean visible,boolean teammate) {
            this.id=id;this.distance=distance;
            this.visible=visible;this.teammate=teammate;
        }
    }
    private static WorldSnapshot snapshot(WorldSnapshotAdmission<Player> admission,
                                          boolean withPin,int tick){
        List<WorldSnapshot.Entity> entities=new ArrayList<WorldSnapshot.Entity>();
        WorldSnapshotAdmission.Visitor<Player> visitor=(p,visible)->{
            entities.add(new WorldSnapshot.Entity(p.id,p.distance,0,0,
                20,true,visible,true));
        };
        if(withPin)admission.forEachWithPinned(visitor);
        else admission.forEach(visitor);
        return new WorldSnapshot(1,tick,0,0,0,0,0,entities);
    }
    private static void edges(){
        TargetHudTeamPin pin=new TargetHudTeamPin();
        ok(!pin.hasBest(),"no stale target before admission");
        boolean rejected=false;
        try{pin.bestId();}catch(IllegalStateException expected){rejected=true;}
        ok(rejected,"must not reuse an ID across sessions");
        ok(!pin.offer(1,-1,true,false,64),"negative distance rejected");
        ok(!pin.offer(1,Double.NaN,true,false,64),"nan rejected");
        ok(!pin.offer(1,Double.POSITIVE_INFINITY,true,false,64),"infinite rejected");
        ok(!pin.offer(1,4,true,false,Double.NaN),"nan maximum rejected");
        ok(!pin.offer(1,4,true,false,Double.POSITIVE_INFINITY),"infinite maximum rejected");
        ok(!pin.offer(1,4,true,false,0),"zero maximum rejected");
        ok(!pin.offer(1,81,true,false,64),"out of range rejected");
        ok(!pin.offer(1,4,false,true,64),"occluded cannot pin for visible HUD");
        ok(!pin.hasBest(),"ineligible candidates never become pinned");
        ok(pin.offer(40,64,true,true,64),"inclusive range candidate admitted");
        ok(!pin.offer(42,64,true,true,64),"same range worse ID rejected");
        ok(pin.offer(39,64,true,true,64),"lower ID wins distance tie");
        ok(pin.offer(90,16,true,true,64),"closer player wins regardless of ID");
        ok(!pin.offer(1,25,true,true,64),"farther player never replaces closest");
        ok(pin.bestId()==90,"closest deterministic ID retained");
        pin.clear();
        ok(!pin.hasBest(),"world transition clears primitive pin state");
        ok(pin.offer(4,9,false,false,64),"unfiltered HUD can pin non-visible player");
        pin.clear();
    }
    private static void crowded() {
        WorldSnapshotAdmission<Player> admission=new WorldSnapshotAdmission<Player>();
        TargetHudTeamPin pin=new TargetHudTeamPin();
        TargetHudPolicy target=new TargetHudPolicy();
        List<Player> candidates=new ArrayList<Player>();
        for(int id=1;id<=350;id++)
            candidates.add(new Player(id,1+id*.01,true,true));
        for(int id=1001;id<=1350;id++)
            candidates.add(new Player(id,1+(id-1000)*.01,false,true));
        // Out of the visible top-256, but still inside the Target HUD range.
        final int enemy=9999;
        candidates.add(new Player(enemy,7,true,false));
        IntPredicate nonTeammate=id->id==enemy;
        Random rng=new Random(2682026L);
        for(int trial=0;trial<90;trial++) {
            Collections.shuffle(candidates,rng);
            admission.clear();pin.clear();
            for(Player p:candidates) {
                if(!p.teammate && pin.offer(p.id,p.distance*p.distance,
                    p.visible,true,24*24))
                    ok(admission.pin(p,p.id,p.distance*p.distance,p.visible),
                        "closest enemy can reserve one bounded pin");
                ok(admission.offer(p,p.id,p.distance*p.distance,p.visible) ||
                    admission.size()<=512,"bounded nearest admission");
            }
            ok(admission.size()==512,"two 256 slots filled by closer teammates");
            ok(admission.sizeWithPinned()==513,"exact one extra reserved candidate");
            ok(target.select(snapshot(admission,false,2*trial+1),24,true,
                "nearest",null,nonTeammate)==null,
                "reproduce the crowded no-enemy HUD defect");
            WorldSnapshot withPin=snapshot(admission,true,2*trial+2);
            ok(withPin.entities().size()==513,
                "snapshot respects original 513 player upper bound");
            ok(target.select(withPin,24,true,"nearest",null,nonTeammate).id==enemy,
                "team-filtered nearest can find the admitted enemy");
            ok(target.select(withPin,24,true,"crosshair",enemy,nonTeammate).id==enemy,
                "crosshair target remains compatible with shared pin");
            ok(target.select(withPin,24,true,"nearest",null).id!=enemy,
                "legacy nearest still uses the actual closest player");
        }
        pin.clear();admission.clear();
        ok(!pin.hasBest()&&admission.sizeWithPinned()==0,
            "no EntityPlayer references or pinned IDs held after clear");
    }
    private static void randomized(){
        Random rng=new Random(12682026L);
        TargetHudTeamPin pin=new TargetHudTeamPin();
        for(int frame=0;frame<60000;frame++){
            pin.clear();
            int count=2+rng.nextInt(33);
            int expectedId=0;
            double best=Double.POSITIVE_INFINITY;
            boolean any=false;
            boolean visibleOnly=rng.nextBoolean();
            double range=4+rng.nextInt(20);
            for(int i=0;i<count;i++){
                int id=rng.nextInt(400)-200;
                double distance=rng.nextInt(200)*0.125;
                double sq=distance*distance;
                boolean visible=rng.nextBoolean();
                boolean teammate=rng.nextInt(3)==0;
                // Admission excludes teammates first; shared consumers may
                // still admit them to their own nearest budgets.
                if(teammate)continue;
                boolean qualifies=sq<=range*range&&(!visibleOnly||visible);
                boolean better=qualifies&&(!any||sq<best||
                    (sq==best&&id<expectedId));
                boolean changed=pin.offer(id,sq,visible,visibleOnly,range*range);
                ok(changed==better,"strict independent nearest oracle "+frame+":"+i);
                if(better){expectedId=id;best=sq;any=true;}
            }
            ok(pin.hasBest()==any,"visibility and team candidate count "+frame);
            if(any)ok(pin.bestId()==expectedId,"closest stable ID "+frame);
        }
        pin.clear();
        ok(!pin.hasBest(),"no stale best ID after repeated world clears");
    }
    public static void main(String[] args){
        edges();crowded();randomized();
        ok(checks>500000,"randomized crowded candidate tracking coverage");
        System.out.println("TARGET_HUD_CROWDED_TEAM_PIN_PASS assertions="+checks);
    }
}
