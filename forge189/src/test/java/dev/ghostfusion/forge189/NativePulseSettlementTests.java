package dev.ghostfusion.forge189;

import dev.ghostfusion.features.NativePulseSettlement;
import dev.ghostfusion.features.NativePulseSettlement.Lane;
import dev.ghostfusion.features.NativePulseSettlement.Phase;
import net.minecraft.client.settings.KeyBinding;

/**
 * Actual Minecraft 1.8.9 KeyBinding API + session settlement state.
 * Headless Java8; does not launch Minecraft or claim an actual server ACK.
 */
public final class NativePulseSettlementTests {
    private static int checks;
    private static void ok(boolean v,String why) {
        checks++;if(!v)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        final int attackCode=232,useCode=233;
        KeyBinding attack=new KeyBinding("key.ghostc.r372.attack",attackCode,
            "key.categories.misc");
        KeyBinding use=new KeyBinding("key.ghostc.r372.use",useCode,
            "key.categories.misc");
        KeyBinding.unPressAllKeys();
        NativePulseSettlement p=new NativePulseSettlement();
        p.begin(Lane.ATTACK,8,0);
        KeyBinding.onTick(attackCode);
        p.nativeReturned(Lane.ATTACK,8,0);
        // Simulate an exception after the native API but before the second
        // TriggerBot policy commit: the press exists; never replay it.
        p.abandon(Lane.ATTACK,8,0);
        ok(attack.isPressed()&&!attack.isPressed(),
            "native attack key has exactly one queued press");
        ok(p.phase(Lane.ATTACK,8)==Phase.UNCERTAIN,
            "incomplete native attack cannot be retried");
        ok(!p.admit(Lane.ATTACK,8),
            "same generation keeps uncertain attack isolated");
        p.begin(Lane.USE,8,1);
        KeyBinding.onTick(useCode);
        p.nativeReturned(Lane.USE,8,1);
        p.committed(Lane.USE,8,1);
        p.abandon(Lane.USE,8,1);
        ok(use.isPressed()&&!use.isPressed(),
            "independent settled native use key has one pulse");
        ok(p.admit(Lane.USE,8),"settled use can admit a later tick");
        ok(p.admit(Lane.ATTACK,9),"fresh generation clears unknown attack");
        p.begin(Lane.ATTACK,9,0);
        KeyBinding.onTick(attackCode);
        p.nativeReturned(Lane.ATTACK,9,0);
        p.committed(Lane.ATTACK,9,0);
        p.abandon(Lane.ATTACK,9,0);
        ok(attack.isPressed()&&!attack.isPressed(),
            "successful next-generation attack remains unique");
        ok(checks>=7,"original 1.8.9 API incomplete/settled native scenarios");
        System.out.println("NATIVE_PULSE_SETTLEMENT_PROBE_PASS assertions="+checks);
    }
}
