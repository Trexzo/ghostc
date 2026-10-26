package dev.ghostfusion.tests;

import dev.ghostfusion.features.ChestStealerPolicy;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.ModuleRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** The actual Forge controller is tested by Forge JDK8 build/wiring checks.
 * Pure policy and profiles cannot certify server inventory transaction ACKs. */
public final class ChestStealerTests {
    private static int checks;
    private static void ok(boolean result,String message) {
        checks++;
        if(!result)throw new AssertionError(message);
    }
    private static void rejects(Runnable work,String message) {
        boolean failed=false;
        try{work.run();}catch(RuntimeException expected){failed=true;}
        ok(failed,message);
    }
    private static int next(ChestStealerPolicy policy,boolean[] slots,
                            boolean changed,long tick,int window,long epoch) {
        return policy.next(true,window,slots.length,4,3,slots,changed,tick,epoch);
    }
    private static void transitions() {
        ChestStealerPolicy p=new ChestStealerPolicy();
        boolean[] chest=new boolean[27];
        chest[0]=true;chest[4]=true;chest[26]=true;
        ok(next(p,chest,false,0,1,1)==-1,"open tick never shifts items");
        ok(next(p,chest,false,1,1,1)==-1,"contents may arrive later than GUI");
        ok(next(p,chest,false,3,1,1)==-1,"minimum opening delay");
        ok(next(p,chest,false,4,1,1)==0,"first eligible native chest slot selected");
        ok(next(p,chest,false,5,1,1)==-1,"pending transaction never repeated");
        ok(next(p,chest,true,5,1,1)==-1,"observed local change clears pending, enforces pacing");
        ok(next(p,chest,false,6,1,1)==-1,"pacing still enforced");
        ok(next(p,chest,false,7,1,1)==4,"second valid source selected after 3 ticks");
        ok(next(p,chest,true,8,1,1)==-1,"second slot observation confirmed");
        ok(next(p,chest,false,10,1,1)==26,"round-robin visits end of chest");
        ok(next(p,chest,true,11,1,1)==-1,"end slot completed");
        chest[0]=false;chest[4]=false;chest[26]=false;
        ok(next(p,chest,false,13,1,1)==-1,"empty chest no longer clicks");
        chest[1]=true;
        ok(next(p,chest,false,14,1,1)==1,"new slot may appear after contents update");
        for(long tick=15;tick<=27;tick++)
            ok(next(p,chest,false,tick,1,1)==-1,
                "unchanged source never repeated or silently inferred accepted");
        ok(next(p,chest,true,28,1,1)==-1,
            "late ACK after timeout does not reauthorize same window");
        ok(next(p,chest,false,30,2,1)==-1,"new window resets stale denial");
        ok(next(p,chest,false,34,2,1)==1,
            "new window starts fresh after warmup");
        ok(p.next(false,2,27,4,3,chest,false,35,1)==-1,
            "manual click / focus interference clears pending state");
        ok(next(p,chest,false,36,2,1)==-1,"resume requires new warmup");
        ok(next(p,chest,false,40,2,1)==1,"new safe window admission");
        ok(next(p,chest,false,0,2,2)==-1,"world epoch clears pending");
        ok(next(p,chest,false,4,2,2)==1,"new world processes only new chest");
        p.reset();
        ok(next(p,chest,false,5,2,2)==-1,"F6 panic reset never replays clicks");
        rejects(()->p.next(true,2,27,1,3,chest,false,0,2),
            "opening delay below 2 rejected");
        rejects(()->p.next(true,2,27,4,1,chest,false,0,2),
            "click delay below 2 rejected");
        rejects(()->p.next(true,2,55,4,3,new boolean[55],false,0,2),
            "oversized chest inventory rejected");
        rejects(()->p.next(true,256,27,4,3,chest,false,0,2),
            "invalid Minecraft window ID rejected");
        rejects(()->p.next(true,2,27,4,3,new boolean[26],false,0,2),
            "candidate vector length mismatch rejected");
        rejects(()->p.next(true,2,27,4,3,chest,false,-1,2),
            "negative session tick rejected");
        rejects(()->p.next(true,2,27,4,3,chest,false,0,-1),
            "negative world generation rejected");
    }
    private static void randomized() {
        Random rng=new Random(3382026L);
        ChestStealerPolicy p=new ChestStealerPolicy();
        boolean[] slots=new boolean[54];
        int window=1;
        long epoch=1;
        int since=-1;
        int lastClick=-100;
        int pending=-1;
        int pendingAt=-1;
        boolean blocked=false;
        boolean open=false;
        boolean confirm=false;
        for(int tick=0;tick<32000;tick++) {
            // Deterministic periodic reopen/disconnect forces provenance
            // checks and avoids treating windows with reused IDs as a match.
            if(tick%197==0) {
                p.reset();
                window=(window%100)+1;
                epoch++;
                since=tick;lastClick=-100;pending=-1;
                pendingAt=-1;blocked=false;open=true;
                Arrays.fill(slots,false);
            }
            if(rng.nextInt(8)==0)
                slots[rng.nextInt(slots.length)]=true;
            if(rng.nextInt(12)==0)
                slots[rng.nextInt(slots.length)]=false;
            confirm=pending>=0 && rng.nextInt(3)==0;
            if(confirm) slots[pending]=false;
            int selected=next(p,slots,confirm,tick,window,epoch);
            // Model one in-flight request and exact pacing. Failed
            // confirmation blocks the whole window by 12 ticks.
            if(open) {
                open=false;
                ok(selected==-1,"new window never clicks immediately");
                continue;
            }
            if(pending>=0) {
                if(confirm) {
                    pending=-1;pendingAt=-1;
                } else if(tick-pendingAt>=12) {
                    blocked=true;
                }
            }
            boolean canSelect=!blocked && pending<0 &&
                tick-since>=4 && tick-lastClick>=3;
            if(selected>=0) {
                ok(canSelect && selected<slots.length && slots[selected],
                    "32k native slot selection respects full safe admission");
                lastClick=tick;pending=selected;pendingAt=tick;
            } else if(!canSelect) {
                ok(selected==-1,"32k blocked/cooldown frames remain no-op");
            }
        }
    }
    private static void profileAndOwner() throws Exception {
        ChestStealerPolicy policy=new ChestStealerPolicy();
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread foreign=new Thread(()->{
            try {next(policy,new boolean[27],false,0,1,1);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        foreign.start();foreign.join();
        ok(denied.get(),"chest policy owner thread");
        Path root=Files.createTempDirectory("ghostc-r338-chest-");
        HostRuntime h=new HostRuntime(root);
        String id="inventory.cheststealer";
        ok(h.modules().get(id)!=null &&
           h.modules().get(id).state()==ModuleRegistry.State.DISABLED,
            "registered real chest module defaults off");
        ok(h.chestStealerOpenDelay()==6 && h.chestStealerClickDelay()==4 &&
           !h.chestStealerAllowCustom(),"conservative defaults");
        h.worldPresence(true);
        h.uiActions().fraction(id,"open_delay_ticks",1.0);
        h.uiActions().fraction(id,"click_delay_ticks",0.0);
        h.uiActions().bool(id,"allow_custom_names");
        h.uiActions().toggle(id);
        ok(h.featureEnabled(id) && h.chestStealerOpenDelay()==20 &&
           h.chestStealerClickDelay()==2 && h.chestStealerAllowCustom(),
            "real GUI settings and enable mutation");
        h.close();
        HostRuntime loaded=new HostRuntime(root);
        loaded.worldPresence(true);
        ok(loaded.featureEnabled(id) && loaded.chestStealerOpenDelay()==20 &&
           loaded.chestStealerClickDelay()==2 && loaded.chestStealerAllowCustom(),
            "native chest settings and module persisted");
        loaded.uiActions().toggle(id);
        loaded.close();
        HostRuntime disabled=new HostRuntime(root);
        disabled.worldPresence(true);
        ok(!disabled.featureEnabled(id) && disabled.chestStealerOpenDelay()==20,
            "explicit disable and unchanged settings roundtrip");
        disabled.close();
    }
    public static void main(String[] args) throws Exception {
        transitions();
        randomized();
        profileAndOwner();
        ok(checks>25000,"32k chest window/single-flight randomized policy ticks");
        System.out.println("CHEST_STEALER_PASS assertions="+checks);
    }
}
