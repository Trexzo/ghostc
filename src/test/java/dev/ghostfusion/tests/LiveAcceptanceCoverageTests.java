package dev.ghostfusion.tests;

import dev.ghostfusion.features.LiveAcceptanceCoverage;
import dev.ghostfusion.features.LiveAcceptanceCoverage.Check;
import dev.ghostfusion.features.ModuleInteractionStateMachine;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Enabled;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Frame;
import dev.ghostfusion.features.ModuleInteractionStateMachine.Scene;
import dev.ghostfusion.host.RuntimeDiagnostics;
import dev.ghostfusion.core.ConfigIntegrity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Passive evidence is exercised, not automatically game/server certified. */
public final class LiveAcceptanceCoverageTests {
    private static int assertions;
    private static void ok(boolean yes,String why) {
        assertions++;
        if(!yes)throw new AssertionError(why);
    }
    private static Enabled all() {
        return new Enabled(true,true,true,true,true,true,true,true);
    }
    private static Frame frame(ModuleInteractionStateMachine m,long ep,long t,
                               boolean world,Scene scene,boolean focused,
                               boolean pending,Enabled enabled) {
        return m.advance(ep,t,world,scene,focused,enabled,pending);
    }
    private static int seenBit(Check c) {
        return LiveAcceptanceCoverage.bit(c);
    }
    private static void deterministic() {
        ModuleInteractionStateMachine m=new ModuleInteractionStateMachine();
        LiveAcceptanceCoverage coverage=new LiveAcceptanceCoverage();
        Frame f=frame(m,1,0,false,Scene.NO_WORLD,false,false,all());
        ok(coverage.observe(f,"DISABLED",0,0)==0 &&
           coverage.count()==0,
           "no live session never produces acceptance evidence");
        f=frame(m,1,1,true,Scene.GAME,true,false,all());
        int bits=coverage.observe(f,"ATTACH_PENDING",0,0);
        ok((bits&seenBit(Check.GAMEPLAY))!=0 &&
           (bits&seenBit(Check.CONCURRENT))!=0 &&
           !coverage.seen(Check.HOOK_ATTACHED),
           "focused combat+bridge+velocity are a combined grant, pending hook not ready");
        ok(coverage.observe(f,"ATTACH_PENDING",0,0)==0,
           "same frame cannot inflate first-observed evidence");
        bits=coverage.observe(f,"ATTACHED",0,0);
        ok(bits==seenBit(Check.HOOK_ATTACHED),
           "only genuine Netty event-loop attachment marks packet-hook evidence");
        f=frame(m,1,2,true,Scene.GAME,true,true,all());
        bits=coverage.observe(f,"ATTACHED",0,0);
        ok(bits==seenBit(Check.WTAP_HOLD) && !f.autoSprint,
           "actual WTap sprint reservation provides isolated handoff evidence");
        bits=coverage.observe(f,"ATTACHED",1,1);
        ok(bits==(seenBit(Check.S12_LOCAL)|seenBit(Check.S27_EXPLOSION)),
           "aggregates prove local packet class arrived, not server damage success");
        f=frame(m,1,3,true,Scene.PLAYER_INVENTORY,true,false,all());
        bits=coverage.observe(f,"DISABLED",1,1);
        ok(bits==(seenBit(Check.ARMOR)|seenBit(Check.GUI_BLOCK)) &&
           !f.inventoryManager,
           "actual player inventory AutoArmor precedence and GUI blocking observed");
        Enabled noArmor=new Enabled(true,true,true,true,true,false,true,true);
        f=frame(m,1,4,true,Scene.PLAYER_INVENTORY,true,false,noArmor);
        ok(coverage.observe(f,"DISABLED",1,1)==seenBit(Check.SORTER),
           "sorter only counted in the absence of AutoArmor ownership");
        f=frame(m,1,5,true,Scene.CHEST,true,false,all());
        ok(coverage.observe(f,"DISABLED",1,1)==seenBit(Check.CHEST),
           "chest transfer permission counted separately");
        f=frame(m,1,6,true,Scene.GAME,false,false,all());
        ok(coverage.observe(f,"DISABLED",1,1)==seenBit(Check.FOCUS_BLOCK),
           "actual window-focus denial counted on native owner frame");
        ok(coverage.count()==Check.values().length,
           "all eleven distinct observation categories covered once");
        ok(coverage.readout().contains("11/11") &&
           coverage.readout().contains("DISABLED") &&
           coverage.readout().length()<85 &&
           !coverage.readout().contains("@"),
           "readout is anonymous and bounded with current hook state");
        ok(coverage.observe(f,"nonsense",1,1)==0 &&
           coverage.readout().contains("UNAVAILABLE"),
           "unknown packet-hook statuses never appear as untrusted text");
        f=frame(m,2,0,true,Scene.GAME,true,false,all());
        ok(coverage.observe(f,"MAPPING_MISSING",1,1)==0 &&
           coverage.count()==Check.values().length,
           "new epoch preserves only prior in-process evidence; no new claims");
        boolean denied=false;
        try{coverage.observe(null,"ATTACHED",0,0);}
        catch(IllegalArgumentException ex){denied=true;}
        ok(denied,"missing native frame refused");
        denied=false;
        try{coverage.observe(f,"ATTACHED",-1,0);}
        catch(IllegalArgumentException ex){denied=true;}
        ok(denied,"negative packet counter refused");
        denied=false;
        try{LiveAcceptanceCoverage.bit(null);}
        catch(IllegalArgumentException ex){denied=true;}
        ok(denied,"invalid coverage enum refused");
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread worker=new Thread(()->{
            try{coverage.count();}
            catch(IllegalStateException ex){refused.set(true);}
        });
        worker.start();
        try{worker.join();}catch(InterruptedException ex){throw new AssertionError(ex);}
        ok(refused.get(),"foreign Netty thread cannot read/mutate owner coverage");
    }
    private static void seeded() {
        LiveAcceptanceCoverage coverage=new LiveAcceptanceCoverage();
        ModuleInteractionStateMachine machine=new ModuleInteractionStateMachine();
        Random rng=new Random(3472027L);
        long epoch=1,tick=0;
        int oldMask=0;
        for(int i=0;i<45000;i++) {
            if(i%383==0){epoch++;tick=0;}
            boolean world=rng.nextBoolean(),foreground=rng.nextBoolean();
            boolean pending=rng.nextBoolean();
            Scene scene=Scene.values()[rng.nextInt(Scene.values().length)];
            Enabled e=new Enabled(rng.nextBoolean(),rng.nextBoolean(),
                rng.nextBoolean(),rng.nextBoolean(),rng.nextBoolean(),
                rng.nextBoolean(),rng.nextBoolean(),rng.nextBoolean());
            Frame frame=machine.advance(epoch,tick++,world,scene,foreground,e,pending);
            String status=rng.nextBoolean()?"ATTACH_PENDING":"ATTACHED";
            int bits=coverage.observe(frame,status,i>100?7:0,i>200?5:0);
            ok((bits&oldMask)==0,
               "45k frames never re-grant the same acceptance evidence");
            ok(Integer.bitCount(bits)+Integer.bitCount(oldMask)==coverage.count(),
               "45k evidence only grows on first observed native condition");
            oldMask|=bits;
            ok(coverage.count()<=Check.values().length &&
               coverage.readout().length()<85,
               "45k evidence status fixed and bounded");
        }
        ok(oldMask!=0 && coverage.count()>=4,
           "randomized combined paths actually exercised acceptance categories");
    }
    private static void legacyCounterMigration() throws Exception {
        Path dir=Files.createTempDirectory("ghostc-r347-evidence");
        RuntimeDiagnostics diagnostics=new RuntimeDiagnostics();
        for(Check check:Check.values())
            diagnostics.record(RuntimeDiagnostics.Counter.valueOf(
                "ACCEPT_"+check.name()));
        diagnostics.save(dir);
        Path target=dir.resolve("diagnostics-last.properties");
        Properties old=ConfigIntegrity.read(target);
        for(Check check:Check.values())
            old.remove("count.accept_"+check.name().toLowerCase(java.util.Locale.ROOT));
        try(OutputStream out=Files.newOutputStream(target)){
            old.store(out,"old R3.46 anonymous report");
        }
        diagnostics.save(dir);
        Path backup=dir.resolve("diagnostics-last.properties.bak");
        ok(Files.isRegularFile(backup,LinkOption.NOFOLLOW_LINKS) &&
           ConfigIntegrity.read(backup).equals(old),
           "previous strict schema1 report lacking only new evidence counters rotates");
        Properties restored=ConfigIntegrity.read(target);
        for(Check check:Check.values()) {
            String key="count.accept_"+check.name().toLowerCase(java.util.Locale.ROOT);
            ok("1".equals(restored.getProperty(key)),
                "first observed native category persisted as anonymous integer");
        }
        ok(!restored.containsKey("player")&&!restored.containsKey("server") &&
           !restored.containsKey("packet"),
           "no user identity, packet payload or server data stored");
    }
    public static void main(String[] args) throws Exception {
        deterministic();
        seeded();
        legacyCounterMigration();
        ok(assertions>135000,"45k local acceptance coverage model checks");
        System.out.println("LIVE_ACCEPTANCE_COVERAGE_PASS assertions="+assertions);
    }
}
