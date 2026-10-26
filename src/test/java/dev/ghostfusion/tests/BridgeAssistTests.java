package dev.ghostfusion.tests;

import dev.ghostfusion.core.GhostModule;
import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.features.BridgeAssistPolicy;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pure Java Bridge Assist regression. No actual game/physical keyboard claims. */
public final class BridgeAssistTests {
    private static int assertions;
    private static void ok(boolean pass,String why) {
        assertions++;
        if(!pass)throw new AssertionError(why);
    }
    private static void rejects(Runnable callback,String why) {
        boolean rejected=false;
        try {callback.run();} catch(RuntimeException expected){rejected=true;}
        ok(rejected,why);
    }
    private static boolean decision(BridgeAssistPolicy p,boolean enabled,boolean manual,
                                    boolean ground,boolean support,boolean moving,
                                    boolean edge,int delay,long tick,long epoch) {
        return p.wantsSneak(enabled,manual,ground,support,moving,edge,delay,tick,epoch);
    }
    private static void cornerCases() {
        BridgeAssistPolicy p=new BridgeAssistPolicy();
        ok(decision(p,true,false,true,true,true,true,2,0,1),
            "first eligible edge anticipates an actual drop immediately");
        ok(decision(p,true,false,true,true,true,false,2,1,1),
            "delayed unsneak keeps edge safe for one frame");
        ok(decision(p,true,false,true,true,true,false,2,2,1),
            "delay is inclusive at configured endpoint");
        ok(!decision(p,true,false,true,true,true,false,2,3,1),
            "never hold beyond maximum release delay");
        ok(!decision(p,true,false,true,true,true,false,2,4,1),
            "no stale reactivation on later solid ground");
        ok(decision(p,true,false,true,true,true,true,0,5,1),
            "zero delay still sneaks on edge");
        ok(!decision(p,true,false,true,true,true,false,0,6,1),
            "zero delay releases at first safe step");
        ok(decision(p,true,false,true,true,true,true,8,7,1),
            "maximum configured delay accepts edge");
        ok(!decision(p,true,true,true,true,true,true,8,8,1),
            "manual sneak owns its input; synthetic owner releases");
        ok(!decision(p,true,false,true,true,true,false,8,9,1),
            "manual input cancels pending synthetic hold");
        ok(decision(p,true,false,true,true,true,true,3,10,1),
            "edge reacquisition allowed after user override");
        ok(!decision(p,true,false,false,false,true,true,3,11,1),
            "airborne state never preserves synthetic sneaking");
        ok(!decision(p,true,false,true,true,true,false,3,12,1),
            "landing on safe ground cannot replay previous edge");
        ok(decision(p,true,false,true,true,true,true,1,13,1),
            "can arm again in same world");
        ok(!decision(p,true,false,true,false,true,true,1,14,1),
            "unsupported current footing vetoes sneak");
        ok(!decision(p,true,false,true,true,false,true,1,15,1),
            "release movement vetoes sneak");
        ok(!decision(p,false,false,true,true,true,true,1,16,1),
            "module disable immediately releases owned sneak");
        ok(!decision(p,true,false,true,true,true,false,1,17,1),
            "reenable on safe ground never replays old edge");
        ok(decision(p,true,false,true,true,true,true,1,18,1),
            "re-enable can acquire fresh edge");
        ok(!decision(p,true,false,true,true,true,false,1,0,2),
            "different world generation drops all pending input");
        ok(decision(p,true,false,true,true,true,true,1,1,2),
            "new world accepts fresh grounded edge only");
        rejects(()->decision(p,true,false,true,true,true,true,-1,2,2),
            "negative delay rejected");
        rejects(()->decision(p,true,false,true,true,true,true,9,2,2),
            "oversized delay rejected");
        rejects(()->decision(p,true,false,true,true,true,true,1,-1,2),
            "negative tick rejected");
        rejects(()->decision(p,true,false,true,true,true,true,1,2,-1),
            "negative epoch rejected");
        rejects(()->decision(p,true,false,true,true,true,true,1,0,2),
            "backward tick rejected before mutation");
        p.reset();
        ok(!decision(p,true,false,true,true,true,false,2,0,2),
            "world/focus explicit reset clears synthetic state");
    }
    private static void randomizedTrace() {
        for(int seed=0;seed<60;seed++) {
            BridgeAssistPolicy p=new BridgeAssistPolicy();
            Random rng=new Random(34342026L+seed*17);
            boolean held=false;
            long edgeTick=-1;
            for(long tick=0;tick<400;tick++) {
                boolean enabled=rng.nextInt(20)!=0;
                boolean manual=rng.nextInt(9)==0;
                boolean grounded=rng.nextInt(18)!=0;
                boolean support=rng.nextInt(13)!=0;
                boolean moving=rng.nextInt(8)!=0;
                boolean edge=rng.nextInt(7)==0;
                int delay=rng.nextInt(9);
                boolean eligible=enabled && !manual && grounded && support && moving;
                boolean expected;
                if(!eligible) {
                    held=false;edgeTick=-1;expected=false;
                } else if(edge) {
                    held=true;edgeTick=tick;expected=true;
                } else if(held && edgeTick>=0 && tick-edgeTick<=delay) {
                    expected=true;
                } else {
                    held=false;edgeTick=-1;expected=false;
                }
                boolean actual=decision(p,enabled,manual,grounded,support,
                    moving,edge,delay,tick,1);
                ok(actual==expected,"24k seeded edge/release oracle "+seed+"/"+tick);
            }
        }
    }
    private static void ownerAndProfile() throws Exception {
        final BridgeAssistPolicy policy=new BridgeAssistPolicy();
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try {policy.wantsSneak(true,false,true,true,true,true,1,0,1);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        worker.start();worker.join();
        ok(denied.get(),"bridge state cannot be read/modified by foreign thread");
        Path dir=Files.createTempDirectory("ghostc-bridge-profile-");
        HostRuntime h=new HostRuntime(dir);
        String id="movement.bridgeassist";
        ok(h.modules().get(id).state()==ModuleRegistry.State.DISABLED,
            "new movement automation disabled by default");
        ok(h.bridgeLookahead()==0.75 &&
           h.bridgeReleaseDelayTicks()==2 && h.bridgeRequireBlocks() &&
           !h.bridgeAllowSideways(),"defaults preserve cautious forward-only bridging");
        h.worldPresence(true);
        h.uiActions().fraction(id,"lookahead",0.8);
        h.uiActions().fraction(id,"release_delay_ticks",0.75);
        h.uiActions().bool(id,"require_blocks");
        h.uiActions().bool(id,"allow_sideways");
        h.uiActions().toggle(id);
        double lookahead=h.bridgeLookahead();
        int delay=h.bridgeReleaseDelayTicks();
        ok(h.featureEnabled(id) && lookahead>0.75 && delay>=5 &&
           !h.bridgeRequireBlocks() && h.bridgeAllowSideways(),
           "settings and enabling work through existing typed F7 UI actions");
        h.close();
        HostRuntime loaded=new HostRuntime(dir);
        loaded.worldPresence(true);
        ok(loaded.featureEnabled(id) &&
           loaded.bridgeLookahead()==lookahead &&
           loaded.bridgeReleaseDelayTicks()==delay &&
           !loaded.bridgeRequireBlocks() && loaded.bridgeAllowSideways(),
           "actual sealed profile reload restores module and settings");
        loaded.uiActions().toggle(id);
        ok(!loaded.featureEnabled(id),"manual disable still authoritative");
        loaded.close();
        HostRuntime off=new HostRuntime(dir);
        off.worldPresence(true);
        ok(!off.featureEnabled(id),"explicit disable survives restart");
        off.close();
    }
    public static void main(String[] args) throws Exception {
        cornerCases();
        randomizedTrace();
        ownerAndProfile();
        ok(assertions>=24000,"24k bridge replay plus profile and lifecycle tests");
        System.out.println("BRIDGE_ASSIST_PASS assertions="+assertions);
    }
}
