package dev.ghostfusion.tests;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.83: an incomplete native ATTACK can already have reached Minecraft
 * even though no successful attack receipt exists. Block Hit's dependent
 * USE must be excluded on that frame and through unresolved ATTACK state.
 *
 * No game, server acknowledgement, hit or actual packet is simulated here.
 */
public final class BlockHitAttackUncertaintyTests {
    private static long checks;
    private static void ok(boolean pass,String why) {
        checks++;
        if(!pass)throw new AssertionError(why);
    }
    private static boolean stage(BlockHitPolicy block,long tick,long epoch) {
        return block.preview(true,true,false,true,true,false,false,
            2,0,"interval",tick,epoch);
    }
    private static void exact() throws Exception {
        Object world=new Object(),player=new Object();
        NativePulseSettlement gate=new NativePulseSettlement();
        BlockHitPolicy block=new BlockHitPolicy();
        ok(gate.reconcileNative(1,world,player),
            "native world/player proof establishes session");
        ok(!gate.attackConflictsWithBlockHit(1,0),
            "no ATTACK has started; independent Block Hit cadence is permitted");
        ok(!stage(block,0,1),"initial physical hold belongs to vanilla");
        ok(stage(block,2,1),"Block Hit pulse is staged on due interval");

        // Native ATTACK was delivered, then a downstream Hit Select or
        // TriggerBot receipt failed. No success flag can prove it absent.
        gate.begin(Lane.ATTACK,1,2);
        gate.nativeReturned(Lane.ATTACK,1,2);
        ok(gate.attackConflictsWithBlockHit(1,2),
            "a returned but uncommitted ATTACK blocks same-tick USE");
        gate.abandon(Lane.ATTACK,1,2);
        ok(gate.phase(Lane.ATTACK,1)==Phase.UNCERTAIN,
            "partial successful Minecraft key pulse quarantines ATTACK");
        ok(gate.attackConflictsWithBlockHit(1,2),
            "ATTACK uncertainty still blocks same-frame Block Hit");
        block.cancelPrepared();
        ok(stage(block,3,1),
            "denied USE does not consume Block Hit interval");
        ok(gate.attackConflictsWithBlockHit(1,3),
            "unresolved ATTACK continues blocking dependent Block Hit");
        block.cancelPrepared();
        ok(gate.admitNative(Lane.USE,1,world,player),
            "unrelated USE settlement lane retains its original independence");
        ok(gate.attackConflictsWithBlockHit(1,4),
            "a live Block Hit controller still enforces dependent-output lock");

        // Panic / reconnect with same native identity must not recover.
        ok(gate.reconcileNative(2,world,player),
            "same native player/world may be observed after numeric epoch change");
        ok(gate.attackConflictsWithBlockHit(2,0),
            "same-object numeric epoch does not clear unresolved ATTACK");
        ok(!gate.reconcileNative(2,new Object(),player),
            "same-generation world replacement cannot authorize output");
        ok(gate.attackConflictsWithBlockHit(2,1),
            "same-generation identity swap cannot bypass quarantine");
        Object newWorld=new Object();
        ok(gate.reconcileNative(3,newWorld,player),
            "new generation plus different native world proves recovery");
        ok(!gate.attackConflictsWithBlockHit(3,0),
            "verified new native world clears dependent USE block");
        block.reset();
        ok(!stage(block,0,3),
            "fresh session requires initial vanilla hold");
        ok(stage(block,2,3),"new world permits normal Block Hit interval");
        ok(!gate.attackConflictsWithBlockHit(3,2),
            "no new attack attempt means use can commit");
        block.committed(2,3);
        ok(!stage(block,3,3),"committed Block Hit respects own interval");

        // Even a completely successful native ATTACK cannot be followed
        // by a second synthetic USE pulse during the same tick.
        NativePulseSettlement success=new NativePulseSettlement();
        Object successWorld=new Object();
        ok(success.reconcileNative(7,successWorld,player),
            "completed ATTACK test pins exact native world");
        success.begin(Lane.ATTACK,7,5);
        success.nativeReturned(Lane.ATTACK,7,5);
        success.committed(Lane.ATTACK,7,5);
        success.abandon(Lane.ATTACK,7,5);
        ok(success.phase(Lane.ATTACK,7)==Phase.IDLE,
            "completed native attack resets lane to idle");
        ok(success.attackConflictsWithBlockHit(7,5),
            "completed attack still reserves its physical output frame");
        ok(!success.attackConflictsWithBlockHit(7,6),
            "completed attack does not impose an invented extra-tick cooldown");

        // A merely scheduled and rejected attack never began. No native
        // receipt is invented, and Block Hit's own gating stays authoritative.
        NativePulseSettlement noAttack=new NativePulseSettlement();
        ok(noAttack.reconcileNative(8,new Object(),player),
            "rejected attack input has fresh native session");
        ok(!noAttack.attackConflictsWithBlockHit(8,5),
            "unstarted/rejected ATTACK request cannot falsely reserve USE");
        ok(noAttack.admitNative(Lane.USE,8,new Object(),player)==false,
            "untrusted same-epoch native player/world swap still fails closed");

        // Block Hit's post_attack mode still arms only from a *confirmed*
        // Clicker dispatch and issues USE in a later eligible tick.
        BlockHitPolicy post=new BlockHitPolicy();
        ok(!post.preview(true,true,false,true,true,false,true,
            2,0,"post_attack",5,7),
            "confirmed native Clicker pulse arms a later post-attack Block Hit");
        ok(post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",6,7),
            "post-attack next-tick USE remains stageable after settled ATTACK");
        ok(!success.attackConflictsWithBlockHit(7,6),
            "no artificial cooldown blocks next-tick post-attack USE");
        post.committed(6,7);
        ok(!post.preview(true,true,false,true,true,false,false,
            2,0,"post_attack",7,7),
            "post-attack event is consumed by one committed USE");

        AtomicBoolean denied=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{success.attackConflictsWithBlockHit(7,7);}
            catch(IllegalStateException expected){denied.set(true);}
        });
        foreign.start();foreign.join();
        ok(denied.get(),"Netty/foreign thread cannot inspect native attack state");
        boolean invalid=false;
        try{success.attackConflictsWithBlockHit(7,-1);}
        catch(IllegalArgumentException expected){invalid=true;}
        ok(invalid,"negative native client tick is rejected");
    }

    private static void randomized() {
        Random rng=new Random(3832026L);
        for(int i=0;i<90000;i++) {
            long epoch=i+10L;
            Object world=new Object(),player=new Object();
            NativePulseSettlement nativePulse=new NativePulseSettlement();
            BlockHitPolicy block=new BlockHitPolicy();
            ok(nativePulse.reconcileNative(epoch,world,player),
                "90k native identity admission "+i);
            ok(!stage(block,0,epoch),"90k first hold remains vanilla "+i);
            int type=rng.nextInt(8);
            // 0: no attack, 1: completed attack at 4,
            // 2: completed at 3, 3: unknown before native return at 4,
            // 4: unknown after native return at 4,
            // 5: unknown after native return at 3,
            // 6: active IN_FLIGHT at 4, 7: RETURNED at 4.
            int attackTick=(type==2||type==5)?3:4;
            if(type!=0) {
                nativePulse.begin(Lane.ATTACK,epoch,attackTick);
                if(type!=3 && type!=6)nativePulse.nativeReturned(
                    Lane.ATTACK,epoch,attackTick);
                if(type==1||type==2)
                    nativePulse.committed(Lane.ATTACK,epoch,attackTick);
                if(type==1||type==2||type==3||type==4||type==5)
                    nativePulse.abandon(Lane.ATTACK,epoch,attackTick);
            }
            boolean expectedAtFour=type!=0&&type!=2;
            boolean blockedAtFour=nativePulse.attackConflictsWithBlockHit(epoch,4);
            ok(blockedAtFour==expectedAtFour,
                "90k independent same-frame ATTACK/USE ownership oracle "+i);
            ok(stage(block,4,epoch),"90k native Block Hit due independent of attack uncertainty "+i);
            if(blockedAtFour)block.cancelPrepared();
            else block.committed(4,epoch);
            // An uncertain or in-flight ATTACK blocks later dependent USE.
            boolean expectedAtFive=type==3||type==4||type==5||
                type==6||type==7;
            boolean blockedAtFive=nativePulse.attackConflictsWithBlockHit(epoch,5);
            ok(blockedAtFive==expectedAtFive,
                "90k unresolved native ATTACK persists next frame "+i);
            boolean stagedAtFive=stage(block,5,epoch);
            // If tick4 successfully committed Block Hit, interval=2 still
            // forbids a repeat; otherwise its previous clock is restored.
            ok(stagedAtFive==blockedAtFour,
                "90k independent Block Hit pending/committed interval oracle "+i);
            if(stagedAtFive) {
                if(blockedAtFive)block.cancelPrepared();
                else block.committed(5,epoch);
            }
            ok((nativePulse.uncertainMask()&1)!=0 ==
                (type==3||type==4||type==5),
                "90k partial native ATTACK quarantine does not fabricate success "+i);
        }
        ok(checks>540000,"90k seeded attack/use settlement and native cadence traces");
    }
    public static void main(String[] args) throws Exception {
        exact();
        randomized();
        System.out.println("BLOCKHIT_ATTACK_UNCERTAINTY_PASS assertions="+checks);
    }
}
