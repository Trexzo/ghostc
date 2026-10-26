package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.SessionServices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** R0.7 — deterministic tests for Block Hit, Hit Select and shared action ordering. */
public final class CombatMoreTests {
    private static int n;
    private static void ok(boolean yes, String why) {
        n++;
        if (!yes) throw new AssertionError(why);
    }
    private static void rejects(Runnable run, String why) {
        boolean failed=false;
        try { run.run(); } catch (RuntimeException ex) { failed=true; }
        ok(failed, why);
    }
    private static void hitSelect() throws Exception {
        HitSelectPolicy p=new HitSelectPolicy();
        ok(!p.accept(false,true,0,2,2,0,1),"disabled never accepts");
        ok(!p.accept(true,false,0,2,2,1,1),"no crosshair target");
        ok(!p.accept(true,true,5,2,2,2,1),"hurt window blocks extra pulse");
        ok(p.accept(true,true,2,2,2,3,1),"threshold inclusive");
        ok(!p.accept(true,true,0,2,2,3,1),"same tick gated");
        ok(!p.accept(true,true,0,2,2,4,1),"minimum gap gate");
        ok(p.accept(true,true,0,2,2,5,1),"cooldown ends");
        ok(p.accept(true,true,0,2,2,0,2),"epoch reset allows first selection");
        rejects(()->p.accept(true,true,-1,2,2,1,2),"negative hurt time rejected");
        rejects(()->p.accept(true,true,0,11,2,1,2),"invalid threshold");
        rejects(()->p.accept(true,true,0,2,0,1,2),"invalid gap");
        rejects(()->p.accept(true,true,0,2,2,-1,2),"negative tick");
        ok(p.accept(true,true,0,2,2,2,2),"forward tick permitted");
        rejects(()->p.accept(true,true,0,2,2,1,2),"regressed tick");
        p.reset();
        ok(p.accept(true,true,0,0,1,0,1),"explicit reset clears gate");
        for(int seed=0;seed<90;seed++) {
            Random rnd=new Random(seed*31L+17);
            p.reset();
            long lastAccepted=-1;
            for(long tick=0;tick<90;tick++) {
                int hurt=rnd.nextInt(12);
                int threshold=2;
                int gap=3;
                boolean visibleTarget=rnd.nextBoolean();
                boolean allowed=p.accept(true,visibleTarget,hurt,threshold,gap,tick,1);
                boolean expected=visibleTarget && hurt<=threshold &&
                    (lastAccepted<0 || tick-lastAccepted>=gap);
                ok(allowed==expected,"deterministic hit-select seed "+seed+" tick "+tick);
                if(allowed)lastAccepted=tick;
            }
        }
        AtomicBoolean wrongThread=new AtomicBoolean(false);
        Thread t=new Thread(()->{
            try { p.accept(true,true,0,2,2,1,2); }
            catch(IllegalStateException ex) { wrongThread.set(true); }
        });
        t.start();t.join();
        ok(wrongThread.get(),"owner-thread fence");
    }
    private static void blockHit() throws Exception {
        BlockHitPolicy p=new BlockHitPolicy();
        ok(!p.due(true,true,false,true,true,true,false,4,0,1),"first tick arms");
        ok(!p.due(true,true,false,true,true,true,false,4,1,1),"early 1");
        ok(!p.due(true,true,false,true,true,true,false,4,3,1),"early 3");
        ok(!p.due(true,true,false,true,true,true,true,4,4,1),"same-tick attack takes precedence");
        ok(p.due(true,true,false,true,true,true,false,4,5,1),"block pulse retries next idle tick");
        ok(!p.due(true,true,false,true,true,true,false,4,5,1),"no duplicate same tick");
        ok(!p.due(true,true,true,true,true,true,false,4,10,1),"vanilla use blocks synthetic use");
        ok(!p.due(true,true,false,true,true,true,false,4,11,1),"held context resets to first press");
        ok(!p.due(true,true,false,true,true,true,false,4,13,1),"reset gap maintained");
        ok(p.due(true,true,false,true,true,true,false,4,15,1),"rearmed due");
        ok(!p.due(true,true,false,false,true,true,false,4,16,1),"sword required");
        ok(!p.due(true,true,false,true,false,true,false,4,17,1),"optional target gate");
        ok(!p.due(true,true,false,true,false,false,false,4,18,1),"first press after invalid state arms");
        ok(p.due(true,true,false,true,false,false,false,4,22,1),"can allow no target when configured");
        ok(!p.due(false,true,false,true,true,true,false,4,30,1),"disabled gate");
        ok(!p.due(true,false,false,true,true,true,false,4,31,1),"release gate");
        ok(!p.due(true,true,false,true,true,true,false,4,32,1),"resume arms");
        ok(!p.due(true,true,false,true,true,true,false,4,0,2),"new epoch re-arms");
        rejects(()->p.due(true,true,false,true,true,true,false,1,1,2),"interval minimum enforced");
        rejects(()->p.due(true,true,false,true,true,true,false,21,1,2),"interval maximum enforced");
        rejects(()->p.due(true,true,false,true,true,true,false,4,-1,2),"invalid tick");
        rejects(()->p.due(true,true,false,true,true,true,false,4,1,-1),"invalid generation");
        p.reset();
        ok(!p.due(true,true,false,true,true,true,false,2,0,1),"reset first press");
        ok(p.due(true,true,false,true,true,true,false,2,2,1),"period 2 accepted");
        for(int seed=0;seed<75;seed++) {
            Random r=new Random(seed+929);
            p.reset();
            long lastPulse=0;
            boolean armed=false;
            for(long tick=0;tick<80;tick++) {
                boolean held=r.nextInt(10)>1;
                boolean rightUse=r.nextInt(10)==0;
                boolean sword=r.nextInt(10)>1;
                boolean target=r.nextInt(10)>1;
                boolean attack=r.nextBoolean();
                boolean eligibility=held && !rightUse && sword && target;
                boolean expected=false;
                if(!eligibility){armed=false;}
                else if(!armed){armed=true;lastPulse=tick;}
                else if(tick-lastPulse>=3 && !attack){expected=true;lastPulse=tick;}
                boolean got=p.due(true,held,rightUse,sword,target,true,attack,3,tick,1);
                ok(got==expected,"block-hit trace "+seed+" tick "+tick);
            }
        }
        AtomicBoolean rejected=new AtomicBoolean(false);
        Thread other=new Thread(()->{
            try {p.due(true,true,false,true,true,true,false,4,0,2);}
            catch(IllegalStateException ex){rejected.set(true);}
        });
        other.start();other.join();
        ok(rejected.get(),"blockhit single owner");
    }
    private static void sharedCoordinator() {
        SessionServices s=new SessionServices();
        s.world(true);s.tickAdvance();
        HitSelectPolicy hit=new HitSelectPolicy();
        BlockHitPolicy block=new BlockHitPolicy();
        long t=s.tick(),epoch=s.generation();
        ok(hit.accept(true,true,0,2,2,t,epoch),"hit select allows initial");
        s.actions().request("combat.clicker",ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,0,10,t,1,epoch);
        boolean extraAttack=s.actions().pending()>0;
        ok(!block.due(true,true,false,true,true,true,extraAttack,2,t,epoch),
            "block arms without use conflict");
        ActionCoordinator.Decision attack=s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,t,epoch);
        ok(attack!=null && "combat.clicker".equals(attack.owner),"clicker dispatches through arbiter");
        ok(s.actions().consume(ActionCoordinator.Type.ATTACK,
            ActionCoordinator.Phase.OUTPUT,t,epoch)==null,"attack cannot double consume");
        s.tickAdvance();t=s.tick();
        ok(!block.due(true,true,false,true,true,true,false,2,t,epoch),"block gap");
        s.tickAdvance();t=s.tick();
        ok(block.due(true,true,false,true,true,true,false,2,t,epoch),"block due on non-attack tick");
        s.actions().request("combat.blockhit",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,10,t,1,epoch);
        ActionCoordinator.Decision use=s.actions().consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,t,epoch);
        ok(use!=null && "combat.blockhit".equals(use.owner),"use intent resolves independently");
        ok(s.actions().consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,t,epoch)==null,"no duplicate use");
        s.actions().request("combat.blockhit",ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,0,10,t,1,epoch);
        s.disconnect();
        ok(s.actions().pending()==0,"world leave removes use");
        s.world(true);s.tickAdvance();
        ok(s.actions().consume(ActionCoordinator.Type.USE,
            ActionCoordinator.Phase.OUTPUT,s.tick(),s.generation())==null,"no delayed use on reconnect");
        s.close();
    }
    private static void profiles() throws Exception {
        Path path=Files.createTempDirectory("ghostc-r07-combat-");
        HostRuntime h=new HostRuntime(path);
        String block="combat.blockhit",hit="combat.hitselect";
        ok(h.modules().get(block).state()==ModuleRegistry.State.DISABLED,"block hit default off");
        ok(h.modules().get(hit).state()==ModuleRegistry.State.DISABLED,"hit select default off");
        h.worldPresence(true);
        h.uiActions().fraction(block,"interval_ticks",1);
        h.uiActions().bool(block,"require_target");
        h.uiActions().fraction(hit,"max_hurt_time",0);
        h.uiActions().fraction(hit,"min_gap_ticks",1);
        h.uiActions().bool(hit,"require_target");
        ok(h.blockIntervalTicks()==10 && !h.blockRequireTarget(),"blockhit typed settings");
        ok(h.hitMaxHurtTime()==0 && h.hitGapTicks()==8 && !h.hitRequireTarget(),"hitselect typed settings");
        h.uiActions().toggle(block);h.uiActions().toggle(hit);
        ok(h.featureEnabled(block) && h.featureEnabled(hit),"features opt-in");
        h.uiActions().bind(block,44);
        h.key(44,true,false);h.key(44,false,false);
        ok(!h.featureEnabled(block) && h.featureEnabled(hit),"nonstatus keybind persistence");
        h.close();
        HostRuntime again=new HostRuntime(path);
        ok(again.modules().get(block).state()==ModuleRegistry.State.DISABLED,"disabled persisted");
        ok(again.modules().get(hit).state()==ModuleRegistry.State.ENABLED,"enabled persisted");
        ok(again.blockIntervalTicks()==10 && !again.blockRequireTarget(),"block profile roundtrip");
        ok(again.hitMaxHurtTime()==0 && again.hitGapTicks()==8 && !again.hitRequireTarget(),"hit profile roundtrip");
        again.worldPresence(true);
        ok(!again.featureEnabled(block) && again.featureEnabled(hit),"loaded states active only in world");
        again.disconnect();
        ok(!again.featureEnabled(hit),"no combat without world");
        again.close();
    }
    public static void main(String[] args) throws Exception {
        hitSelect(); blockHit(); sharedCoordinator(); profiles();
        // Run opt-in event mode tests through the existing Core CI runner.
        CombatModesTests.main(args);
        ok(n>12000,"large independent deterministic regression");
        System.out.println("COMBAT_MORE_REGRESSION_PASS assertions="+n);
    }
}
