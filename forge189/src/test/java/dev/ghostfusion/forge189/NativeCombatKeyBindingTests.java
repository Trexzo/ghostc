package dev.ghostfusion.forge189;

import dev.ghostfusion.features.BlockHitPolicy;
import dev.ghostfusion.features.HitSelectPolicy;
import dev.ghostfusion.features.TriggerBotPolicy;
import dev.ghostfusion.features.WTapPolicy;
import dev.ghostfusion.services.ActionCoordinator;
import dev.ghostfusion.services.ActionCoordinator.Decision;
import dev.ghostfusion.services.ActionCoordinator.Type;
import dev.ghostfusion.services.ActionCoordinator.Phase;
import net.minecraft.client.settings.KeyBinding;
import java.util.UUID;
import java.util.Random;

/**
 * Java8 Forge test against ACTUAL Minecraft 1.8.9 KeyBinding input machinery.
 *
 * This is a native CLASS boundary probe, not a launched client, physical
 * LWJGL mouse, genuine AttackEntityEvent, network hit, or server acceptance.
 * Its headless keys have unique temporary codes and are never used in game.
 */
public final class NativeCombatKeyBindingTests {
    private static int checks;
    private static void ok(boolean yes,String description) {
        checks++;if(!yes)throw new AssertionError(description);
    }
    private static void nativeKeys() {
        KeyBinding attack=new KeyBinding("key.ghostc.nativeprobe.attack",205,
            "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.nativeprobe.use",206,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        ok(!attack.isKeyDown()&&!attack.isPressed()&&!use.isPressed(),
            "native key registry starts without a phantom press");
        KeyBinding.onTick(205);
        ok(attack.isPressed(),"vanilla onTick publishes one real native key event");
        ok(!attack.isPressed(),"vanilla consumer consumes exactly one key event");
        ok(!use.isPressed(),"attack does not enqueue a use event");
        KeyBinding.setKeyBindState(205,true);
        ok(attack.isKeyDown(),"native physical held state remains separate");
        ok(!attack.isPressed(),
            "physical pressed state alone is not another queued synthetic pulse");
        KeyBinding.setKeyBindState(205,false);
        ok(!attack.isKeyDown(),"native physical release is preserved");
        KeyBinding.onTick(206);
        ok(use.isPressed()&&!use.isPressed(),"native sword-use is independent");
        KeyBinding.unPressAllKeys();
    }

    private static void sharedNativeReplay() {
        final int attackCode=205,useCode=206;
        KeyBinding attack=new KeyBinding("key.ghostc.nativeprobe.attack2",attackCode,
            "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.nativeprobe.use2",useCode,
            "key.categories.misc");
        // A new JVM test process contains no real GameSettings, but
        // Minecraft's IntHashMap maps a key code to one KeyBinding. Register
        // these exact bindings and verify that the native map recognizes them.
        KeyBinding.unPressAllKeys();
        ActionCoordinator actions=new ActionCoordinator();
        HitSelectPolicy hit=new HitSelectPolicy();
        TriggerBotPolicy trigger=new TriggerBotPolicy();
        BlockHitPolicy block=new BlockHitPolicy();
        WTapPolicy wtap=new WTapPolicy();
        Random r=new Random(353189L);
        UUID a=UUID.fromString("123e4567-e89b-12d3-a456-426614174005");
        UUID b=UUID.fromString("123e4567-e89b-12d3-a456-426614174006");
        int actualClicker=0,actualTrigger=0,rejected=0,blockUse=0;
        int lastActualAttack=-1;
        final int frames=24000;
        long epoch=1;
        for(int t=0;t<frames;t++) {
            if(t>0 && t%4001==0) {
                epoch++;
                actions.reset();
                trigger.reset();
                hit.reset();
                block.reset();
                wtap.reset();
                KeyBinding.unPressAllKeys();
                lastActualAttack=-1;
            }
            boolean physicalHeld=r.nextInt(5)==0;
            boolean useHeld=r.nextInt(13)==0;
            boolean validTarget=r.nextInt(11)!=0;
            UUID target=(t/317)%2==0?a:b;
            KeyBinding.setKeyBindState(attackCode,physicalHeld);
            KeyBinding.setKeyBindState(useCode,useHeld);
            // No direct packets, player attacks, or foreign mouse hooks:
            // the source requests one owner-tick native key event.
            actions.cancelOwner("combat.clicker");
            actions.cancelOwner("combat.triggerbot");
            boolean clickDue=physicalHeld && !useHeld && r.nextInt(4)==0;
            if(clickDue)
                actions.request("combat.clicker",Type.ATTACK,Phase.OUTPUT,
                    0,20,t,1,epoch);
            boolean triggerDue=trigger.due(epoch,t,
                !physicalHeld && !useHeld && validTarget,target,2,4);
            if(triggerDue)
                actions.request("combat.triggerbot",Type.ATTACK,Phase.OUTPUT,
                    0,10,t,1,epoch);
            Decision winner=actions.consume(Type.ATTACK,Phase.OUTPUT,t,epoch);
            ok(actions.consume(Type.ATTACK,Phase.OUTPUT,t,epoch)==null,
                "one exclusive native attack owner per game tick "+t);
            boolean clickerDispatched=false,attackDispatched=false;
            if(winner!=null) {
                boolean approved=hit.preview(true,validTarget,0,2,3,
                    0,"normal",1,t,epoch);
                boolean nativeGuard=r.nextInt(7)!=0;
                boolean ownerAllowed="combat.clicker".equals(winner.owner)
                    ? physicalHeld && !useHeld
                    : !physicalHeld && !useHeld && validTarget;
                if(approved && nativeGuard && ownerAllowed) {
                    // This is the precise native KeyBinding API the real
                    // ForgeCombatController calls for an accepted attack.
                    KeyBinding.onTick(attackCode);
                    hit.committed(t,epoch);
                    attackDispatched=true;
                    clickerDispatched="combat.clicker".equals(winner.owner);
                    if(clickerDispatched)actualClicker++;
                    else {
                        trigger.dispatched(epoch,t,target);
                        actualTrigger++;
                    }
                    ok(lastActualAttack<0 || t-lastActualAttack>=3,
                        "shared accepted-native gap protects both sources "+t);
                    lastActualAttack=t;
                }else rejected++;
                hit.cancelPrepared();
            }
            ok(attack.isPressed()==attackDispatched,
                "native pressed queue matches only successful dispatch "+t);
            ok(!attack.isPressed(),"no duplicate attack press from losing owner "+t);
            // Forge WTap is intentionally driven by native AttackEntityEvent,
            // NOT by the KeyBinding call. This test never invents that event.
            ok(!wtap.pending(),"synthetic key alone never arms native WTap "+t);

            // BlockHit in post_attack mode accepts actual clicker pulses only;
            // an approved ATTACK intent without a native pulse never triggers.
            boolean blockCandidate=block.due(true,physicalHeld,useHeld,true,
                validTarget,true,clickerDispatched,4,0,"post_attack",t,epoch);
            if(blockCandidate) {
                ok(!attackDispatched && physicalHeld && !useHeld,
                    "native block pulse cannot share native attack tick "+t);
                KeyBinding.onTick(useCode);
                blockUse++;
            }
            ok(use.isPressed()==blockCandidate,
                "native use queue matches true post-attack output "+t);
            ok(!use.isPressed(),"no duplicate post-attack use "+t);
        }
        KeyBinding.setKeyBindState(attackCode,false);
        KeyBinding.setKeyBindState(useCode,false);
        KeyBinding.unPressAllKeys();
        ok(actualClicker>100 && actualTrigger>100 && rejected>100,
            "both native attack owners and denied output exercised");
        ok(blockUse>0,"real Minecraft use-key path exercised after valid clicker");
        ok(checks>frames*4,"substantial real native KeyBinding input boundary replay");
        System.out.println("NATIVE_COMBAT_KEYBINDING_PASS assertions="+checks+
            " clicker="+actualClicker+" trigger="+actualTrigger+
            " rejected="+rejected+" use="+blockUse);
    }
    public static void main(String[] args) {
        nativeKeys();
        sharedNativeReplay();
    }
}
