package dev.ghostfusion.tests;

import dev.ghostfusion.core.Setting;
import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.services.WorldSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;

/** R2.67: nearest eligible non-teammate and crosshair-only opt-in policy. */
public final class TargetHudTeamFilterTests {
    private static int checks;
    private static void ok(boolean good,String message){
        checks++;if(!good)throw new AssertionError(message);
    }
    private static WorldSnapshot.Entity e(int id,double x,boolean visible,
                                          boolean alive,double hp,boolean player){
        return new WorldSnapshot.Entity(id,x,0,0,hp,alive,visible,player);
    }
    private static WorldSnapshot world(long tick,List<WorldSnapshot.Entity> entities) {
        return new WorldSnapshot(1,tick,0,0,0,0,0,entities);
    }
    private static void edge(){
        TargetHudPolicy policy=new TargetHudPolicy();
        List<WorldSnapshot.Entity> candidates=new ArrayList<WorldSnapshot.Entity>();
        candidates.add(e(12,1,true,true,20,true)); // nearer teammate
        candidates.add(e(13,2,true,true,20,true)); // enemy
        candidates.add(e(14,2,true,true,20,true)); // enemy with worse ID
        candidates.add(e(15,3,true,true,20,true));
        candidates.add(e(16,4,false,true,20,true));
        WorldSnapshot w=world(1,candidates);
        IntPredicate skip12=id->id!=12;
        ok(policy.select(w,8,true,"nearest",null).id==12,
            "legacy nearest still chooses teammate with option off");
        ok(policy.select(w,8,true,"nearest",null,skip12).id==13,
            "nearest policy picks first eligible non-teammate");
        ok(policy.select(w,8,true,"nearest",null,id->id!=12&&id!=13).id==14,
            "stable ID tie-break preserved after team filter");
        ok(policy.select(w,8,true,"crosshair",12,skip12)==null,
            "crosshair teammate suppressed without unrelated fallback");
        ok(policy.select(w,8,true,"crosshair",13,skip12).id==13,
            "crosshair enemy remains visible");
        ok(policy.select(w,8,true,"crosshair",16,skip12)==null,
            "visible-only check runs before live predicate");
        ok(policy.select(w,8,true,"crosshair",15,skip12).id==15,
            "crosshair non-teammate allowed");
        ok(policy.select(w,8,true,"nearest",null,id->false)==null,
            "all teammates produces no HUD");
        ok(policy.select(null,8,true,"nearest",null,skip12)==null,
            "world reset produces no target");
        boolean rejected=false;
        try {policy.select(w,8,true,"nearest",null,null);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"null candidate gate rejected");
        rejected=false;
        try {policy.select(w,8,true,"garbage",null,skip12);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"unknown HUD mode rejected");
        rejected=false;
        try {policy.select(w,0,true,"nearest",null,skip12);}
        catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"nonpositive range rejected");
    }
    private static void replay(){
        TargetHudPolicy policy=new TargetHudPolicy();
        Random rnd=new Random(2672026L);
        for(int frame=0;frame<30000;frame++){
            int count=1+rnd.nextInt(32);
            List<WorldSnapshot.Entity> items=new ArrayList<WorldSnapshot.Entity>();
            final boolean[] team=new boolean[count+1];
            for(int n=1;n<=count;n++){
                team[n]=rnd.nextInt(4)==0;
                // Deliberate ties and a mix of eligible/ineligible entities.
                items.add(e(n,1+rnd.nextInt(160)/10.0,
                    rnd.nextInt(7)!=0,rnd.nextInt(10)!=0,
                    rnd.nextInt(8)==0?0:20,rnd.nextInt(8)!=0));
            }
            WorldSnapshot snapshot=world(frame+1,items);
            int focus=1+rnd.nextInt(count+3);
            double range=2+rnd.nextInt(15);
            boolean visibleOnly=rnd.nextBoolean();
            IntPredicate allowed=id->id>0&&id<team.length&&!team[id];
            WorldSnapshot.Entity expect=null;
            double bestDistance=Double.POSITIVE_INFINITY;
            WorldSnapshot.Entity crosshair=null;
            for(WorldSnapshot.Entity player:items){
                double distance=player.x*player.x;
                if(player.id==focus&&player.alive&&player.health>0&&player.player&&
                   (!visibleOnly||player.visible)&&distance<=range*range&&!team[player.id])
                    crosshair=player;
                if(!player.alive||player.health<=0||!player.player||
                   (visibleOnly&&!player.visible)||distance>range*range||team[player.id])
                    continue;
                if(expect==null||distance<bestDistance||
                   (distance==bestDistance&&player.id<expect.id)){
                    expect=player;bestDistance=distance;
                }
            }
            WorldSnapshot.Entity actual=policy.select(snapshot,range,
                visibleOnly,"nearest",null,allowed);
            ok(actual==expect,"nearest eligible enemy exactly matches oracle "+frame);
            WorldSnapshot.Entity aimed=policy.select(snapshot,range,
                visibleOnly,"crosshair",focus,allowed);
            ok(aimed==crosshair,"aimed teammate never falls back to stranger "+frame);
            WorldSnapshot.Entity legacy=policy.select(snapshot,range,
                visibleOnly,"nearest",focus);
            WorldSnapshot.Entity includeAll=policy.select(snapshot,range,
                visibleOnly,"nearest",focus,id->true);
            ok(legacy==includeAll,"opt-in predicate leaves legacy nearest parity "+frame);
            ok(actual==null||!team[actual.id],"never select excluded team");
        }
    }
    private static void profileRestore()throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r267-hud-team");
        HostRuntime host=new HostRuntime(dir);
        ok(!host.targetIgnoreTeammates(),
            "legacy profile keeps all eligible players visible");
        ok(!((Setting.Bool)host.modules().get("hud.target")
            .settings().get("ignore_teammates")).get(),
            "new setting is available in ClickGUI and defaults false");
        host.uiActions().bool("hud.target","ignore_teammates");
        ok(host.targetIgnoreTeammates(),"user can enable optional HUD team filter");
        host.persist();host.close();
        HostRuntime restored=new HostRuntime(dir);
        ok(restored.targetIgnoreTeammates(),"HUD team policy persists across restart");
        ok(restored.uiActions().reset("hud.target","ignore_teammates"),
            "R2.65 setting reset restores legacy disabled default");
        ok(!restored.targetIgnoreTeammates(),"reset is immediately visible");
        restored.close();
    }
    public static void main(String[] args)throws Exception{
        edge();replay();profileRestore();
        ok(checks>120000,"substantial deterministic target HUD selection checks");
        System.out.println("TARGET_HUD_TEAM_FILTER_PASS checks="+checks);
    }
}
