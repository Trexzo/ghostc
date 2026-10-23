package dev.ghostfusion.tests;

import dev.ghostfusion.services.*;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pure Java, repeatable G3 intent arbitration and lifecycle regression suite. */
public final class ServicesTests {
    private static int assertions;
    private static void ok(boolean expected,String why) {
        assertions++; if(!expected) throw new AssertionError(why);
    }
    private static void rejects(Runnable block,String why) {
        boolean failed=false;
        try { block.run(); } catch(RuntimeException expected) { failed=true; }
        ok(failed,why);
    }
    private static WorldSnapshot.Entity entity(int id,double x,double hp,boolean visible,boolean alive) {
        return new WorldSnapshot.Entity(id,x,0,0,hp,alive,visible,true);
    }
    private static void snapshotsAndTargets() {
        List<WorldSnapshot.Entity> originals=new ArrayList<WorldSnapshot.Entity>();
        originals.add(entity(7,3,5,true,true));
        originals.add(entity(3,-3,1,true,true));
        originals.add(entity(9,2,10,false,true));
        originals.add(entity(4,1,3,true,false));
        WorldSnapshot world=new WorldSnapshot(1,1,0,0,0,0,0,originals);
        originals.clear();
        ok(world.entities().size()==4,"world defensive copy");
        rejects(()->world.entities().clear(),"immutable list");
        TargetSelector selector=new TargetSelector();
        TargetSelector.Query q=new TargetSelector.Query(4,true,true,TargetSelector.Order.DISTANCE);
        List<WorldSnapshot.Entity> distance=selector.select(world,q);
        ok(distance.size()==2,"dead and invisible excluded");
        ok(distance.get(0).id==3 && distance.get(1).id==7,"distance tie uses stable ID");
        List<WorldSnapshot.Entity> health=selector.select(world,
            new TargetSelector.Query(4,true,true,TargetSelector.Order.HEALTH));
        ok(health.get(0).id==3,"health ordering");
        List<WorldSnapshot.Entity> idOrder=selector.select(world,
            new TargetSelector.Query(4,false,true,TargetSelector.Order.ENTITY_ID));
        ok(idOrder.size()==3 && idOrder.get(2).id==9,"ID ordering includes invisible");
        rejects(()->new WorldSnapshot(0,0,0,0,0,0,0,
            Arrays.asList(entity(3,1,1,true,true),entity(3,2,1,true,true))),"duplicate entity ID");
        rejects(()->entity(9,Double.NaN,2,true,true),"nonfinite entity coordinate");
        rejects(()->new TargetSelector.Query(Double.POSITIVE_INFINITY,true,true,TargetSelector.Order.DISTANCE),"invalid range");
        for(int seed=0;seed<80;seed++) {
            Random rnd=new Random(seed);
            List<WorldSnapshot.Entity> list=new ArrayList<WorldSnapshot.Entity>();
            for(int i=0;i<100;i++) list.add(entity(i,rnd.nextInt(40)-20,1+rnd.nextInt(20),rnd.nextBoolean(),true));
            WorldSnapshot w=new WorldSnapshot(8,seed,0,0,0,0,0,list);
            List<WorldSnapshot.Entity> first=selector.select(w,
                new TargetSelector.Query(12,false,true,TargetSelector.Order.DISTANCE));
            Collections.shuffle(list,new Random(seed+100));
            WorldSnapshot reordered=new WorldSnapshot(8,seed,0,0,0,0,0,list);
            List<WorldSnapshot.Entity> second=selector.select(reordered,
                new TargetSelector.Query(12,false,true,TargetSelector.Order.DISTANCE));
            ok(first.size()==second.size(),"stable selection length "+seed);
            for(int i=0;i<first.size();i++)
                ok(first.get(i).id==second.get(i).id,"stable selection by ID "+seed);
        }
    }
    private static void rotations() {
        RotationCoordinator r=new RotationCoordinator();
        r.request("alpha",RotationCoordinator.Scope.CAMERA,45,20,3,20,3,2);
        r.request("beta",RotationCoordinator.Scope.CAMERA,75,35,3,20,3,2);
        RotationCoordinator.Decision d=r.decide(RotationCoordinator.Scope.CAMERA,20,2,10,11);
        ok("alpha".equals(d.owner),"first-in tie wins");
        r.request("beta",RotationCoordinator.Scope.CAMERA,-90,-10,8,20,3,2);
        d=r.decide(RotationCoordinator.Scope.CAMERA,20,2,10,11);
        ok("beta".equals(d.owner) && d.yaw==-90 && d.pitch==-10,"priority arbitration");
        RotationCoordinator.Decision other=r.decide(RotationCoordinator.Scope.OUTBOUND,20,2,10,11);
        ok(other.owner==null && other.yaw==10 && other.pitch==11,"scope isolation and camera restore");
        r.cancel("beta");
        ok("alpha".equals(r.decide(RotationCoordinator.Scope.CAMERA,21,2,9,9).owner),"cancel owner");
        ok(r.decide(RotationCoordinator.Scope.CAMERA,23,2,9,9).owner==null,"expiry exclusive");
        ok(r.decide(RotationCoordinator.Scope.CAMERA,20,3,9,9).owner==null,"generation stale");
        rejects(()->r.request("wrong",RotationCoordinator.Scope.CAMERA,0,100,1,1,1,1),"pitch bounded");
        rejects(()->r.request("wrong",RotationCoordinator.Scope.CAMERA,Float.NaN,0,1,1,1,1),"invalid yaw");
        rejects(()->r.request("wrong",RotationCoordinator.Scope.CAMERA,0,0,1,Long.MAX_VALUE,1,1),"overflow");
        r.request("z",RotationCoordinator.Scope.OUTBOUND,123,12,1,23,1,2);
        ok("z".equals(r.decide(RotationCoordinator.Scope.OUTBOUND,23,2,0,0).owner),"independent outbound intent");
        r.clear();
        ok(r.pending()==0 && r.decide(RotationCoordinator.Scope.OUTBOUND,23,2,4,4).owner==null,"reset restores host value");
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread otherThread=new Thread(()->{
            try { r.decide(RotationCoordinator.Scope.CAMERA,23,2,0,0); }
            catch(IllegalStateException e) { rejected.set(true); }
        });
        otherThread.start();
        try { otherThread.join(); } catch(InterruptedException e) { throw new AssertionError(e); }
        ok(rejected.get(),"rotation thread owns execution");
        r.shutdown();
        rejects(()->r.request("z",RotationCoordinator.Scope.CAMERA,0,0,1,1,1,1),"shutdown prevents reuse");
    }
    private static void actions() {
        ActionCoordinator a=new ActionCoordinator();
        a.request("slow",ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,0,1,2,2,1);
        a.request("fast",ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,0,9,2,2,1);
        a.request("visual",ActionCoordinator.Type.SWING,ActionCoordinator.Phase.OUTPUT,0,2,2,1,1);
        ok(a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.PRE_INPUT,2,1)==null,"phase strict");
        ActionCoordinator.Decision selected=a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,2,1);
        ok(selected!=null && "fast".equals(selected.owner),"attack winner");
        ok(a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,2,1)==null,"once per tick action");
        ok(a.consume(ActionCoordinator.Type.SWING,ActionCoordinator.Phase.OUTPUT,2,1)!=null,"independent type consumption");
        ok(a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,3,1)!=null,"new tick not blocked");
        ok(a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,4,1)==null,"expired slow action");
        a.request("old",ActionCoordinator.Type.USE,ActionCoordinator.Phase.SIMULATE,0,1,5,1,1);
        ok(a.consume(ActionCoordinator.Type.USE,ActionCoordinator.Phase.SIMULATE,5,2)==null,"epoch invalid");
        a.request("old",ActionCoordinator.Type.USE,ActionCoordinator.Phase.SIMULATE,0,1,5,1,2);
        a.cancelOwner("old");
        ok(a.consume(ActionCoordinator.Type.USE,ActionCoordinator.Phase.SIMULATE,5,2)==null,"owner cancel");
        rejects(()->a.request("bad",ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.PRE_INPUT,9,1,2,1,1),"invalid hotbar"); // overloaded by phase signature intentionally checked below
        a.request("bar",ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.PRE_INPUT,8,5,6,1,2);
        ok(a.consume(ActionCoordinator.Type.HOTBAR_SELECT,ActionCoordinator.Phase.PRE_INPUT,6,2).argument==8,"hotbar index preserved");
        a.reset();
        ok(a.pending()==0,"reset empty");
        a.shutdown();
        rejects(()->a.consume(ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,7,2),"shutdown");
    }
    private static void packets() {
        PacketCoordinator p=new PacketCoordinator(2);
        List<PacketCoordinator.Result> statuses=new ArrayList<PacketCoordinator.Result>();
        List<Integer> bytes=new ArrayList<Integer>();
        PacketCoordinator.Transport sink=(direction,payload)->bytes.add((int)payload[0]);
        byte[] caller={5,6};
        long first=p.submit("alpha",PacketCoordinator.Direction.OUTBOUND,caller,1,statuses::add);
        caller[0]=9;
        long second=p.submit("beta",PacketCoordinator.Direction.INBOUND,new byte[]{3},1,statuses::add);
        long no=p.submit("gamma",PacketCoordinator.Direction.OUTBOUND,new byte[]{8},1,statuses::add);
        ok(first>0 && second>first && no==-1,"bounded queue");
        ok(statuses.size()==1 && statuses.get(0)==PacketCoordinator.Result.REJECTED,"immediate rejection completion");
        ok(p.cancel(second),"cancel transaction");
        ok(!p.cancel(second),"cancel idempotent");
        ok(p.flush(sink,1,2)==1 && bytes.get(0)==5,"packet payload copied and delivered");
        ok(statuses.size()==3,"one completion per submission");
        ok(p.metrics().queued==0 && p.metrics().cancelled==1,"queue metrics");
        long stale=p.submit("alpha",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},1,statuses::add);
        ok(p.flush(sink,2,1)==1 && statuses.get(statuses.size()-1)==PacketCoordinator.Result.STALE_SESSION,"generation fence");
        long failed=p.submit("alpha",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},2,statuses::add);
        p.flush((direction,payload)->{ throw new java.io.IOException("fake failure"); },2,1);
        ok(statuses.get(statuses.size()-1)==PacketCoordinator.Result.TRANSPORT_FAILED,"transport failures complete");
        p.submit("alpha",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},2,statuses::add);
        p.submit("alpha",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},2,statuses::add);
        ok(p.cancelOwner("alpha")==2,"cancelOwner every request");
        long thrown=p.submit("z",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},2,result->{throw new IllegalStateException("listener error");});
        ok(p.flush(sink,2,1)==1 && p.metrics().callbackFailures==1,"listener exception contained");
        p.submit("z",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},3,statuses::add);
        p.reset();
        ok(statuses.get(statuses.size()-1)==PacketCoordinator.Result.STALE_SESSION,"reset completes stale");
        p.submit("z",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},3,statuses::add);
        p.shutdown();
        ok(statuses.get(statuses.size()-1)==PacketCoordinator.Result.SHUTDOWN,"shutdown completes pending");
        rejects(()->p.submit("z",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},3,statuses::add),"shutdown submission");
        // Metrics query after shutdown intentionally disallowed by owner lifecycle.
    }
    private static void inventory() {
        InventoryCoordinator inv=new InventoryCoordinator(3);
        long a=inv.submit("one",4,1,10,3,1);
        long b=inv.submit("two",8,5,10,3,1);
        long c=inv.submit("three",10,5,10,1,1);
        ok(inv.submit("four",1,1,10,1,1)==-1,"inventory bound");
        InventoryCoordinator.Transaction t=inv.poll(10,1);
        ok(t.id==b && t.slot==8 && t.state==InventoryCoordinator.State.ACTIVE,"priority");
        ok(inv.poll(11,1).id==b,"single flight");
        ok(inv.acknowledge(a,true,1)==null,"wrong id cannot ack");
        ok(inv.acknowledge(b,true,2)==null,"wrong generation cannot ack");
        ok(inv.acknowledge(b,true,1).state==InventoryCoordinator.State.CONFIRMED,"explicit confirmation");
        ok(inv.poll(11,1).id==a,"higher priority expired at 11");
        ok(inv.cancelOwner("one")==1,"cancel active");
        ok(inv.poll(11,1)==null,"no remaining pending");
        inv.submit("one",1,1,13,1,1);
        ok(inv.poll(14,1)==null,"expiry removes queued");
        inv.submit("one",1,1,15,2,1);
        ok(inv.poll(15,2)==null,"epoch fence");
        inv.submit("one",2,1,16,2,2);
        ok(inv.poll(16,2)!=null && inv.active(),"new generation active");
        inv.reset();
        ok(!inv.active() && inv.queued()==0,"reset clears");
        inv.shutdown();
        rejects(()->inv.poll(20,2),"shutdown");
    }
    /** Future-dated inventory work cannot preempt already-ready work.
     * Expired/wrong-session entries must be pruned without temporary lists. */
    private static void inventoryScheduling() throws Exception {
        java.util.Random random=new java.util.Random(2862026L);
        for(int cycle=0;cycle<12000;cycle++) {
            long tick=cycle*10L, generation=cycle+1L;
            InventoryCoordinator inv=new InventoryCoordinator(5);
            long future=inv.submit("future",1,100,tick+3,4,generation);
            long middle=inv.submit("middle",2,50,tick+2,5,generation);
            long ready=inv.submit("ready",3,1,tick,5,generation);
            long old=inv.submit("stale",4,200,tick,1,generation+1);
            ok(future>0 && middle>0 && ready>0 && old>0,
                "valid scheduled tokens accepted "+cycle);
            InventoryCoordinator.Transaction first=inv.poll(tick,generation);
            ok(first!=null && first.id==ready && first.start==tick &&
               first.state==InventoryCoordinator.State.ACTIVE,
               "eligible lower-priority item selected over future priorities "+cycle);
            ok(inv.queued()==2,
               "wrong-generation work pruned without dropping future work "+cycle);
            ok(inv.acknowledge(ready,true,generation).state==
               InventoryCoordinator.State.CONFIRMED,
               "ready work commits independently "+cycle);
            ok(inv.poll(tick+1,generation)==null && inv.queued()==2,
               "future work remains queued before start "+cycle);
            InventoryCoordinator.Transaction second=inv.poll(tick+2,generation);
            ok(second!=null && second.id==middle && second.start==tick+2,
               "middle request starts on exact tick "+cycle);
            ok(inv.poll(tick+2,generation).id==middle,
               "active work remains single-flight "+cycle);
            ok(inv.acknowledge(middle,random.nextBoolean(),generation)!=null,
               "middle transaction receives explicit acknowledgement "+cycle);
            InventoryCoordinator.Transaction third=inv.poll(tick+3,generation);
            ok(third!=null && third.id==future && third.start==tick+3,
               "higher-priority future request activates on schedule "+cycle);
            ok(inv.acknowledge(future,true,generation)!=null &&
               inv.poll(tick+4,generation)==null && inv.queued()==0,
               "all scheduled work finished without phantom requests "+cycle);
            long expiry=inv.submit("expire",9,3,tick+5,1,generation);
            ok(expiry>0 && inv.poll(tick+6,generation)==null &&
               inv.queued()==0,
               "future work reaching deadline without polling expires "+cycle);
            long firstOwner=inv.submit("owner.a",10,2,tick+7,3,generation);
            long other=inv.submit("owner.b",11,2,tick+7,3,generation);
            long secondOwner=inv.submit("owner.a",12,2,tick+8,3,generation);
            ok(inv.cancelOwner("owner.a")==2 && inv.queued()==1,
               "owner cancellation removes all pending original matches "+cycle);
            ok(inv.poll(tick+7,generation).id==other &&
               inv.cancelOwner("owner.b")==1 && !inv.active() &&
               inv.queued()==0,
               "owner cancellation releases active and keeps other owners "+cycle);
            ok(firstOwner>0 && secondOwner>0,
               "owner tokens retain positive monotonic identity "+cycle);
            inv.shutdown();
        }
        // Equal-priority ordering remains FIFO by token, not input slot.
        InventoryCoordinator ordered=new InventoryCoordinator(3);
        long a=ordered.submit("a",7,8,99,3,1);
        long b=ordered.submit("b",6,8,99,3,1);
        ok(ordered.poll(98,1)==null && ordered.queued()==2,
            "both equal-priority requests wait until their start");
        ok(ordered.poll(99,1).id==a,"equal-priority first token wins");
        ok(ordered.acknowledge(a,true,1)!=null &&
           ordered.poll(99,1).id==b,
           "second equal-priority token follows first");
        ordered.shutdown();

        // The ID is a positive, unreused acknowledgement token, including
        // at the long boundary. Refuse overflow rather than wrapping to -1.
        InventoryCoordinator nearLimit=new InventoryCoordinator(2);
        java.lang.reflect.Field sequence=InventoryCoordinator.class.getDeclaredField("nextId");
        sequence.setAccessible(true);
        sequence.setLong(nearLimit,Long.MAX_VALUE-1);
        ok(nearLimit.submit("near.max",2,1,0,2,1)==Long.MAX_VALUE,
            "last positive inventory token is valid");
        boolean wrapped=false;
        try { nearLimit.submit("wrap",3,1,0,2,1); }
        catch(IllegalStateException expected){wrapped=true;}
        ok(wrapped && nearLimit.queued()==1,
            "inventory token overflow rejected without mutating queue");
        nearLimit.shutdown();
    }
    private static void sessions() throws Exception {
        SessionServices s=new SessionServices();
        ok(!s.connected() && s.generation()==0 && s.tick()==0,"initial state");
        s.world(true);
        ok(s.connected() && s.generation()==1,"world join epoch");
        s.world(true);
        ok(s.generation()==1,"world join idempotent");
        s.tickAdvance();
        WorldSnapshot snapshot=new WorldSnapshot(s.generation(),s.tick(),0,0,0,0,0,
            Collections.singletonList(entity(8,2,4,true,true)));
        s.publish(snapshot);
        ok(s.snapshot()==snapshot,"immutable snapshot published");
        s.rotations().request("a",RotationCoordinator.Scope.CAMERA,20,1,1,s.tick(),2,s.generation());
        List<PacketCoordinator.Result> observed=new ArrayList<PacketCoordinator.Result>();
        s.packets().submit("a",PacketCoordinator.Direction.OUTBOUND,new byte[]{7},s.generation(),observed::add);
        s.inventory().submit("a",1,1,s.tick(),3,s.generation());
        s.actions().request("a",ActionCoordinator.Type.ATTACK,ActionCoordinator.Phase.SIMULATE,0,1,s.tick(),2,s.generation());
        s.world(false);
        ok(s.generation()==2 && !s.connected() && s.snapshot()==null,"world out invalidates");
        ok(s.rotations().pending()==0 && s.actions().pending()==0 && s.inventory().queued()==0,"services cleared");
        ok(s.packets().metrics().queued==0 && observed.size()==1 &&
            observed.get(0)==PacketCoordinator.Result.STALE_SESSION,"packet completion on world leave");
        s.disconnect();
        ok(s.generation()==3,"disconnect rotates epoch even after world event");
        s.world(true);
        ok(s.generation()==4,"reconnect epoch");
        rejects(()->s.publish(snapshot),"cannot publish old snapshot");
        s.close();
        rejects(()->s.tickAdvance(),"closed services reject writes");
        HostRuntime host=new HostRuntime(Files.createTempDirectory("ghostc-g3-host"));
        long before=host.services().generation();
        host.worldPresence(true);
        ok(host.services().generation()==before+1,"host world transition linked");
        host.tick();
        ok(host.services().tick()==1,"host tick linked");
        host.disconnect();
        ok(!host.services().connected(),"host disconnect invalidates");
        host.close();
    }
    public static void main(String[] args) throws Exception {
        snapshotsAndTargets(); rotations(); actions(); packets(); inventory();
        inventoryScheduling(); sessions();
        ok(assertions>100,"nontrivial service assertions");
        System.out.println("SERVICES_REGRESSION_PASS assertions="+assertions);
    }
}
