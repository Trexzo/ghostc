package dev.ghostfusion.tests;

import dev.ghostfusion.features.TargetHudPolicy;
import dev.ghostfusion.services.WorldSnapshot;
import dev.ghostfusion.host.HostRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** R2.57 snapshot-gated, persistent Target HUD selection modes. */
public final class TargetHudSelectionTests {
    private static int assertions;
    private static void ok(boolean condition,String reason){
        assertions++;
        if(!condition)throw new AssertionError(reason);
    }
    private static WorldSnapshot.Entity entity(int id,double x,boolean visible,
                                                boolean alive,double health,
                                                boolean player) {
        return new WorldSnapshot.Entity(id,x,0,0,health,alive,visible,player);
    }
    private static WorldSnapshot snapshot(List<WorldSnapshot.Entity> objects,long tick){
        return new WorldSnapshot(1,tick,0,0,0,0,0,objects);
    }
    private static void edgeCases() {
        TargetHudPolicy policy=new TargetHudPolicy();
        List<WorldSnapshot.Entity> objects=new ArrayList<WorldSnapshot.Entity>();
        objects.add(entity(4,2,true,true,20,true));
        objects.add(entity(6,4,true,true,20,true));
        objects.add(entity(8,3,false,true,20,true));
        objects.add(entity(10,3,true,true,0,true));
        objects.add(entity(12,3,true,false,20,true));
        objects.add(entity(14,3,true,true,20,false));
        objects.add(entity(16,9,true,true,20,true));
        WorldSnapshot snap=snapshot(objects,1);
        ok(policy.select(snap,6,true).id==4,"legacy nearest policy preserved");
        ok(policy.select(snap,6,true,"nearest",6).id==4,
            "nearest mode ignores live crosshair hint");
        ok(policy.select(snap,6,true,"crosshair",6).id==6,
            "crosshair selection prefers aimed at eligible player");
        ok(policy.select(snap,6,true,"crosshair",null)==null,
            "no reticle hit does not fall back to nearest");
        ok(policy.select(snap,6,true,"crosshair",999)==null,
            "unpublished/de-spawned target never displayed");
        ok(policy.select(snap,6,true,"crosshair",8)==null,
            "visible-only setting rejects hidden reticle player");
        ok(policy.select(snap,6,false,"crosshair",8).id==8,
            "opt-out of visible-only is honored");
        ok(policy.select(snap,6,true,"crosshair",10)==null,
            "dead-health target rejected");
        ok(policy.select(snap,6,true,"crosshair",12)==null,
            "dead-flag target rejected");
        ok(policy.select(snap,6,true,"crosshair",14)==null,
            "non-player target rejected");
        ok(policy.select(snap,6,true,"crosshair",16)==null,
            "out-of-range target rejected");
        ok(policy.select(snap,6,true,"crosshair",4).id==4,
            "nearer aimed-at player admitted");
        ok(policy.select(null,6,true,"crosshair",6)==null,
            "world transition cannot paint a stale target");
        boolean threw=false;
        try{policy.select(snap,6,true,"invalid",4);}
        catch(IllegalArgumentException expected){threw=true;}
        ok(threw,"unknown selection mode fails closed");
    }
    private static void replay(){
        TargetHudPolicy policy=new TargetHudPolicy();
        Random random=new Random(2572026L);
        for(int frame=0;frame<30000;frame++){
            List<WorldSnapshot.Entity> players=new ArrayList<WorldSnapshot.Entity>();
            int amount=1+random.nextInt(24);
            for(int id=0;id<amount;id++){
                double distance=random.nextInt(121)*.125;
                players.add(entity(id+1,distance,random.nextBoolean(),
                    random.nextInt(7)!=0,random.nextInt(9)==0?0:20,
                    random.nextInt(8)!=0));
            }
            WorldSnapshot snap=snapshot(players,frame+1);
            int focused=random.nextInt(amount+8)+1;
            boolean visibleOnly=random.nextBoolean();
            double range=2+random.nextInt(13);
            WorldSnapshot.Entity expected=null;
            for(WorldSnapshot.Entity p:players) {
                if(p.id!=focused)continue;
                double dx=p.x-snap.playerX;
                if(p.alive && p.health>0 && p.player &&
                   (!visibleOnly||p.visible) && dx*dx<=range*range)
                    expected=p;
            }
            WorldSnapshot.Entity actual=policy.select(snap,range,visibleOnly,
                "crosshair",focused);
            ok(actual==expected,"crosshair policy matches independent gate "+frame);
            WorldSnapshot.Entity nearest=policy.select(snap,range,visibleOnly);
            WorldSnapshot.Entity same=policy.select(snap,range,visibleOnly,"nearest",focused);
            ok(same==nearest,"nearest source/overload parity "+frame);
        }
    }
    private static void persisted() throws Exception{
        Path dir=Files.createTempDirectory("ghostc-r257-selection-");
        HostRuntime host=new HostRuntime(dir);
        ok("nearest".equals(host.targetSelection()),"old profiles default to nearest");
        host.uiActions().cycle("hud.target","selection");
        ok("crosshair".equals(host.targetSelection()),
            "typed ClickGUI action selects crosshair-only");
        host.persist();host.close();
        HostRuntime restored=new HostRuntime(dir);
        ok("crosshair".equals(restored.targetSelection()),
            "new selection saved and restored through existing profile");
        restored.uiActions().cycle("hud.target","selection");
        ok("nearest".equals(restored.targetSelection()),
            "GUI cycles back to legacy nearest");
        restored.persist();restored.close();
    }
    public static void main(String[] args)throws Exception {
        edgeCases();replay();persisted();
        ok(assertions>60000,"substantial deterministic selection tests");
        System.out.println("TARGET_HUD_SELECTION_PASS assertions="+assertions);
    }
}
