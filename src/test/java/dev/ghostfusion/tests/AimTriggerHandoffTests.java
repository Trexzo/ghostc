package dev.ghostfusion.tests;

import dev.ghostfusion.features.AimTriggerHandoff;
import dev.ghostfusion.features.TriggerBotPolicy;
import java.util.Random;
import java.util.UUID;

/** R3.50 camera ownership handoff and TriggerBot dwell continuity. */
public final class AimTriggerHandoffTests {
    private static int checks;
    private static void ok(boolean yes,String description) {
        checks++; if(!yes)throw new AssertionError(description);
    }
    private static boolean yield(boolean enabled,boolean attack,boolean use,
                                 boolean using,boolean binding,boolean crosshair) {
        return AimTriggerHandoff.yieldCamera(
            enabled,attack,use,using,binding,crosshair);
    }
    public static void main(String[] args) {
        for(int bits=0;bits<64;bits++) {
            boolean enabled=(bits&1)!=0,attack=(bits&2)!=0,use=(bits&4)!=0;
            boolean using=(bits&8)!=0,binding=(bits&16)!=0,crosshair=(bits&32)!=0;
            boolean expected=enabled&&!attack&&!use&&!using&&binding&&crosshair;
            ok(yield(enabled,attack,use,using,binding,crosshair)==expected,
                "all six handoff guards are enforced "+bits);
        }
        ok(yield(true,false,false,false,true,true),
            "physical player already under crosshair allows trigger ownership");
        ok(!yield(true,true,false,false,true,true),
            "held native attack retains Aim Assist camera ownership");
        ok(!yield(true,false,true,false,true,true),
            "held native use never yields camera to TriggerBot");
        ok(!yield(false,false,false,false,true,true),
            "module disabled has no effect on Aim Assist");
        ok(!yield(true,false,false,false,true,false),
            "stale target or failed native ray does not yield camera");
        ok(!yield(true,false,false,false,false,true),
            "unbound or conflicting attack key blocks handoff");

        TriggerBotPolicy trigger=new TriggerBotPolicy();
        UUID player=UUID.fromString("123e4567-e89b-12d3-a456-426614174004");
        ok(!trigger.due(11,0,yield(true,false,false,false,true,true),
            player,2,4),"first native crosshair acquisition arms");
        ok(!trigger.due(11,1,yield(true,false,false,false,true,true),
            player,2,4),"first held camera handoff cannot shortcut dwell");
        ok(trigger.due(11,2,yield(true,false,false,false,true,true),
            player,2,4),"steady native camera handoff reaches TriggerBot");
        trigger.dispatched(11,2,player);
        ok(!trigger.due(11,3,false,null,2,4),
            "manual physical attack revokes idle TriggerBot acquisition");
        ok(!trigger.due(11,4,true,player,2,4),
            "fresh acquisition required after manual attack");
        Random random=new Random(3502026L);
        for(int i=0;i<50000;i++) {
            boolean enabled=random.nextBoolean();
            boolean attack=random.nextBoolean();
            boolean use=random.nextBoolean();
            boolean using=random.nextBoolean();
            boolean binding=random.nextBoolean();
            boolean target=random.nextBoolean();
            boolean handoff=yield(enabled,attack,use,using,binding,target);
            ok(!handoff || (enabled&&!attack&&!use&&!using&&binding&&target),
                "mixed source input always preserves manual authority "+i);
        }
        ok(checks>50000,"substantial Aim Assist/TriggerBot cooperation trace");
        System.out.println("AIM_TRIGGER_HANDOFF_PASS assertions="+checks);
    }
}
