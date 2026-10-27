package dev.ghostfusion.tests;

import dev.ghostfusion.features.AutoArmorPolicy;
import dev.ghostfusion.features.AutoArmorPolicy.Action;
import dev.ghostfusion.features.AutoArmorPolicy.Piece;
import dev.ghostfusion.features.AutoArmorPolicy.Step;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.core.ModuleRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure owner-thread armor decision contract, not proof of server confirmations.
 * Native GUI mapping/reobfuscation are checked by Forge CI separately.
 */
public final class AutoArmorTests {
    private static int assertions;
    private static void ok(boolean b,String msg) {
        assertions++;
        if(!b)throw new AssertionError(msg);
    }
    private static void rejects(Runnable work,String msg) {
        boolean failed=false;
        try{work.run();}catch(RuntimeException expected){failed=true;}
        ok(failed,msg);
    }
    private static Piece empty(){return new Piece(AutoArmorPolicy.EMPTY,0);}
    private static Piece other(){return new Piece(AutoArmorPolicy.OTHER,0);}
    private static Piece armor(int kind,double score){return new Piece(kind,score);}
    private static Piece[] storage() {
        Piece[] pieces=new Piece[36];
        for(int i=0;i<pieces.length;i++)pieces[i]=empty();
        return pieces;
    }
    private static Piece[] worn() {
        Piece[] pieces=new Piece[4];
        for(int i=0;i<pieces.length;i++)pieces[i]=empty();
        return pieces;
    }
    private static Action next(AutoArmorPolicy p,Piece[] bag,Piece[] worn,
                               boolean upgrade,boolean confirmed,long tick,long epoch) {
        return p.next(true,bag,worn,upgrade,6,5,confirmed,tick,epoch);
    }
    private static void ordinaryEquip() {
        AutoArmorPolicy policy=new AutoArmorPolicy();
        Piece[] bag=storage(),w=worn();
        bag[0]=armor(0,200);bag[1]=armor(0,400);
        bag[2]=armor(1,500);bag[3]=armor(2,450);bag[4]=armor(3,300);
        ok(next(policy,bag,w,false,false,0,1)==null,"opening armor inventory arms");
        ok(next(policy,bag,w,false,false,5,1)==null,"warmup never equips early");
        Action a=next(policy,bag,w,false,false,6,1);
        ok(a!=null && a.step==Step.EQUIP && a.containerSlot==10 &&
            a.armorKind==0 && a.candidateSlot==10,
            "best helmet selected from exact native storage slot10");
        ok(next(policy,bag,w,false,false,7,1)==null,
            "no second click before local player slot observation");
        bag[1]=empty();w[0]=armor(0,400);
        ok(next(policy,bag,w,false,true,8,1)==null,
            "equipped helmet confirmation still obeys minimum click spacing");
        a=next(policy,bag,w,false,false,11,1);
        ok(a!=null && a.step==Step.EQUIP &&
            a.containerSlot==11 && a.armorKind==1,
            "second armor category uses actual container slot11");
        bag[2]=empty();w[1]=armor(1,500);
        next(policy,bag,w,false,true,12,1);
        a=next(policy,bag,w,false,false,16,1);
        ok(a!=null && a.step==Step.EQUIP &&
            a.armorKind==2 && a.containerSlot==12,
            "leggings selector uses armor kind2");
        bag[3]=empty();w[2]=armor(2,450);
        next(policy,bag,w,false,true,17,1);
        a=next(policy,bag,w,false,false,21,1);
        ok(a!=null && a.step==Step.EQUIP &&
            a.armorKind==3 && a.containerSlot==13,
            "boots selector uses armor kind3");
        bag[4]=empty();w[3]=armor(3,300);
        next(policy,bag,w,false,true,22,1);
        ok(next(policy,bag,w,false,false,26,1)==null,
            "four completed categories do not re-equip or move gear");
    }
    private static void safeUpgrade() {
        AutoArmorPolicy p=new AutoArmorPolicy();
        Piece[] bag=storage(),w=worn();
        bag[4]=armor(0,250);bag[6]=armor(0,500);
        w[0]=armor(0,300);
        next(p,bag,w,false,false,0,1);
        ok(next(p,bag,w,false,false,6,1)==null,
            "fill-only default refuses replacing already worn armor");
        p.reset();
        next(p,bag,w,true,false,0,2);
        Action remove=next(p,bag,w,true,false,6,2);
        ok(remove!=null && remove.step==Step.REMOVE &&
            remove.containerSlot==5 && remove.armorKind==0 &&
            remove.candidateSlot==15,
            "upgrade first unequips original helmet into free storage");
        ok(next(p,bag,w,true,false,7,2)==null,
            "pending original helmet removal fences further actions");
        w[0]=empty();bag[35]=armor(0,300);
        ok(next(p,bag,w,true,true,8,2)==null,
            "observed removal cannot issue second click during cooldown");
        Action equip=next(p,bag,w,true,false,11,2);
        ok(equip!=null && equip.step==Step.EQUIP &&
            equip.containerSlot==15 && equip.armorKind==0,
            "only confirmed removal permits pinned upgrade candidate");
        bag[6]=empty();w[0]=armor(0,500);
        next(p,bag,w,true,true,12,2);
        ok(next(p,bag,w,true,false,16,2)==null,
            "upgraded helmet cannot be reequipped or oscillate");
    }
    private static void guardsAndFailures() {
        AutoArmorPolicy p=new AutoArmorPolicy();
        Piece[] bag=storage(),w=worn();
        bag[0]=armor(0,300);w[0]=other();
        next(p,bag,w,true,false,0,1);
        ok(next(p,bag,w,true,false,6,1)==null,
            "non-armor occupied helmet slot (pumpkin) is never treated empty");
        p.reset();
        w[0]=armor(0,100);
        for(int i=1;i<36;i++)bag[i]=other();
        next(p,bag,w,true,false,0,2);
        ok(next(p,bag,w,true,false,6,2)==null,
            "never unequip existing helmet when storage is full");
        bag[35]=empty();
        next(p,bag,w,true,false,0,3); // epoch changes while previous session
        Action a=next(p,bag,w,true,false,6,3);
        ok(a!=null && a.step==Step.REMOVE,
            "spare storage unlocks safe staged upgrade");
        for(int tick=7;tick<=20;tick++)
            ok(next(p,bag,w,true,false,tick,3)==null,
                "unconfirmed unequip never causes blind new equip or retries");
        w[0]=empty();bag[35]=armor(0,100);
        ok(next(p,bag,w,true,true,21,3)==null,
            "late local inventory change cannot revive timed-out session");
        p.reset();
        bag=storage();w=worn();bag[0]=armor(0,300);
        next(p,bag,w,false,false,0,4);
        ok(next(p,bag,w,false,false,6,4)!=null,"can issue initial equip");
        ok(p.next(false,bag,w,false,6,5,false,7,4)==null,
            "manual input cancels in-flight state");
        ok(next(p,bag,w,false,false,8,4)==null,
            "after manual interaction a fresh warmup is required");
        ok(next(p,bag,w,false,false,14,4)!=null,
            "only fresh eligible inventory session can rearm");
        ok(next(p,bag,w,false,false,0,5)==null,
            "new world epoch clears all pending operations");
        p.reset();
        ok(next(p,bag,w,false,false,0,5)==null,
            "Panic reset never replays pending quick-moves");
        rejects(()->new Piece(-2,0),"invalid negative armor kind rejected");
        rejects(()->new Piece(5,0),"invalid armor kind rejected");
        rejects(()->new Piece(-1,1),"empty scored armor rejected");
        rejects(()->new Piece(1,Double.NaN),"NaN protection rejected");
        rejects(()->p.next(true,new Piece[35],worn(),false,6,5,false,0,1),
            "invalid source storage layout rejected");
        rejects(()->p.next(true,storage(),new Piece[3],false,6,5,false,0,1),
            "invalid armor armor slot layout rejected");
        rejects(()->p.next(true,storage(),worn(),false,1,5,false,0,1),
            "too-short opening delay rejected");
        rejects(()->p.next(true,storage(),worn(),false,6,21,false,0,1),
            "too-long click interval rejected");
        rejects(()->p.next(true,storage(),worn(),false,6,5,false,-1,1),
            "negative tick rejected");
        rejects(()->p.next(true,storage(),worn(),false,6,5,false,0,-1),
            "negative epoch rejected");
    }
    private static void randomized() {
        final Random rng=new Random(3402027L);
        AutoArmorPolicy p=new AutoArmorPolicy();
        long epoch=0;
        Piece[] bag=storage(),w=worn();
        Action pending=null;
        int pendingAt=-1,accepted=0,removed=0,equipped=0;
        for(int tick=0;tick<40000;tick++) {
            if(tick%227==0) {
                epoch++;p.reset();
                bag=storage();w=worn();
                pending=null;pendingAt=-1;
                for(int kind=0;kind<4;kind++) {
                    int source=kind+rng.nextInt(5)*4;
                    bag[source]=armor(kind,300+rng.nextInt(300));
                    if(rng.nextBoolean())w[kind]=armor(kind,100+rng.nextInt(180));
                }
                // Occasionally block an empty armor slot with unrelated item
                if(rng.nextInt(8)==0)w[rng.nextInt(4)]=other();
            }
            boolean confirmed=pending!=null && tick>pendingAt &&
                rng.nextInt(4)==0;
            if(confirmed) {
                int kind=pending.armorKind;
                if(pending.step==Step.REMOVE) {
                    int emptySlot=-1;
                    for(int i=0;i<bag.length;i++)
                        if(bag[i].kind==AutoArmorPolicy.EMPTY){emptySlot=i;break;}
                    if(emptySlot>=0) {
                        bag[emptySlot]=w[kind];w[kind]=empty();
                    } else confirmed=false;
                } else {
                    int src=pending.candidateSlot-9;
                    w[kind]=bag[src];bag[src]=empty();
                }
                if(confirmed)pending=null;
            }
            Action result=p.next(true,bag,w,true,4,3,confirmed,tick,epoch);
            ok(result==null || (result.containerSlot>=5 &&
                result.containerSlot<=44 && result.armorKind>=0 &&
                result.armorKind<=3 && result.candidateSlot>=9 &&
                result.candidateSlot<=44),
               "40k invariant: native slot arguments in guarded range");
            if(result!=null) {
                accepted++;
                int kind=result.armorKind;
                Piece candidate=bag[result.candidateSlot-9];
                ok(candidate.kind==kind,
                    "only matching armor category can be selected");
                if(result.step==Step.REMOVE) {
                    removed++;
                    ok(result.containerSlot==5+kind && w[kind].kind==kind,
                        "removal always targets an occupied matching armor slot");
                    boolean free=false;
                    for(Piece item:bag)
                        if(item.kind==AutoArmorPolicy.EMPTY)free=true;
                    ok(free && candidate.score>w[kind].score,
                        "existing armor only removed for an actual higher-rated upgrade with space");
                } else {
                    equipped++;
                    ok(result.containerSlot==result.candidateSlot &&
                       w[kind].kind==AutoArmorPolicy.EMPTY,
                       "equipment transfer only occurs when armor destination is empty");
                }
                pending=result;pendingAt=tick;
            }
        }
        ok(accepted>0 && removed>0 && equipped>0,
            "randomized trace exercised unequips and equips");
    }
    private static void profileAndOwner() throws Exception {
        AutoArmorPolicy policy=new AutoArmorPolicy();
        AtomicBoolean denied=new AtomicBoolean(false);
        Thread offThread=new Thread(()->{
            try{next(policy,storage(),worn(),false,false,0,1);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        offThread.start();offThread.join();
        ok(denied.get(),"foreign thread cannot schedule inventory transactions");
        Path root=Files.createTempDirectory("ghostc-r340-autoarmor-");
        String id="inventory.autoarmor";
        HostRuntime h=new HostRuntime(root);
        ok(h.modules().get(id)!=null &&
           h.modules().get(id).state()==ModuleRegistry.State.DISABLED,
           "native armor module is real and disabled by default");
        ok(h.autoArmorOpenDelay()==6 && h.autoArmorClickDelay()==5 &&
           !h.autoArmorUpgrade(),"original inventory unchanged until enabled");
        h.worldPresence(true);
        h.uiActions().fraction(id,"open_delay_ticks",1);
        h.uiActions().fraction(id,"click_delay_ticks",0);
        h.uiActions().bool(id,"upgrade_existing");
        h.uiActions().toggle(id);
        ok(h.featureEnabled(id) && h.autoArmorOpenDelay()==20 &&
           h.autoArmorClickDelay()==2 && h.autoArmorUpgrade(),
           "F7 settings enable actual armor upgrades with bounded click pacing");
        h.close();
        HostRuntime restored=new HostRuntime(root);
        restored.worldPresence(true);
        ok(restored.featureEnabled(id) && restored.autoArmorUpgrade() &&
           restored.autoArmorOpenDelay()==20 && restored.autoArmorClickDelay()==2,
           "sealed profile persists armor settings and module state");
        restored.uiActions().toggle(id);
        restored.close();
        HostRuntime off=new HostRuntime(root);
        off.worldPresence(true);
        ok(!off.featureEnabled(id) && off.autoArmorUpgrade(),
           "explicit disable persists without destroying armor configuration");
        off.close();
    }
    public static void main(String[] args) throws Exception {
        ordinaryEquip();
        safeUpgrade();
        guardsAndFailures();
        randomized();
        profileAndOwner();
        ok(assertions>40000,"full 40k AutoArmor scenario replay");
        System.out.println("AUTO_ARMOR_PASS assertions="+assertions);
    }
}
