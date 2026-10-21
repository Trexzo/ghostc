package dev.ghostfusion.tests;

import dev.ghostfusion.services.PacketCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic re-entrant packet-completion reset and shutdown fences. */
public final class PacketLifecycleReentryTests {
    private static long assertions;
    private static void ok(boolean value,String message) {
        assertions++;
        if(!value)throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        boolean blocked=false;
        try {action.run();} catch(IllegalStateException expected){blocked=true;}
        ok(blocked,message);
    }
    private static void resetCallbacks() {
        PacketCoordinator packets=new PacketCoordinator(8);
        final AtomicInteger completed=new AtomicInteger();
        final AtomicInteger rejected=new AtomicInteger();
        List<PacketCoordinator.Result> receipts=new ArrayList<PacketCoordinator.Result>();
        for(int i=0;i<5;i++) {
            long token=packets.submit("module."+i,PacketCoordinator.Direction.OUTBOUND,
                new byte[]{(byte)i},5,result->{
                    completed.incrementAndGet();
                    receipts.add(result);
                    try {
                        packets.submit("callback.reenter",PacketCoordinator.Direction.OUTBOUND,
                            new byte[]{42},5,unused->{});
                    } catch(IllegalStateException expected){rejected.incrementAndGet();}
                    try {packets.reset();}
                    catch(IllegalStateException expected){rejected.incrementAndGet();}
                });
            ok(token>0,"original reset request accepted");
        }
        packets.reset();
        ok(completed.get()==5 && rejected.get()==10,
           "all old callbacks called exactly once and both nested actions refused");
        ok(packets.metrics().queued==0 && packets.metrics().cancelled==5,
           "reset left no old, newly-enqueued or leaked requests");
        for(PacketCoordinator.Result result:receipts)
            ok(result==PacketCoordinator.Result.STALE_SESSION,
               "every old request terminates as STALE_SESSION");
        packets.reset();
        ok(completed.get()==5,"second reset must not duplicate completions");
        final AtomicInteger postReset=new AtomicInteger();
        long fresh=packets.submit("module.fresh",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{9},6,r->{
                ok(r==PacketCoordinator.Result.SENT,"new-generation request delivered normally");
                postReset.incrementAndGet();
            });
        ok(fresh>0 && packets.flush((direction,payload)->{
            ok(payload[0]==9,"fresh payload unaffected by stale callbacks");
        },6,8)==1,"reset fence releases for legitimate new-generation submissions");
        ok(postReset.get()==1 && packets.metrics().queued==0,
           "fresh request receives exactly one terminal completion");
        packets.shutdown();
    }
    private static void shutdownCallbacks() {
        PacketCoordinator packets=new PacketCoordinator(8);
        final AtomicInteger completed=new AtomicInteger();
        final AtomicInteger refused=new AtomicInteger();
        List<PacketCoordinator.Result> receipts=new ArrayList<PacketCoordinator.Result>();
        for(int i=0;i<6;i++){
            long token=packets.submit("m."+i,PacketCoordinator.Direction.INBOUND,
                new byte[]{(byte)i},2,result->{
                    completed.incrementAndGet();
                    receipts.add(result);
                    try {packets.submit("reenter",PacketCoordinator.Direction.OUTBOUND,
                        new byte[]{1},2,r->{});}
                    catch(IllegalStateException expected){refused.incrementAndGet();}
                    try {packets.reset();}
                    catch(IllegalStateException expected){refused.incrementAndGet();}
                });
            ok(token>0,"old request queued for shutdown");
        }
        packets.shutdown();
        ok(completed.get()==6 && refused.get()==12,
           "shutdown rejects all callback re-entrant submissions/resets");
        for(PacketCoordinator.Result result:receipts)
            ok(result==PacketCoordinator.Result.SHUTDOWN,
               "every drained request gets SHUTDOWN exactly once");
        packets.shutdown();
        ok(completed.get()==6,"idempotent shutdown does not redeliver completions");
        rejects(()->packets.submit("late",PacketCoordinator.Direction.INBOUND,
            new byte[]{1},2,r->{}),"service remains stopped");
        rejects(()->packets.metrics(),"service cannot expose old work after shutdown");
    }
    private static void thrownCallbacksStillDrain() {
        PacketCoordinator packets=new PacketCoordinator(4);
        AtomicInteger seen=new AtomicInteger();
        packets.submit("bad",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},1,
            r->{seen.incrementAndGet();throw new IllegalArgumentException("private callback");});
        packets.submit("good",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},1,
            r->{ok(r==PacketCoordinator.Result.STALE_SESSION,"subsequent reset callback survives");
                seen.incrementAndGet();});
        packets.reset();
        ok(seen.get()==2,"callback exception does not stop draining remaining requests");
        ok(packets.metrics().callbackFailures==1 &&
           packets.metrics().queued==0,"fault isolated, queue empty");
        packets.submit("bad",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},2,
            r->{seen.incrementAndGet();throw new IllegalStateException("private callback");});
        packets.submit("good",PacketCoordinator.Direction.OUTBOUND,new byte[]{2},2,
            r->{ok(r==PacketCoordinator.Result.SHUTDOWN,"subsequent shutdown callback survives");
                seen.incrementAndGet();});
        packets.shutdown();
        ok(seen.get()==4,"callback exception does not interrupt shutdown completion");
    }
    private static void worldTransitions(){
        SessionServices services=new SessionServices();
        services.world(true);
        final AtomicInteger blocked=new AtomicInteger();
        final AtomicInteger completions=new AtomicInteger();
        for(int i=0;i<3;i++) {
            services.packets().submit("world."+i,PacketCoordinator.Direction.OUTBOUND,
                new byte[]{4},services.generation(),r->{
                    ok(r==PacketCoordinator.Result.STALE_SESSION,
                       "world transition cancels old packet");
                    completions.incrementAndGet();
                    try {services.packets().submit("stale.callback",
                        PacketCoordinator.Direction.OUTBOUND,new byte[]{5},
                        services.generation(),unused->{});}
                    catch(IllegalStateException expected){blocked.incrementAndGet();}
                });
        }
        services.world(false);
        ok(completions.get()==3 && blocked.get()==3,
           "session world transition cannot be repopulated from stale callbacks");
        ok(services.packets().metrics().queued==0,"old packets drained on world exit");
        services.world(true);
        services.close();
    }
    private static void repeatedLifecycles() {
        for(int cycle=0;cycle<4000;cycle++){
            final int generation=cycle;
            PacketCoordinator coordinator=new PacketCoordinator(3);
            AtomicInteger completed=new AtomicInteger();
            AtomicInteger denied=new AtomicInteger();
            for(int i=0;i<3;i++){
                coordinator.submit("m."+i,PacketCoordinator.Direction.OUTBOUND,
                    new byte[]{(byte)i},cycle,r->{
                        completed.incrementAndGet();
                        ok(r==PacketCoordinator.Result.STALE_SESSION,
                           "repeated reset returns stale session");
                        try {coordinator.submit("nested",PacketCoordinator.Direction.OUTBOUND,
                            new byte[]{1},generation,unused->{});}
                        catch(IllegalStateException expected){denied.incrementAndGet();}
                    });
            }
            coordinator.reset();
            ok(completed.get()==3 && denied.get()==3 &&
                coordinator.metrics().queued==0,"repeat reset is complete and bounded");
            coordinator.shutdown();
        }
    }
    public static void main(String[] args) {
        resetCallbacks();shutdownCallbacks();thrownCallbacksStillDrain();
        worldTransitions();repeatedLifecycles();
        ok(assertions>14000,"substantial packet lifecycle regression coverage");
        System.out.println("PACKET_LIFECYCLE_REENTRY_PASS assertions="+assertions);
    }
}
