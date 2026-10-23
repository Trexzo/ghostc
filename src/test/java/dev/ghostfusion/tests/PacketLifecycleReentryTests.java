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
    /** Reentrant transport/completion callbacks must not recursively drain
     * beyond a single flush call's budget or exhaust the Java call stack. */
    private static void flushReentryBudget() {
        PacketCoordinator packets=new PacketCoordinator(8);
        AtomicInteger refused=new AtomicInteger();
        AtomicInteger sent=new AtomicInteger();
        List<Integer> bytes=new ArrayList<Integer>();
        final PacketCoordinator.Transport transport=(direction,payload)->{
            bytes.add((int)payload[0]);
            try { packets.flush((d,b)->{},1,8); }
            catch(IllegalStateException expected) { refused.incrementAndGet(); }
        };
        packets.submit("m.first",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1},1,result->{
                ok(result==PacketCoordinator.Result.SENT,"first completion is delivered");
                sent.incrementAndGet();
                try { packets.flush(transport,1,8); }
                catch(IllegalStateException expected) { refused.incrementAndGet(); }
                // Submission from an ordinary completion is permitted, but it
                // cannot be recursively flushed in the same outer callback.
                long added=packets.submit("m.new",PacketCoordinator.Direction.OUTBOUND,
                    new byte[]{3},1,r->sent.incrementAndGet());
                ok(added>0,"reentrant completion can queue future work");
            });
        packets.submit("m.second",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{2},1,result->{
                ok(result==PacketCoordinator.Result.SENT,"second completion still called");
                sent.incrementAndGet();
            });
        ok(packets.flush(transport,1,2)==2,
            "outer flush respects two-request budget despite nested attempts");
        ok(refused.get()==3,"transport and completion recursive flushes rejected");
        ok(bytes.size()==2 && bytes.get(0)==1 && bytes.get(1)==2,
            "only original two requests reached transport");
        ok(packets.metrics().queued==1 && sent.get()==2,
            "new callback-submitted request remains queued for next flush");
        ok(packets.flush(transport,1,2)==1 && sent.get()==3,
            "fresh request delivered once on explicit subsequent flush");
        ok(packets.metrics().queued==0 && packets.metrics().delivered==3 &&
            packets.metrics().failed==0,"normal metrics preserved");
        packets.shutdown();

        // A reset invoked by a completion can still cancel the remaining
        // batch; recursive flush rejection must not block cancellation.
        PacketCoordinator cancelling=new PacketCoordinator(4);
        AtomicInteger stale=new AtomicInteger();
        cancelling.submit("reset.first",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{4},2,r->{
                ok(r==PacketCoordinator.Result.SENT,"resetting first packet sent");
                cancelling.reset();
            });
        cancelling.submit("reset.second",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{5},2,r->{
                ok(r==PacketCoordinator.Result.STALE_SESSION,
                    "reset cancels undelivered captured batch");
                stale.incrementAndGet();
            });
        ok(cancelling.flush((direction,payload)->{},2,4)==1,
            "outer flush skips detached requests after completion reset");
        ok(stale.get()==1 && cancelling.metrics().queued==0,
            "reset from completion remains safe and terminal exactly once");
        cancelling.shutdown();

        // Reproduce a repeated completion-triggered nested-flush storm
        // without recursion or new work surviving the explicit budget.
        for(int cycle=0;cycle<3000;cycle++){
            final int generation=cycle;
            PacketCoordinator q=new PacketCoordinator(2);
            AtomicInteger blocked=new AtomicInteger();
            q.submit("storm",PacketCoordinator.Direction.OUTBOUND,new byte[]{1},generation,
                result->{
                    try { q.flush((d,b)->{},generation,2); }
                    catch(IllegalStateException expected){blocked.incrementAndGet();}
                });
            ok(q.flush((d,b)->{},generation,1)==1 && blocked.get()==1 &&
                q.metrics().queued==0,"nested flush storm bounded "+cycle);
            q.shutdown();
        }
    }
    /** R2.80: owner cancellation must remain terminal and count-stable even
     * when a completion shuts down, resets, or queues additional work. */
    private static void atomicOwnerCancellation() {
        PacketCoordinator shutdown=new PacketCoordinator(5);
        List<String> events=new ArrayList<String>();
        shutdown.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1},1,r->{
                events.add("a1:"+r);
                shutdown.shutdown();
            });
        shutdown.submit("owner.b",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{2},1,r->events.add("b:"+r));
        shutdown.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{3},1,r->events.add("a2:"+r));
        ok(shutdown.cancelOwner("owner.a")==2,
            "callback-triggered shutdown cannot interrupt detached owner batch");
        ok(events.size()==3 && "a1:CANCELLED".equals(events.get(0)) &&
            "b:SHUTDOWN".equals(events.get(1)) &&
            "a2:CANCELLED".equals(events.get(2)),
            "detached owner requests remain CANCELLED; unrelated shutdown is distinct");
        rejects(()->shutdown.cancelOwner("owner.a"),
            "shutdown stays final after owner cancellation returns");

        PacketCoordinator reset=new PacketCoordinator(5);
        List<String> resetResults=new ArrayList<String>();
        reset.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1},2,r->{resetResults.add("a1:"+r);reset.reset();});
        reset.submit("owner.b",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{2},2,r->resetResults.add("b:"+r));
        reset.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{3},2,r->resetResults.add("a2:"+r));
        ok(reset.cancelOwner("owner.a")==2 &&
           resetResults.contains("a1:CANCELLED") &&
           resetResults.contains("a2:CANCELLED") &&
           resetResults.contains("b:STALE_SESSION"),
            "reset from callback only cancels non-detached requests");
        ok(reset.metrics().queued==0 && reset.metrics().cancelled==3,
            "reset and detached owner cancellations counted exactly once");
        reset.shutdown();

        PacketCoordinator submitting=new PacketCoordinator(6);
        List<String> receipts=new ArrayList<String>();
        submitting.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1},3,r->{
                receipts.add("old1:"+r);
                ok(submitting.cancelOwner("owner.a")==0,
                    "nested owner cancellation cannot recapture detached batch");
                long newId=submitting.submit("owner.a",
                    PacketCoordinator.Direction.OUTBOUND,new byte[]{9},3,
                    result->receipts.add("new:"+result));
                ok(newId>0,"new work from completion allowed after detachment");
            });
        submitting.submit("owner.b",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{2},3,r->receipts.add("peer:"+r));
        submitting.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{3},3,r->receipts.add("old2:"+r));
        ok(submitting.cancelOwner("owner.a")==2,
            "original matched count stable across nested cancellation and submit");
        ok(submitting.metrics().queued==2 && submitting.metrics().cancelled==2,
            "new same-owner work and unrelated peer remain pending");
        ok(submitting.flush((d,b)->{},3,6)==2,
            "new work delivers on a subsequent explicit flush");
        ok(receipts.contains("old1:CANCELLED") &&
           receipts.contains("old2:CANCELLED") &&
           receipts.contains("new:SENT") && receipts.contains("peer:SENT"),
            "each detached and subsequently submitted item has unique correct result");
        submitting.shutdown();

        PacketCoordinator throwing=new PacketCoordinator(3);
        AtomicInteger settled=new AtomicInteger();
        throwing.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{1},1,r->{settled.incrementAndGet();
                throw new IllegalStateException("private callback");});
        throwing.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
            new byte[]{2},1,r->{
                ok(r==PacketCoordinator.Result.CANCELLED,"later owner receipt is cancelled");
                settled.incrementAndGet();
            });
        ok(throwing.cancelOwner("owner.a")==2 && settled.get()==2 &&
           throwing.metrics().callbackFailures==1 &&
           throwing.metrics().cancelled==2,
            "thrown first callback does not prevent later owner completion");
        throwing.shutdown();
    }

    private static void ownerCancellationReplay() {
        for(int cycle=0;cycle<6000;cycle++) {
            final int mode=cycle%4, epoch=cycle;
            PacketCoordinator packets=new PacketCoordinator(5);
            AtomicInteger owners=new AtomicInteger(),peers=new AtomicInteger(),
                newWork=new AtomicInteger();
            packets.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
                new byte[]{1},epoch,r->{
                    ok(r==PacketCoordinator.Result.CANCELLED,
                        "old owner receipt remains cancelled "+epoch);
                    owners.incrementAndGet();
                    if(mode==0)packets.shutdown();
                    else if(mode==1)packets.reset();
                    else if(mode==2) {
                        ok(packets.cancelOwner("owner.a")==0,
                            "reentry does not see detached owner "+epoch);
                    } else {
                        packets.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
                            new byte[]{4},epoch,result->{
                                ok(result==PacketCoordinator.Result.SENT,
                                    "new owner work not retroactively cancelled "+epoch);
                                newWork.incrementAndGet();
                            });
                    }
                });
            packets.submit("owner.b",PacketCoordinator.Direction.OUTBOUND,
                new byte[]{2},epoch,r->{
                    PacketCoordinator.Result expected=mode==0?
                        PacketCoordinator.Result.SHUTDOWN:mode==1?
                        PacketCoordinator.Result.STALE_SESSION:
                        PacketCoordinator.Result.SENT;
                    ok(r==expected,"peer terminal result follows transition "+epoch);
                    peers.incrementAndGet();
                });
            packets.submit("owner.a",PacketCoordinator.Direction.OUTBOUND,
                new byte[]{3},epoch,r->{
                    ok(r==PacketCoordinator.Result.CANCELLED,
                        "second original owner receipt remains cancelled "+epoch);
                    owners.incrementAndGet();
                });
            ok(packets.cancelOwner("owner.a")==2 && owners.get()==2,
                "complete original owner batch settled exactly once "+epoch);
            if(mode>=2)
                ok(packets.flush((d,b)->{},epoch,5)==(mode==3?2:1),
                    "subsequent queue contains only non-detached work "+epoch);
            ok(peers.get()==1 && newWork.get()==(mode==3?1:0),
                "completion counts match owner cancellation mode "+epoch);
            if(mode!=0) {
                ok(packets.metrics().queued==0 &&
                   packets.metrics().cancelled==(mode==1?3:2),
                    "owner and reset metrics exact "+epoch);
            }
            packets.shutdown();
        }
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
        flushReentryBudget();atomicOwnerCancellation();ownerCancellationReplay();
        worldTransitions();repeatedLifecycles();
        ok(assertions>14000,"substantial packet lifecycle regression coverage");
        System.out.println("PACKET_LIFECYCLE_REENTRY_PASS assertions="+assertions);
    }
}
