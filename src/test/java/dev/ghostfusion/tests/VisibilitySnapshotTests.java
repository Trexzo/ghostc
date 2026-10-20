package dev.ghostfusion.tests;
import dev.ghostfusion.services.WorldSnapshotAdmission;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.features.AimAssistPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Visible-only nearest-256 population cannot be starved by hidden candidates. */
public final class VisibilitySnapshotTests {
    private static int checks;
    private static void ok(boolean good,String what){
        checks++;if(!good)throw new AssertionError(what);
    }
    private static final class Candidate {
        final int id;final double z;final boolean visible;
        Candidate(int id,double z,boolean visible){
            this.id=id;this.z=z;this.visible=visible;
        }
    }
    private static WorldSnapshot build(List<Candidate> candidates,long tick){
        WorldSnapshotAdmission<Candidate> b=new WorldSnapshotAdmission<Candidate>();
        try {
            for(Candidate c:candidates)b.offer(c,c.id,c.z*c.z,c.visible);
            final List<WorldSnapshot.Entity> selected=new ArrayList<WorldSnapshot.Entity>(b.size());
            b.forEach(new WorldSnapshotAdmission.Visitor<Candidate>(){
                public void visit(Candidate c,boolean visible){
                    selected.add(new WorldSnapshot.Entity(c.id,0,0,c.z,20,true,visible,true));
                }
            });
            return new WorldSnapshot(1,tick,0,0,0,0,0,selected);
        } finally {b.clear();}
    }
    private static void crowded() {
        List<Candidate> loaded=new ArrayList<Candidate>();
        for(int id=0;id<400;id++)loaded.add(new Candidate(id,1.0+id*.0001,false));
        for(int id=0;id<400;id++)loaded.add(new Candidate(1000+id,3.0+id*.0001,true));
        WorldSnapshot baseline=build(loaded,1);
        ok(baseline.entities().size()==512,"256 visible + 256 hidden");
        int v=0,h=0;
        for(WorldSnapshot.Entity e:baseline.entities()){
            if(e.visible)v++;else h++;
        }
        ok(v==256&&h==256,"both admission classes full");
        ok(new TargetHudPolicy().select(baseline,24,true).id==1000,
            "visible-only HUD cannot be crowded out");
        ok(new TargetHudPolicy().select(baseline,24,false).id==0,
            "unfiltered HUD still chooses nearest occluded");
        AimAssistPolicy.Suggestion suggestion=
            new AimAssistPolicy().suggest(baseline,1.62,1.25,6,75,10,true);
        ok(suggestion!=null&&suggestion.entityId>=1000,
            "visible-only Aim Assist receives real target");
        Random r=new Random(2142026L);
        for(int t=0;t<88;t++){
            Collections.shuffle(loaded,r);
            WorldSnapshot again=build(loaded,t+2);
            ok(again.entities().size()==baseline.entities().size(),"population stable");
            for(int j=0;j<baseline.entities().size();j++){
                WorldSnapshot.Entity a=baseline.entities().get(j),b=again.entities().get(j);
                ok(a.id==b.id&&a.visible==b.visible,
                    "visible/hidden nearest ordering stable after shuffle "+t+"/"+j);
            }
            ok(new TargetHudPolicy().select(again,24,true).id==1000,
                "Target HUD stable after reshuffle");
        }
    }
    private static void duplicatesAndOwners(){
        WorldSnapshotAdmission<String> a=new WorldSnapshotAdmission<String>();
        ok(a.offer("occluded",12,1,false),"hidden candidate admitted");
        ok(a.offer("visible",12,9,true),"visible replacement admitted");
        ok(a.size()==1&&a.visibleSize()==1&&a.hiddenSize()==0,
            "same ID not present twice");
        ok(!a.offer("occluded newer",12,.5,false),
            "hidden duplicate cannot displace visible");
        ok(a.offer("closer visible",12,4,true),
            "nearest duplicate wins within visible group");
        final List<String> values=new ArrayList<String>();
        a.forEach(new WorldSnapshotAdmission.Visitor<String>(){
            public void visit(String item,boolean visible){
                values.add((visible?"V":"H")+item);
            }
        });
        ok(values.size()==1 && "Vcloser visible".equals(values.get(0)),
            "closest visible duplicate wins");
        ok(!a.offer(null,15,2,false),"null candidate ignored");
        ok(!a.offer("nan",16,Double.NaN,true),"NaN distance rejected");
        ok(!a.offer("negative",17,-1,false),"negative distance rejected");
        AtomicBoolean blocked=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try {a.clear();}catch(IllegalStateException e){blocked.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException e){throw new AssertionError(e);}
        ok(blocked.get(),"owner thread required");
        a.clear();
        ok(a.size()==0,"world change clears both groups");
        ok(a.offer("new world",99,1,true),"new-world buffer reusable");
        a.clear();
        boolean rejected=false;
        try {a.forEach(null);}catch(IllegalArgumentException ex){rejected=true;}
        ok(rejected,"null visitor rejected");
    }
    private static void mixedReplay(){
        WorldSnapshotAdmission<Integer> b=new WorldSnapshotAdmission<Integer>();
        Random r=new Random(20261020L);
        for(int frame=0;frame<190;frame++){
            b.clear();
            for(int i=0;i<700;i++){
                b.offer(i,i,r.nextInt(1200)+r.nextDouble(),i%3==0);
                ok(b.size()<=512,"bounded throughout churn");
            }
            ok(b.visibleSize()<=256&&b.hiddenSize()<=256,
                "each class separately bounded");
            final int[] seen={0};
            b.forEach(new WorldSnapshotAdmission.Visitor<Integer>(){
                public void visit(Integer value,boolean visible){seen[0]++;}
            });
            ok(seen[0]==b.size(),"merged visitor returns all selected entries");
        }
        b.clear();
        ok(b.size()==0,"no retained entity references after replay");
    }
    public static void main(String[] args){
        crowded();duplicatesAndOwners();mixedReplay();
        ok(checks>175000,"substantial visibility target regression");
        System.out.println("VISIBILITY_SNAPSHOT_PASS assertions="+checks);
    }
}
