package dev.ghostfusion.tests;

import dev.ghostfusion.services.SnapshotDemand;
import dev.ghostfusion.services.WorldSnapshotAdmission;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.AimAssistPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** R2.15: no unnecessary world scans/rays, preserved eligible targets. */
public final class SnapshotDemandTests {
    private static int assertions;
    private static void ok(boolean answer,String message){
        assertions++;
        if(!answer)throw new AssertionError(message);
    }
    private static void invalid(Runnable action,String why){
        boolean threw=false;
        try{action.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,why);
    }
    private static void matrix(){
        for(int flags=0;flags<16;flags++){
            boolean hud=(flags&1)!=0;
            boolean aim=(flags&2)!=0;
            boolean hudVisible=(flags&4)!=0;
            boolean aimVisible=(flags&8)!=0;
            SnapshotDemand d=SnapshotDemand.of(hud,hudVisible,24,
                                               aim,aimVisible,6);
            boolean visible=(hud&&hudVisible)||(aim&&aimVisible);
            boolean unrestricted=(hud&&!hudVisible)||(aim&&!aimVisible);
            ok(d.needed()==(hud||aim),"only live consumers need entities");
            ok(d.needVisible==visible,"visible requirement across 16 modes");
            ok(d.needUnfiltered==unrestricted,"unrestricted requirement across 16 modes");
            double v=(hud&&hudVisible)?24:(aim&&aimVisible)?6:0;
            double u=(hud&&!hudVisible)?24:(aim&&!aimVisible)?6:0;
            ok(d.visibleDistanceSq==v*v,"per-consumer visible radius "+flags);
            ok(d.unfilteredDistanceSq==u*u,"per-consumer unfiltered radius "+flags);
            ok(d.testVisibility(36)==(v>=6),"ray-gate at six blocks "+flags);
            ok(d.testVisibility(576)==(v>=24),"ray-gate at 24 blocks "+flags);
            ok(d.admit(false,576)==(u>=24),"hidden demand independently bounded "+flags);
            ok(d.admit(true,576)==(Math.max(v,u)>=24),"visible candidate cap "+flags);
            ok(d.testVisibility()==visible,"LOS only when visible-only query needs it");
            ok(d.maximumDistanceSq==(hud?576.0:aim?36.0:0.0),
               "range bounded to active feature");
            ok(d.accepts(0.0)==(hud||aim),"origin range accepted only with consumer");
            ok(d.accepts(36.0)==(hud||aim),"six block range");
            ok(d.accepts(576.0)==hud,"24 blocks only for active HUD");
            ok(!d.accepts(577.0),"out of range rejected before ray");
            ok(!d.accepts(Double.NaN),"nonfinite squared distance");
            ok(!d.accepts(Double.POSITIVE_INFINITY),"infinite squared distance");
            ok(!d.accepts(-1),"negative distance");
            ok(d.admit(true)==(visible||unrestricted),"visible relevance");
            ok(d.admit(false)==unrestricted,"hidden relevance");
        }
        invalid(()->SnapshotDemand.of(true,true,Double.NaN,false,true,6),
                "enabled HUD cannot have invalid range");
        invalid(()->SnapshotDemand.of(false,true,8,true,false,6.1),
                "enabled aim rejects range out of bounds");
        SnapshotDemand disabled=SnapshotDemand.of(false,true,Double.NaN,false,true,-20);
        ok(!disabled.needed(),"inactive consumer settings never poison publication");
    }
    private static final class Entity {
        final int id;
        final double z;
        final boolean visible;
        Entity(int id,double z,boolean visible){this.id=id;this.z=z;this.visible=visible;}
    }
    private static final class Result {
        final WorldSnapshot snapshot;
        final int visited,visibilityRays;
        Result(WorldSnapshot w,int examined,int rays){
            snapshot=w;visited=examined;visibilityRays=rays;
        }
    }
    private static Result collect(List<Entity> loaded,SnapshotDemand demand,long tick){
        WorldSnapshotAdmission<Entity> selection=new WorldSnapshotAdmission<Entity>();
        int examined=0,rays=0;
        try{
            if(demand.needed())for(Entity e:loaded){
                examined++;
                double ds=e.z*e.z;
                if(!demand.accepts(ds))continue;
                boolean visible=false;
                if(demand.testVisibility(ds)){
                    rays++;
                    visible=e.visible;
                }
                if(!demand.admit(visible,ds))continue;
                selection.offer(e,e.id,ds,visible);
            }
            List<WorldSnapshot.Entity> objects=
                new ArrayList<WorldSnapshot.Entity>(selection.size());
            selection.forEach(new WorldSnapshotAdmission.Visitor<Entity>(){
                public void visit(Entity e,boolean visible){
                    objects.add(new WorldSnapshot.Entity(e.id,0,0,e.z,20,true,visible,true));
                }
            });
            return new Result(new WorldSnapshot(1,tick,0,0,0,0,0,objects),examined,rays);
        }finally{selection.clear();}
    }
    private static void crowdedConsumers(){
        List<Entity> scene=new ArrayList<Entity>();
        for(int i=0;i<450;i++)scene.add(new Entity(i,1.1+i*.0002,false));
        for(int i=0;i<450;i++)scene.add(new Entity(1000+i,3.1+i*.0002,true));
        for(int i=0;i<450;i++)scene.add(new Entity(2000+i,200+i,true));
        SnapshotDemand none=SnapshotDemand.of(false,true,8,false,true,4);
        Result empty=collect(scene,none,1);
        ok(empty.visited==0&&empty.visibilityRays==0&&empty.snapshot.entities().isEmpty(),
           "no HUD/aim modules: publish empty tick snapshot with zero player traversal");
        SnapshotDemand unrestricted=SnapshotDemand.of(true,false,24,false,true,4);
        Result normal=collect(scene,unrestricted,2);
        ok(normal.visited==scene.size()&&normal.visibilityRays==0,
           "unrestricted-only targeting makes zero LOS queries");
        ok(normal.snapshot.entities().size()==256,
           "single unfiltered partition bounded to 256");
        ok(new TargetHudPolicy().select(normal.snapshot,24,false).id==0,
           "unfiltered HUD keeps closest hidden candidate");
        SnapshotDemand onlyVisible=SnapshotDemand.of(true,true,24,false,true,4);
        Result visible=collect(scene,onlyVisible,3);
        ok(visible.visibilityRays==900,"far-away players rejected before visibility ray");
        ok(visible.snapshot.entities().size()==256,
           "visible-only snapshot holds only 256 visible candidates");
        ok(new TargetHudPolicy().select(visible.snapshot,24,true).id==1000,
           "visible HUD cannot be starved by 450 nearer hidden players");
        SnapshotDemand mixed=SnapshotDemand.of(true,false,24,true,true,6);
        Result both=collect(scene,mixed,4);
        ok(both.visibilityRays==900,"mixed policy rays only for visible feature's six-block range");
        ok(both.snapshot.entities().size()==512,
           "mixed policy supports 256 visible and 256 hidden");
        ok(new TargetHudPolicy().select(both.snapshot,24,false).id==0,
           "mixed HUD preserves nearest occluded result");
        AimAssistPolicy.Suggestion aim=new AimAssistPolicy().suggest(
            both.snapshot,1.62,1.25,6,75,10,true);
        ok(aim!=null&&aim.entityId>=1000,
           "visible-only aim still sees nearest eligible visible targets");
        SnapshotDemand aimOnly=SnapshotDemand.of(false,true,24,true,true,6);
        Result aimResult=collect(scene,aimOnly,5);
        ok(aimResult.visibilityRays==900,"aim-only range 6 excludes distant targets");
        ok(aimResult.snapshot.entities().size()==256,
           "aim-only visible admission uses one partition");
    }
    private static void asymmetricRadii(){
        List<Entity> players=new ArrayList<Entity>();
        for(int id=0;id<200;id++){
            players.add(new Entity(id,4.0,true));
            players.add(new Entity(id+1000,12.0,false));
            players.add(new Entity(id+2000,18.0,true));
        }
        SnapshotDemand distantHud=SnapshotDemand.of(true,false,24,true,true,6);
        Result r=collect(players,distantHud,10);
        ok(r.visited==600 && r.visibilityRays==200,
           "only visible-only aim radius triggers LOS in mixed HUD24/aim6");
        ok(r.snapshot.entities().size()==456,
           "200 visible near + 256 unfiltered retained (near and mid)");
        ok(new TargetHudPolicy().select(r.snapshot,24,false).id==0,
           "unfiltered HUD still selects closest player");
        AimAssistPolicy.Suggestion aim=new AimAssistPolicy().suggest(
            r.snapshot,1.62,1.25,6,75,10,true);
        ok(aim!=null && aim.entityId<200,
           "aim only sees visible candidates inside six-block radius");
        SnapshotDemand distantVisible=SnapshotDemand.of(true,true,24,true,false,6);
        Result flipped=collect(players,distantVisible,11);
        ok(flipped.visibilityRays==600,
           "visible HUD24 requires LOS farther than unfiltered aim6");
        ok(flipped.snapshot.entities().size()<=512,
           "independent partition budget preserved after range split");
        ok(new TargetHudPolicy().select(flipped.snapshot,24,true).id==0,
           "visible HUD selection unchanged by range split");
        ok(!distantVisible.admit(false,144),
           "hidden 12-block player excluded when only unfiltered aim6 needs hidden");
        ok(distantVisible.admit(true,144),
           "visible 12-block player remains eligible for visible HUD24");
    }
    private static void seededRanges(){
        Random random=new Random(2152026L);
        for(int i=0;i<18000;i++){
            boolean hud=random.nextBoolean(),aim=random.nextBoolean();
            boolean hv=random.nextBoolean(),av=random.nextBoolean();
            double range=2+random.nextDouble()*22;
            double arange=2+random.nextDouble()*4;
            SnapshotDemand d=SnapshotDemand.of(hud,hv,range,aim,av,arange);
            double expected=Math.max(hud?range:0,aim?arange:0);
            ok(d.maximumDistanceSq==expected*expected,"active-range aggregation "+i);
            double visible=Math.max(hud&&hv?range:0,aim&&av?arange:0);
            double plain=Math.max(hud&&!hv?range:0,aim&&!av?arange:0);
            ok(d.visibleDistanceSq==visible*visible,"exact visible range "+i);
            ok(d.unfilteredDistanceSq==plain*plain,"exact unfiltered range "+i);
            double ds=random.nextDouble()*700;
            ok(d.accepts(ds)==(d.needed()&&ds<=expected*expected),
               "exact admission radius "+i);
            ok(!d.testVisibility()||d.needed(),
               "no visibility work without a consumer "+i);
            ok(d.testVisibility(ds)==(d.needVisible && ds<=visible*visible),
               "LOS checks constrained to visible consumer's radius "+i);
            ok(d.admit(false,ds)==(d.needUnfiltered && ds<=plain*plain),
               "hidden candidate constrained to unfiltered radius "+i);
            ok(d.admit(true,ds)==(d.needed() && ds<=expected*expected),
               "visible candidate eligible in either active radius "+i);
            ok(!d.admit(false)||d.needUnfiltered,
               "hidden admission always requires unfiltered consumer "+i);
            ok(!d.admit(true)||d.needed(),
               "visible admission requires consumer "+i);
        }
    }
    public static void main(String[] args){
        matrix();crowdedConsumers();asymmetricRadii();seededRanges();
        ok(assertions>85000,"wide policy and crowded-world regression suite");
        System.out.println("SNAPSHOT_DEMAND_PASS assertions="+assertions);
    }
}
