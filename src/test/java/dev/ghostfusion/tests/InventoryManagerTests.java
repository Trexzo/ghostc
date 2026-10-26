package dev.ghostfusion.tests;

import dev.ghostfusion.features.InventoryManagerPolicy;
import dev.ghostfusion.features.InventoryManagerPolicy.Slot;
import dev.ghostfusion.features.InventoryManagerPolicy.Swap;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.ModuleRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/** Source-only contract; actual Forge GuiInventory vanilla windowClick
 * semantics and multiplayer slot synchronization remain native acceptance. */
public final class InventoryManagerTests {
    private static int assertions;
    private static void ok(boolean yes,String reason) {
        assertions++;
        if(!yes)throw new AssertionError(reason);
    }
    private static void rejects(Runnable call,String reason) {
        boolean rejected=false;
        try{call.run();}catch(RuntimeException expected){rejected=true;}
        ok(rejected,reason);
    }
    private static Slot slot(int kind,double quality) {
        return new Slot(kind,quality);
    }
    private static Slot[] empty() {
        Slot[] s=new Slot[36];
        for(int i=0;i<s.length;i++)s[i]=slot(InventoryManagerPolicy.EMPTY,0);
        return s;
    }
    private static Swap next(InventoryManagerPolicy policy,Slot[] stacks,
                            boolean upgrade,boolean confirmed,long tick,long epoch) {
        return policy.next(true,stacks,upgrade,6,5,confirmed,tick,epoch);
    }
    private static void hotbarContracts() {
        InventoryManagerPolicy p=new InventoryManagerPolicy();
        Slot[] stacks=empty();
        stacks[0]=slot(InventoryManagerPolicy.SWORD,6);
        stacks[1]=slot(InventoryManagerPolicy.PICKAXE,208);
        stacks[2]=slot(InventoryManagerPolicy.AXE,110);
        stacks[3]=slot(InventoryManagerPolicy.SHOVEL,112);
        ok(next(p,stacks,false,false,0,1)==null,"GUI opens without immediate swaps");
        ok(next(p,stacks,false,false,5,1)==null,"contents warmup bounded");
        Swap w=next(p,stacks,false,false,6,1);
        ok(w!=null && w.sourceContainerSlot==9 && w.targetHotbarIndex==0,
            "native ContainerPlayer source 9 becomes hotbar index 0");
        ok(next(p,stacks,false,false,7,1)==null,
            "never submits second click until both prior slots observed changed");
        stacks[0]=slot(InventoryManagerPolicy.EMPTY,0);
        stacks[27]=slot(InventoryManagerPolicy.SWORD,6);
        ok(next(p,stacks,false,true,8,1)==null,
            "two-sided swap confirmation still enforces click spacing");
        ok(next(p,stacks,false,false,10,1)==null,
            "no early second transfer");
        w=next(p,stacks,false,false,11,1);
        ok(w!=null && w.sourceContainerSlot==10 && w.targetHotbarIndex==1,
            "pickaxe goes to actual hotbar slot1");
        stacks[1]=slot(InventoryManagerPolicy.EMPTY,0);
        stacks[28]=slot(InventoryManagerPolicy.PICKAXE,208);
        next(p,stacks,false,true,12,1);
        w=next(p,stacks,false,false,16,1);
        ok(w!=null && w.sourceContainerSlot==11 && w.targetHotbarIndex==2,
            "axe goes to hotbar slot2");
        stacks[2]=slot(InventoryManagerPolicy.EMPTY,0);
        stacks[29]=slot(InventoryManagerPolicy.AXE,110);
        next(p,stacks,false,true,17,1);
        w=next(p,stacks,false,false,21,1);
        ok(w!=null && w.sourceContainerSlot==12 && w.targetHotbarIndex==3,
            "shovel goes to hotbar slot3");
        p.reset();

        stacks=empty();
        stacks[4]=slot(InventoryManagerPolicy.SWORD,12);
        stacks[27]=slot(InventoryManagerPolicy.OTHER,0);
        next(p,stacks,false,false,0,2);
        ok(next(p,stacks,false,false,6,2)==null,
            "unrelated hotbar food/blocks can NEVER be treated as empty");
        ok(next(p,stacks,true,false,7,2)==null,
            "optional upgrade cannot overwrite unrelated hotbar items");
        p.reset();
        stacks[27]=slot(InventoryManagerPolicy.SWORD,6);
        next(p,stacks,false,false,0,3);
        ok(next(p,stacks,false,false,6,3)==null,
            "fill-only default never replaces an existing sword");
        w=next(p,stacks,true,false,7,3);
        ok(w!=null && w.sourceContainerSlot==13 && w.targetHotbarIndex==0,
            "explicit upgrade mode selects superior sword in inventory");
        p.reset();

        stacks=empty();
        stacks[0]=slot(InventoryManagerPolicy.SWORD,10);
        stacks[27]=slot(InventoryManagerPolicy.SWORD,10.00000001);
        next(p,stacks,true,false,0,4);
        ok(next(p,stacks,true,false,6,4)==null,
            "equal ranked sword never swaps back and forth");
        p.reset();
        stacks=empty();
        stacks[30]=slot(InventoryManagerPolicy.SWORD,50);
        next(p,stacks,false,false,0,5);
        ok(next(p,stacks,false,false,6,5)==null,
            "hotbar reserved slot4 cannot be raided for sword category0");
        p.reset();
        stacks[31]=slot(InventoryManagerPolicy.SWORD,50);
        next(p,stacks,false,false,0,6);
        w=next(p,stacks,false,false,6,6);
        ok(w!=null && w.sourceContainerSlot==40 && w.targetHotbarIndex==0,
            "unreserved hotbar index4 may be moved to intended sword slot");
        ok(next(p,stacks,false,false,7,6)==null,
            "unacknowledged attempted swap does not duplicate");
        for(int t=8;t<=25;t++)
            ok(next(p,stacks,false,false,t,6)==null,
                "failed inventory swap blocks current owner window without retry");
        ok(next(p,stacks,false,true,26,6)==null,
            "late local mutation after 12-tick failure cannot unfreeze window");
        ok(next(p,stacks,false,false,0,7)==null,
            "world epoch invalidates prior pending and resets warmup");
        p.reset();
        ok(next(p,stacks,false,false,0,7)==null,
            "F6 Panic reset never inherits source or destination");
        rejects(()->new Slot(6,1),"unknown kind rejected");
        rejects(()->new Slot(InventoryManagerPolicy.EMPTY,1),
            "nonempty score for empty stack rejected");
        rejects(()->new Slot(InventoryManagerPolicy.SWORD,Double.NaN),
            "nonfinite score rejected");
        rejects(()->p.next(true,new Slot[35],false,6,5,false,0,1),
            "wrong Minecraft inventory length rejected");
        rejects(()->p.next(true,empty(),false,1,5,false,0,1),
            "opening delay below minimum rejected");
        rejects(()->p.next(true,empty(),false,6,21,false,0,1),
            "click interval beyond bounds rejected");
        rejects(()->p.next(true,empty(),false,6,5,false,-1,1),
            "negative session tick rejected");
    }
    private static void randomized() {
        final int iterations=32000;
        final Random rng=new Random(3392026L);
        InventoryManagerPolicy p=new InventoryManagerPolicy();
        long epoch=0;
        int accepted=0;
        for(int tick=0;tick<iterations;tick++) {
            if(tick%173==0) {
                p.reset();
                epoch++;
            }
            Slot[] stacks=empty();
            boolean upgrade=rng.nextBoolean();
            for(int j=0;j<stacks.length;j++) {
                int k=rng.nextInt(9);
                int kind=k<=4?k:InventoryManagerPolicy.OTHER;
                stacks[j]=slot(kind,kind==InventoryManagerPolicy.EMPTY?0:
                    kind==InventoryManagerPolicy.OTHER?0:
                    (1+rng.nextInt(500))/10.0);
            }
            boolean ack=rng.nextInt(3)==0;
            boolean eligible=rng.nextInt(17)!=0;
            Swap w=p.next(eligible,stacks,upgrade,4,3,ack,tick,epoch);
            ok(w==null || w.sourceContainerSlot>=9 && w.sourceContainerSlot<=44 &&
                w.targetHotbarIndex>=0 && w.targetHotbarIndex<=3,
                "32k actual vanilla hotbar swap parameters or safe no-op");
            if(w!=null) {
                accepted++;
                int target=27+w.targetHotbarIndex;
                int source=w.sourceContainerSlot-9;
                ok(source!=target && (source<27 || source>30),
                    "never operate reserved unrelated hotbar positions");
                ok(stacks[source].kind==w.targetHotbarIndex+1,
                    "only correct sword/pickaxe/axe/shovel slot category can move");
                ok(stacks[target].kind==InventoryManagerPolicy.EMPTY ||
                    upgrade && stacks[target].kind==stacks[source].kind &&
                    stacks[source].quality>stacks[target].quality,
                    "no replacement of other items, no weaker upgrade");
            }
        }
        ok(accepted>0,"randomized 32k ticks include actual admitted inventory swaps");
    }
    private static void profileAndOwner() throws Exception {
        InventoryManagerPolicy p=new InventoryManagerPolicy();
        AtomicBoolean refused=new AtomicBoolean(false);
        Thread outsider=new Thread(()->{
            try{next(p,empty(),false,false,0,1);}
            catch(IllegalStateException expected){refused.set(true);}
        });
        outsider.start();outsider.join();
        ok(refused.get(),"inventory manager remains owner-thread only");
        Path dir=Files.createTempDirectory("ghostc-r339-inventory-manager-");
        String id="inventory.manager";
        HostRuntime h=new HostRuntime(dir);
        ok(h.modules().get(id)!=null &&
           h.modules().get(id).state()==ModuleRegistry.State.DISABLED,
           "new Inventory Manager module is registered disabled by default");
        ok(h.inventoryManagerOpenDelay()==6 &&
           h.inventoryManagerClickDelay()==5 &&
           !h.inventoryManagerUpgrade(),
           "conservative default warmup, pacing and no overwriting");
        h.worldPresence(true);
        h.uiActions().fraction(id,"open_delay_ticks",1.0);
        h.uiActions().fraction(id,"click_delay_ticks",0.0);
        h.uiActions().bool(id,"upgrade_existing");
        h.uiActions().toggle(id);
        ok(h.featureEnabled(id) &&
           h.inventoryManagerOpenDelay()==20 &&
           h.inventoryManagerClickDelay()==2 &&
           h.inventoryManagerUpgrade(),
           "GUI controls operate real sorter modes and delay");
        h.close();
        HostRuntime loaded=new HostRuntime(dir);
        loaded.worldPresence(true);
        ok(loaded.featureEnabled(id) &&
           loaded.inventoryManagerOpenDelay()==20 &&
           loaded.inventoryManagerClickDelay()==2 &&
           loaded.inventoryManagerUpgrade(),
           "profile roundtrip persists real Inventory Manager state");
        loaded.uiActions().toggle(id);
        loaded.close();
        HostRuntime off=new HostRuntime(dir);
        off.worldPresence(true);
        ok(!off.featureEnabled(id) && off.inventoryManagerUpgrade(),
           "module off persists without resetting numeric and mode settings");
        off.close();
    }
    public static void main(String[] args) throws Exception {
        hotbarContracts();
        randomized();
        profileAndOwner();
        ok(assertions>32000,"deterministic inventory mode replay");
        System.out.println("INVENTORY_MANAGER_PASS assertions="+assertions);
    }
}
