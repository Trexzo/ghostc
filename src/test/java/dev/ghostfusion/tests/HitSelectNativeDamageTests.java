package dev.ghostfusion.tests;

import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.NativeAttackFrameGate;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R3.82: original Hit Select preview must still pass live target/self damage,
 * unchanged settings, correct epoch and tick at native ATTACK output.
 * Headless owner-thread policy only; no Minecraft key pulse or server hit.
 */
public final class HitSelectNativeDamageTests {
    private static long checks;
    private static void ok(boolean value,String why) {
        checks++;
        if(!value)throw new AssertionError(why);
    }
    private static boolean stage(HitSelectPolicy gate,boolean present,int hurt,
                                 int max,int gap,int self,String mode,
                                 int trade,long tick,long epoch) {
        return gate.preview(true,present,hurt,max,gap,self,mode,trade,tick,epoch);
    }
    private static boolean finalGate(HitSelectPolicy gate,boolean present,int hurt,
                                     int max,int gap,int self,String mode,
                                     int trade,long tick,long epoch) {
        return gate.confirmPrepared(true,present,hurt,max,gap,self,mode,trade,
            tick,epoch);
    }
    private static boolean rejects(Runnable work) {
        try{work.run();return false;}
        catch(IllegalStateException expected){return true;}
    }
    private static void exact() throws Exception {
        HitSelectPolicy gate=new HitSelectPolicy();
        ok(stage(gate,true,2,3,7,0,"normal",1,0,1),
            "normal mode stages valid target at initial hurt 2");
        ok(!finalGate(gate,true,5,3,7,0,"normal",1,0,1),
            "target newly hurt above maximum rejects final native action");
        ok(rejects(()->gate.committed(0,1)),
            "rejected native action cannot commit Hit Select cooldown");
        ok(stage(gate,true,1,3,7,0,"normal",1,1,1),
            "rejected target damage has not consumed a shared cooldown");
        ok(finalGate(gate,true,1,3,7,0,"normal",1,1,1),
            "current damage valid at both boundaries admits real pulse");
        gate.committed(1,1);
        ok(!stage(gate,true,0,3,7,0,"normal",1,2,1),
            "genuine native dispatch enforces original minimum interval");
        ok(!stage(gate,true,0,3,7,0,"normal",1,7,1),
            "one tick before shared gap still denied");
        ok(stage(gate,true,0,3,7,0,"normal",1,8,1),
            "shared cooldown boundary permits new preview");
        ok(!finalGate(gate,true,0,3,6,0,"normal",1,8,1),
            "changing configured shared gap after preview is not authorization");
        ok(rejects(()->gate.committed(8,1)),
            "edited settings cannot commit original staged token");

        gate.reset();
        ok(stage(gate,true,0,2,4,0,"ready",1,1,2),
            "ready mode stages target initially fully recovered");
        ok(!finalGate(gate,true,1,2,4,0,"ready",1,1,2),
            "ready mode rejects newly damaged target before native output");
        ok(stage(gate,true,0,2,4,0,"ready",1,2,2),
            "ready cooldown still free after veto");
        ok(finalGate(gate,true,0,2,4,0,"ready",1,2,2),
            "ready mode permits unchanged zero-hurt target");
        gate.committed(2,2);

        gate.reset();
        ok(stage(gate,true,1,2,5,5,"trade",4,1,3),
            "trade mode stages only valid self damage");
        ok(!finalGate(gate,true,1,2,5,2,"trade",4,1,3),
            "trade mode rejects self hurt drop beneath minimum");
        ok(stage(gate,true,1,2,5,5,"trade",4,2,3),
            "trade denied final output did not start a cooldown");
        ok(finalGate(gate,true,2,2,5,5,"trade",4,2,3),
            "trade valid current damage and threshold accepted");
        gate.committed(2,3);
        ok(!stage(gate,true,1,2,5,5,"trade",4,3,3),
            "trade native commit owns shared cooldown");

        gate.reset();
        ok(stage(gate,true,1,3,4,0,"normal",1,0,4),
            "staged target for native registry-lifetime check");
        ok(!finalGate(gate,false,1,3,4,0,"normal",1,0,4),
            "despawned/replaced live target revokes staged token");
        ok(rejects(()->gate.committed(0,4)),
            "replaced target cannot claim a native action");

        gate.reset();
        ok(stage(gate,true,0,2,4,0,"normal",1,0,5),
            "mode edit test initially stages approved frame");
        ok(!finalGate(gate,true,0,2,4,0,"ready",1,0,5),
            "switch to ready in same tick cannot reinterpret a preview");
        ok(!finalGate(gate,true,0,2,4,0,"normal",1,0,5),
            "reverting mode same tick cannot resurrect rejected token");
        ok(stage(gate,true,0,2,4,0,"ready",1,1,5),
            "next tick can acquire new changed-mode preview");
        ok(!finalGate(gate,true,0,2,4,0,"ready",1,2,5),
            "wrong tick cancels the earlier prepared action");
        ok(rejects(()->gate.committed(1,5)),
            "wrong-tick confirmation never accepts delayed native output");

        gate.reset();
        ok(stage(gate,true,0,2,4,0,"normal",1,1,6),
            "fresh session stage");
        ok(!finalGate(gate,true,0,2,4,0,"normal",1,1,7),
            "different session generation denies old staged attack");
        ok(rejects(()->gate.committed(1,6)),
            "old generation cannot commit after final veto");
        ok(stage(gate,true,0,2,4,0,"normal",1,2,7),
            "new session may begin independent native preview");
        ok(finalGate(gate,true,0,2,4,0,"normal",1,2,7),
            "fresh session and exact tick can confirm");
        gate.committed(2,7);
        ok(!finalGate(gate,true,0,2,4,0,"normal",1,2,7),
            "committed preview cannot be confirmed or reused again");
        ok(!stage(gate,true,0,2,4,0,"normal",1,3,7),
            "completed native cooldown survives stale confirm attempt");

        final Object player=new Object(),target=new Object(),replacement=new Object();
        ok(!NativeAttackFrameGate.target(target,replacement,target,player,false,20),
            "changed native crosshair cannot provide current hurt evidence");
        ok(!NativeAttackFrameGate.target(target,target,replacement,player,false,20),
            "registry object replacement cannot provide current hurt evidence");
        ok(NativeAttackFrameGate.target(target,target,target,player,false,20),
            "genuine same native target can be read immediately before output");

        AtomicBoolean foreignRejected=new AtomicBoolean();
        Thread foreign=new Thread(()->{
            try{gate.confirmPrepared(true,true,0,2,4,0,"normal",1,3,7);}
            catch(IllegalStateException expected){foreignRejected.set(true);}
        });
        foreign.start();foreign.join();
        ok(foreignRejected.get(),"foreign Netty thread cannot confirm pending native hit");
    }

    private static void randomized() {
        Random rng=new Random(3822026L);
        String[] modes={"normal","ready","trade"};
        for(int i=0;i<90000;i++) {
            HitSelectPolicy gate=new HitSelectPolicy();
            String mode=modes[rng.nextInt(modes.length)];
            int limit=rng.nextInt(11);
            int gap=1+rng.nextInt(20);
            int trade=1+rng.nextInt(10);
            int firstHurt="ready".equals(mode)?0:rng.nextInt(limit+1);
            int firstSelf="trade".equals(mode)
                ? trade+rng.nextInt(21-trade):rng.nextInt(21);
            long epoch=i+1L,tick=2;
            ok(stage(gate,true,firstHurt,limit,gap,firstSelf,mode,trade,tick,epoch),
                "90k initial valid native damage preview "+i);
            int nowHurt=rng.nextInt(14);
            int nowSelf=rng.nextInt(21);
            boolean active=rng.nextInt(31)!=0;
            boolean targetPresent=rng.nextInt(9)!=0;
            boolean editMode=rng.nextInt(23)==0;
            boolean editGap=rng.nextInt(29)==0;
            boolean editLimit=rng.nextInt(29)==0;
            boolean editTrade=rng.nextInt(29)==0;
            String finalMode=editMode?modes[(java.util.Arrays.asList(modes).indexOf(mode)+1)%3]:mode;
            int finalGap=editGap?(gap%20)+1:gap;
            int finalLimit=editLimit?(limit+1)%11:limit;
            int finalTrade=editTrade?(trade%10)+1:trade;
            long finalTick=rng.nextInt(59)==0?tick+1:tick;
            long finalEpoch=rng.nextInt(67)==0?epoch+1:epoch;
            boolean expected=active&&targetPresent&&nowHurt<=limit &&
                (!"ready".equals(mode)||nowHurt==0) &&
                (!"trade".equals(mode)||nowSelf>=trade) &&
                !editMode&&!editGap&&!editLimit&&!editTrade &&
                finalTick==tick&&finalEpoch==epoch;
            boolean got=gate.confirmPrepared(active,targetPresent,nowHurt,
                finalLimit,finalGap,nowSelf,finalMode,finalTrade,finalTick,finalEpoch);
            ok(got==expected,
                "90k independent final damage/setting/epoch oracle "+i);
            if(got) {
                gate.committed(tick,epoch);
                boolean nextAllowed=stage(gate,true,0,limit,gap,20,
                    mode,trade,tick+1,epoch);
                ok(nextAllowed==(gap==1),
                    "90k committed cooldown honors exact configured gap "+i);
                gate.cancelPrepared();
            } else {
                ok(rejects(()->gate.committed(tick,epoch)),
                    "90k vetoed native output cannot claim cooldown "+i);
                ok(stage(gate,true,firstHurt,limit,gap,firstSelf,mode,trade,tick+1,epoch),
                    "90k canceled preview has no synthetic cooldown "+i);
                gate.cancelPrepared();
            }
        }
        ok(checks>270000,"90k seeded same-frame native Hit Select validations executed");
    }

    public static void main(String[] args) throws Exception {
        exact();randomized();
        System.out.println("HITSELECT_NATIVE_DAMAGE_PASS assertions="+checks);
    }
}
