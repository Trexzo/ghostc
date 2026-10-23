package dev.ghostfusion.tests;

import dev.ghostfusion.services.WorldSnapshotAdmission;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.services.WorldSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R2.58: one optional crosshair target cannot be crowded out by nearest caps. */
public final class PinnedSnapshotTests {
    private static int checks;
    private static void ok(boolean valid,String what) {
        checks++;
        if(!valid)throw new AssertionError(what);
    }
    private static final class Candidate {
        final int id;
        final double dist;
        final boolean visible;
        Candidate(int id,double dist,boolean visible) {
            this.id=id;this.dist=dist;this.visible=visible;
        }
    }
    private static List<Candidate> sample() {
        List<Candidate> input=new ArrayList<Candidate>();
        for(int i=0;i<350;i++)
            input.add(new Candidate(i,1+i*.01,true));
        for(int i=0;i<350;i++)
            input.add(new Candidate(1000+i,1+i*.01,false));
        input.add(new Candidate(9000,5.5,true));
        return input;
    }
    private static WorldSnapshot snapshot(WorldSnapshotAdmission<Candidate> b,
                                           boolean pinned,long tick) {
        final List<WorldSnapshot.Entity> entities=new ArrayList<WorldSnapshot.Entity>(
            pinned?b.sizeWithPinned():b.size());
        WorldSnapshotAdmission.Visitor<Candidate> writer=new WorldSnapshotAdmission.Visitor<Candidate>() {
            public void visit(Candidate item,boolean visible) {
                entities.add(new WorldSnapshot.Entity(item.id,item.dist,0,0,
                    20,true,visible,true));
            }
        };
        if(pinned)b.forEachWithPinned(writer);
        else b.forEach(writer);
        return new WorldSnapshot(1,tick,0,0,0,0,0,entities);
    }
    private static void sorted(WorldSnapshot s) {
        int oldId=Integer.MIN_VALUE;
        double oldDist=-1;
        for(WorldSnapshot.Entity e:s.entities()){
            double dist=e.x;
            ok(dist>=oldDist,"output sorted by distance");
            if(dist==oldDist)ok(e.id>oldId,"equal distance sorted by ID");
            oldDist=dist;oldId=e.id;
        }
    }
    private static void crowdedPinnedTarget() {
        WorldSnapshotAdmission<Candidate> b=new WorldSnapshotAdmission<Candidate>();
        List<Candidate> input=sample();
        Random r=new Random(2582026L);
        for(int trial=0;trial<90;trial++) {
            b.clear();
            Collections.shuffle(input,r);
            for(Candidate c:input){
                if(c.id==9000)ok(b.pin(c,c.id,c.dist*c.dist,c.visible),
                    "valid crosshair player can be pinned");
                b.offer(c,c.id,c.dist*c.dist,c.visible);
            }
            ok(b.size()==512 && b.visibleSize()==256 && b.hiddenSize()==256,
                "both nearest-256 partitions remain full and unchanged");
            ok(b.sizeWithPinned()==513,"one overflow player adds exactly one slot");
            WorldSnapshot without=snapshot(b,false,trial*2+1);
            ok(without.entities().size()==512,"ordinary nearest snapshot remains 512");
            ok(new TargetHudPolicy().select(without,24,true,"crosshair",9000)==null,
                "reproduce missing reticle target outside nearest cap");
            WorldSnapshot with=snapshot(b,true,trial*2+2);
            ok(with.entities().size()==513,"pinned selection remains bounded at 513");
            ok(new TargetHudPolicy().select(with,24,true,"crosshair",9000).id==9000,
                "aimed-at valid player now included despite crowded scene");
            ok(new TargetHudPolicy().select(with,24,true).id==0,
                "default nearest HUD remains unchanged");
            sorted(with);
            ok(b.size()==512&&b.sizeWithPinned()==513,
                "pinning never evicts ordinary candidates");
        }
        b.clear();
        ok(b.size()==0&&b.sizeWithPinned()==0,"world reset releases all buffers");
    }
    private static void duplicateAndInvalid(){
        WorldSnapshotAdmission<Candidate> b=new WorldSnapshotAdmission<Candidate>();
        Candidate near=new Candidate(1,1,true);
        ok(!b.pin(null,9,1,true),"null pin rejected");
        ok(!b.pin(near,9,Double.NaN,true),"nan pin rejected");
        ok(!b.pin(near,9,-1,true),"negative pin rejected");
        ok(!b.pin(near,9,Double.POSITIVE_INFINITY,true),"infinite pin rejected");
        ok(b.sizeWithPinned()==0,"invalid pin does not change capacity");
        b.offer(near,1,1,true);
        ok(b.pin(near,1,1,true),"pin may reference ordinary resident");
        ok(b.sizeWithPinned()==1,"ordinary resident is not duplicated");
        WorldSnapshot once=snapshot(b,true,1);
        ok(once.entities().size()==1 && once.entities().get(0).id==1,
            "duplicate ID emitted once");
        ok(b.pin(new Candidate(2,3,false),2,9,false),"replace optional pin");
        ok(b.sizeWithPinned()==2,"one active pin only");
        WorldSnapshot twice=snapshot(b,true,2);
        ok(twice.entities().size()==2 && twice.entities().get(1).id==2,
            "pinned farther player streamed in stable order");
        ok(b.size()==1,"optional target never changes normal count");
        b.clear();
        ok(b.sizeWithPinned()==0,"clear drops pinned state");
        final AtomicBoolean denied=new AtomicBoolean();
        Thread outsider=new Thread(()->{
            try{b.pin(near,1,1,true);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        outsider.start();
        try{outsider.join();}catch(InterruptedException error){
            throw new AssertionError(error);
        }
        ok(denied.get(),"pinned snapshot rejects foreign thread access");
        boolean missingSink=false;
        try{b.forEachWithPinned(null);}catch(IllegalArgumentException expected){
            missingSink=true;
        }
        ok(missingSink,"null pinned visitor rejected");
    }
    /** Visitor callbacks may read counts but cannot mutate buffers or
     * recursively stream the same live candidate snapshot. */
    private static void blocks(Runnable action,String message) {
        boolean denied=false;
        try{action.run();}catch(IllegalStateException expected){denied=true;}
        ok(denied,message);
    }
    private static void visitorMutationGuards() {
        WorldSnapshotAdmission<Candidate> b=new WorldSnapshotAdmission<Candidate>();
        Candidate visible=new Candidate(40,2,true);
        Candidate hidden=new Candidate(41,3,false);
        Candidate pinned=new Candidate(42,4,true);
        ok(b.offer(visible,visible.id,4,true),"baseline visible admission");
        ok(b.offer(hidden,hidden.id,9,false),"baseline hidden admission");
        ok(b.pin(pinned,pinned.id,16,true),"baseline pinned admission");
        List<Integer> visited=new ArrayList<Integer>();
        b.forEachWithPinned((item,isVisible)->{
            visited.add(item.id);
            ok(b.size()==2 && b.sizeWithPinned()==3,
                "read-only inspection allowed during visitor callback");
            blocks(b::clear,"world clear forbidden during emission");
            blocks(()->b.offer(new Candidate(99,0,true),99,0,true),
                "offer forbidden during emission");
            blocks(()->b.pin(new Candidate(98,0,false),98,0,false),
                "pin replacement forbidden during emission");
            blocks(()->b.forEach((nested,v)->{}),
                "recursive plain snapshot emission forbidden");
            blocks(()->b.forEachWithPinned((nested,v)->{}),
                "recursive pinned snapshot emission forbidden");
        });
        ok(visited.equals(java.util.Arrays.asList(40,41,42)),
            "ordered snapshot unchanged despite rejected callback mutations");
        ok(b.size()==2 && b.sizeWithPinned()==3,
            "rejected changes never corrupt admission state");
        boolean thrown=false;
        try {b.forEachWithPinned((item,vis)->{
            throw new IllegalArgumentException("visitor failure");
        });}catch(IllegalArgumentException expected){thrown=true;}
        ok(thrown,"visitor failure is reported to owner");
        visited.clear();
        b.forEachWithPinned((item,vis)->visited.add(item.id));
        ok(visited.equals(java.util.Arrays.asList(40,41,42)),
            "failed visitor releases emission guard for retry");
        b.clear();
        ok(b.sizeWithPinned()==0,"after emission failure clear releases references");

        Random rng=new Random(2832026L);
        for(int frame=0;frame<12000;frame++) {
            b.clear();
            int a=100+frame*3;
            Candidate first=new Candidate(a,1,true);
            Candidate second=new Candidate(a+1,2,false);
            Candidate extra=new Candidate(a+2,3,true);
            ok(b.offer(first,first.id,1,true),"replay visible admitted "+frame);
            ok(b.offer(second,second.id,4,false),"replay hidden admitted "+frame);
            boolean pin=rng.nextBoolean();
            if(pin)ok(b.pin(extra,extra.id,9,true),
                "replay pinned admitted "+frame);
            List<Integer> streamed=new ArrayList<Integer>();
            b.forEachWithPinned((item,vis)->{
                streamed.add(item.id);
                // Callbacks intentionally attempt an unsafe same-frame
                // mutation from either a visible, hidden or pinned slot.
                blocks(()->b.offer(extra,extra.id,0,true),
                    "replay callback cannot change selection "+frame);
                blocks(b::clear,"replay callback cannot clear candidates "+frame);
            });
            ok(streamed.size()==(pin?3:2) &&
               streamed.get(0)==a && streamed.get(1)==a+1 &&
               (!pin || streamed.get(2)==a+2),
               "replay stable merged order with optional pin "+frame);
            ok(b.size()==2 && b.sizeWithPinned()==(pin?3:2),
                "replay buffers intact after callback attempts "+frame);
        }
        b.clear();
        ok(b.sizeWithPinned()==0,"replay final clean world boundary");
    }
    private static void churn() {
        WorldSnapshotAdmission<Candidate> b=new WorldSnapshotAdmission<Candidate>();
        Random r=new Random(20261022L);
        for(int frame=0;frame<220;frame++) {
            b.clear();
            for(int i=0;i<640;i++) {
                double dist=1+r.nextDouble()*9;
                Candidate c=new Candidate(i,dist,i%2==0);
                if(i==630)b.pin(c,c.id,c.dist*c.dist,c.visible);
                b.offer(c,c.id,c.dist*c.dist,c.visible);
                ok(b.sizeWithPinned()<=513,"bounded during candidate admission "+frame);
            }
            WorldSnapshot result=snapshot(b,true,frame+1);
            ok(result.entities().size()<=513,"single extra candidate max "+frame);
            sorted(result);
        }
        b.clear();
        ok(b.sizeWithPinned()==0,"long-session cleanup succeeds");
    }
    public static void main(String[] args) {
        crowdedPinnedTarget();duplicateAndInvalid();visitorMutationGuards();churn();
        ok(checks>140000,"substantial pinned-snapshot crowded replay");
        System.out.println("PINNED_SNAPSHOT_PASS assertions="+checks);
    }
}
