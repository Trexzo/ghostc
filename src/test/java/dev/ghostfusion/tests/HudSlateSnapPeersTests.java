package dev.ghostfusion.tests;

import dev.ghostfusion.ui.HudEditorControls;
import dev.ghostfusion.ui.HudPreviewGeometry;
import dev.ghostfusion.ui.HudSlateSnapPeers;
import dev.ghostfusion.ui.HudSnapPolicy;
import java.util.Random;

/** 40k deterministic ghost-snap, order, completeness and geometry checks. */
public final class HudSlateSnapPeersTests {
    private static long checks;
    private static void ok(boolean b,String name) {
        checks++;if(!b)throw new AssertionError(name);
    }
    private static void invalid(Runnable r,String name) {
        boolean threw=false;
        try{r.run();}catch(IllegalArgumentException expected){threw=true;}
        ok(threw,name);
    }
    private static HudPreviewGeometry.Rect rect(int x,int y,int w,int h) {
        return new HudPreviewGeometry(x,y,w,h,w,h).canvas;
    }
    public static void main(String[] args) {
        HudEditorControls.Item[] items=HudEditorControls.Item.values();
        int all=HudSlateSnapPeers.allBits();
        ok(all==(1<<items.length)-1,"full bit mask covers native tab order");
        HudPreviewGeometry.Rect canvas=rect(0,0,300,200);
        HudPreviewGeometry.Rect moved=rect(104,80,20,20);
        HudPreviewGeometry.Rect ghost=rect(100,77,20,20);
        HudPreviewGeometry.Rect[] peers=new HudPreviewGeometry.Rect[items.length];
        peers[0]=rect(230,140,25,20);
        peers[1]=null; // selected KEYS
        peers[2]=ghost; // CPS disabled, must not attract the cursor
        peers[3]=rect(210,150,18,22);
        peers[4]=rect(250,130,40,30);
        HudPreviewGeometry.Rect[] inactive=HudSlateSnapPeers.peers(
            HudEditorControls.Item.KEYS,peers,0);
        HudSnapPolicy.Result free=HudSnapPolicy.snap(canvas,moved,inactive,5);
        ok(inactive.length==0 && free.x==104 && free.y==80 &&
           free.guideX==HudSnapPolicy.NO_GUIDE &&
           free.guideY==HudSnapPolicy.NO_GUIDE,
           "inactive HUD placeholder never attracts the active drag");
        HudPreviewGeometry.Rect[] active=HudSlateSnapPeers.peers(
            HudEditorControls.Item.KEYS,peers,1<<items[2].ordinal());
        HudSnapPolicy.Result attracted=HudSnapPolicy.snap(canvas,moved,active,5);
        ok(active.length==1&&active[0]==ghost &&
           attracted.x==100&&attracted.y==77,
           "enabled overlapping peer retains native magnetic guide");
        HudPreviewGeometry.Rect[] legacy=HudSlateSnapPeers.peers(
            HudEditorControls.Item.KEYS,peers,all);
        ok(legacy.length==items.length-1&&legacy[0]==peers[0]&&
           legacy[1]==ghost&&legacy[2]==peers[3]&&legacy[3]==peers[4],
           "legacy sees all other previews in identical priority order");
        invalid(()->HudSlateSnapPeers.peers(null,peers,all),
            "selected item required");
        invalid(()->HudSlateSnapPeers.peers(items[1],null,all),
            "candidate array required");
        invalid(()->HudSlateSnapPeers.peers(items[1],new HudPreviewGeometry.Rect[2],0),
            "native tab-length required");
        invalid(()->HudSlateSnapPeers.peers(items[1],peers,-1),
            "negative visibility mask rejected");
        invalid(()->HudSlateSnapPeers.peers(items[1],peers,1<<9),
            "unknown visibility bits rejected");
        HudPreviewGeometry.Rect[] own=peers.clone();
        own[1]=rect(30,30,8,8);
        invalid(()->HudSlateSnapPeers.peers(items[1],own,all),
            "dragged widget never snaps to itself");
        HudPreviewGeometry.Rect[] absent=peers.clone();
        absent[3]=null;
        invalid(()->HudSlateSnapPeers.peers(items[1],absent,0),
            "even invisible peer geometry must be present and valid");

        Random rng=new Random(1112026L);
        for(int i=0;i<40000;i++) {
            HudEditorControls.Item selected=items[rng.nextInt(items.length)];
            HudPreviewGeometry.Rect[] candidate=
                new HudPreviewGeometry.Rect[items.length];
            for(int j=0;j<candidate.length;j++)
                if(j!=selected.ordinal())candidate[j]=rect(
                    rng.nextInt(270),rng.nextInt(170),
                    1+rng.nextInt(30),1+rng.nextInt(25));
            int enabled=rng.nextInt(all+1);
            HudPreviewGeometry.Rect[] chosen=HudSlateSnapPeers.peers(
                selected,candidate,enabled);
            int expected=0;
            for(int j=0;j<items.length;j++)
                if(j!=selected.ordinal()&&(enabled&(1<<j))!=0)expected++;
            ok(chosen.length==expected,"only real visible peers snap "+i);
            int at=0;
            for(int j=0;j<items.length;j++)
                if(j!=selected.ordinal()&&(enabled&(1<<j))!=0)
                    ok(chosen[at++]==candidate[j],
                        "stable tab order and original geometry "+i);
            HudPreviewGeometry.Rect moving=rect(
                rng.nextInt(270),rng.nextInt(170),10,10);
            HudSnapPolicy.Result snap=HudSnapPolicy.snap(
                canvas,moving,chosen,rng.nextInt(7));
            ok(snap.x>=0&&snap.y>=0 &&
               snap.x+moving.width<=canvas.width &&
               snap.y+moving.height<=canvas.height,
               "all Slate snap positions remain inside canvas "+i);
            HudPreviewGeometry.Rect[] unchanged=HudSlateSnapPeers.peers(
                selected,candidate,all);
            ok(unchanged.length==items.length-1,
                "legacy still admits all non-dragged widgets "+i);
        }
        System.out.println("HUD_SLATE_SNAP_PEERS_PASS checks="+checks);
    }
}
