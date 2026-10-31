package dev.ghostfusion.tests;

import dev.ghostfusion.core.ModuleRegistry;
import dev.ghostfusion.host.HostRuntime;
import dev.ghostfusion.host.PanicController;
import java.nio.file.Files;
import java.nio.file.Path;

/** R3.35: all integrated gameplay and visual module registrations, settings
 * and reversible F6 Panic run in one genuine HostRuntime/profile lifecycle.
 * Native Forge gameplay still requires a real Minecraft 1.8.9 acceptance run. */
public final class IntegratedModuleCatalogTests {
    private static int count;
    private static void ok(boolean good,String message) {
        count++;if(!good)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("ghostc-r335-integrated-");
        HostRuntime host=new HostRuntime(root);
        String[] modules={
            "combat.aimassist","combat.clicker","combat.triggerbot","combat.autorod","combat.autosnowball","combat.blockhit","combat.hitselect",
            "combat.velocity","combat.wtap","combat.jumpreset",
            "movement.bridgeassist","movement.sprint",
            "inventory.cheststealer","inventory.manager","inventory.autoarmor",
            "hud.modulelist","hud.target","hud.keystrokes","hud.cps","render.esp"
        };
        for(String id:modules) {
            ok(host.modules().get(id)!=null,"integrated concrete module "+id);
            ok(host.modules().get(id).state()==ModuleRegistry.State.DISABLED,
                "new profile opts out of "+id);
        }
        ok(host.modules().get("combat.aimassist").settings().containsKey("respect_mouse") &&
           host.modules().get("combat.aimassist").settings().containsKey("mouse_pause_ticks"),
            "Aim Assist real manual-mouse override is configurable in F7");
        ok(host.modules().get("combat.aimassist").settings().containsKey("switch_delay_ticks"),
            "Aim Assist opt-in target switching delay available through real F7");
        ok(host.modules().get("combat.aimassist").settings().containsKey("lock_target"),
            "opt-in Aim Assist live UUID target lock registered");
        ok(host.modules().get("combat.aimassist").settings().containsKey("target_priority"),
            "selectable Aim Assist angular/distance priority registered");
        ok(host.modules().get("combat.clicker").settings().containsKey("pattern"),
            "actual AutoClicker pattern mode registered");
        ok(host.modules().get("combat.triggerbot").settings().containsKey("acquire_ticks") &&
           host.modules().get("combat.triggerbot").settings().containsKey("cooldown_ticks") &&
           host.modules().get("combat.triggerbot").settings().containsKey("range"),
           "TriggerBot has native target acquisition, cooldown and range settings");
        ok(host.modules().get("combat.autorod").settings().containsKey("cooldown_ticks") &&
           host.modules().get("combat.autorod").settings().containsKey("range") &&
           host.modules().get("combat.autorod").settings().containsKey("require_sword"),
            "real native AutoRod hotbar transaction exposed through F7");
        ok(host.modules().get("combat.autosnowball").settings().containsKey("cooldown_ticks") &&
           host.modules().get("combat.autosnowball").settings().containsKey("range") &&
           host.modules().get("combat.autosnowball").settings().containsKey("require_sword"),
           "genuine AutoSnowball hotbar transaction exposed in F7");
        ok(host.modules().get("combat.blockhit").settings().containsKey("mode"),
            "event-mode Block Hit registered");
        ok(host.modules().get("combat.hitselect").settings().containsKey("trade_min_self_hurt"),
            "trading Hit Select registered");
        ok(host.modules().get("combat.wtap").settings().containsKey("reset_ticks"),
           "actual WTap sprint reset module and delay setting registered");
        ok(host.modules().get("combat.jumpreset").settings().containsKey("min_hurt_time") &&
           host.modules().get("combat.jumpreset").settings().containsKey("cooldown_ticks") &&
           host.modules().get("combat.jumpreset").settings().containsKey("require_attack_held"),
           "native Jump Reset is a real independently controllable combat module");
        ok(host.modules().get("combat.velocity").settings().containsKey("mode"),
           "packet vs hurt-mode is real registered Velocity control");
        ok(host.modules().get("combat.velocity").settings().containsKey("horizontal_percent") &&
           host.modules().get("combat.velocity").settings().containsKey("vertical_percent"),
           "native Velocity horizontal and vertical percentages are true module settings");
        ok(host.modules().get("movement.bridgeassist").settings().containsKey("lookahead"),
            "native Bridge Assist lookahead registered");
        ok(host.modules().get("inventory.autoarmor").settings().containsKey("upgrade_existing") &&
           host.modules().get("inventory.autoarmor").settings().containsKey("open_delay_ticks"),
           "actual AutoArmor staging and profile controls registered");
        ok(host.modules().get("inventory.manager").settings().containsKey("upgrade_existing") &&
           host.modules().get("inventory.manager").settings().containsKey("click_delay_ticks"),
           "Inventory Manager is a registered real hotbar module, not visual-only");
        ok(host.modules().get("inventory.cheststealer").settings().containsKey("open_delay_ticks") &&
           host.modules().get("inventory.cheststealer").settings().containsKey("allow_custom_names"),
           "actual Chest Stealer GUI module has safe open/transfer controls");
        ok(host.modules().get("hud.modulelist").settings().containsKey("appearance"),
            "in-game Module List skin registered");
        ok(host.modules().get("hud.target").settings().containsKey("motion"),
            "Target HUD opt-in motion registered");
        ok(host.modules().get("hud.keystrokes").settings().containsKey("appearance"),
            "Keystrokes theme registered");
        ok(host.modules().get("hud.cps").settings().containsKey("appearance"),
            "CPS card theme registered");
        host.worldPresence(true);
        host.uiActions().cycle("combat.aimassist","target_priority");
        host.uiActions().cycle("combat.clicker","pattern");
        host.uiActions().fraction("combat.triggerbot","acquire_ticks",.4);
        host.uiActions().fraction("combat.triggerbot","cooldown_ticks",.5);
        host.uiActions().bool("combat.triggerbot","require_sword");
        host.uiActions().cycle("combat.blockhit","mode");
        host.uiActions().cycle("combat.hitselect","mode");
        host.uiActions().cycle("hud.target","skin");
        host.uiActions().cycle("hud.target","skin");
        host.uiActions().cycle("hud.modulelist","appearance");
        host.uiActions().cycle("hud.keystrokes","appearance");
        host.uiActions().fraction("movement.bridgeassist","lookahead",0.7);
        host.uiActions().fraction("inventory.cheststealer","click_delay_ticks",0.5);
        host.uiActions().fraction("inventory.autoarmor","click_delay_ticks",0.75);
        host.uiActions().bool("inventory.autoarmor","upgrade_existing");
        host.uiActions().fraction("inventory.manager","open_delay_ticks",0.75);
        host.uiActions().bool("inventory.manager","upgrade_existing");
        host.uiActions().fraction("combat.wtap","reset_ticks",1.0);
        host.uiActions().cycle("combat.velocity","mode");
        host.uiActions().fraction("combat.velocity","horizontal_percent",0.7);
        host.uiActions().fraction("combat.velocity","vertical_percent",0.9);
        for(String id:modules) {
            host.uiActions().toggle(id);
            ok(host.featureEnabled(id),"integrated module can enable "+id);
        }
        ok("distance".equals(host.aimTargetPriority()),"actual distance ranking setting live");
        ok(host.triggerAcquireTicks()>1 && host.triggerCooldownTicks()>4 &&
           !host.triggerRequireSword(),
           "TriggerBot settings are editable with the shared profile UI");
        ok("balanced".equals(host.clickPattern()),"actual patterned click scheduler setting live");
        ok("post_attack".equals(host.blockMode()),"actual post-hit block mode live");
        ok("ready".equals(host.hitMode()),"actual ready hit select live");
        ok("minimal".equals(host.targetSkin()),"real minimal Target HUD skin live");
        ok("minimal".equals(host.moduleListAppearance()),"real Module List appearance live");
        ok("minimal".equals(host.keysAppearance()),"real Keystrokes skin live");
        final int triggerDelay=host.triggerAcquireTicks();
        final int triggerCooldown=host.triggerCooldownTicks();
        final int armorDelay=host.autoArmorClickDelay();
        ok(armorDelay>5 && host.autoArmorUpgrade(),
           "native AutoArmor click delay and upgrade mode active in GUI");
        final int managerDelay=host.inventoryManagerOpenDelay();
        ok(managerDelay>6 && host.inventoryManagerUpgrade(),
           "actual hotbar sorting policy responds to typed GUI settings");
        final int chestDelay=host.chestStealerClickDelay();
        ok(chestDelay>=2 && chestDelay<=20,
            "real chest click delay selectable through typed GUI");
        final double lookahead=host.bridgeLookahead();
        final double velocityHorizontal=host.velocityHorizontalPercent();
        final double velocityVertical=host.velocityVerticalPercent();
        ok(host.wtapDelayTicks()==3,"actual native attack WTap reset timing configured");
        ok("packet".equals(host.velocityMode()),
           "Velocity switches to clientbound S12/S27 packet mode");
        ok(velocityHorizontal==70 && velocityVertical==90,
           "true native Velocity axes are configurable with module enabled");
        ok(lookahead>0.75,"native edge projection setting accepts UI edits");
        int disabled=PanicController.disableAll(host);
        ok(disabled>=modules.length,"Panic disables every integrated module");
        for(String id:modules)
            ok(!host.featureEnabled(id),"Panic leaves module disabled "+id);
        host.close();
        HostRuntime loaded=new HostRuntime(root);
        loaded.worldPresence(true);
        for(String id:modules)
            ok(!loaded.featureEnabled(id),"Panic persisted disabled state "+id);
        ok("distance".equals(loaded.aimTargetPriority()),"Aim ranking survived Panic");
        ok(loaded.triggerAcquireTicks()==triggerDelay &&
           loaded.triggerCooldownTicks()==triggerCooldown &&
           !loaded.triggerRequireSword(),
           "Panic saves TriggerBot settings without re-enabling it");
        ok("balanced".equals(loaded.clickPattern()),"Click timing survived Panic");
        ok("post_attack".equals(loaded.blockMode()),"Block mode survived Panic");
        ok("ready".equals(loaded.hitMode()),"HitSelect mode survived Panic");
        ok("minimal".equals(loaded.targetSkin()),"Target HUD style survived Panic");
        ok("minimal".equals(loaded.moduleListAppearance()),"Module List style survived Panic");
        ok(loaded.bridgeLookahead()==lookahead,"Bridge Assist lookahead survived Panic");
        ok(loaded.autoArmorClickDelay()==armorDelay && loaded.autoArmorUpgrade(),
           "F6 Panic disables AutoArmor while preserving its saved configuration");
        ok(loaded.inventoryManagerOpenDelay()==managerDelay &&
           loaded.inventoryManagerUpgrade(),
           "Inventory Manager mode persists through Panic without activation");
        ok(loaded.chestStealerClickDelay()==chestDelay,
           "Chest Stealer timing persists through F6 Panic without rearming");
        ok(loaded.wtapDelayTicks()==3,
           "Panic preserves WTap module timing but not enabled state");
        ok("packet".equals(loaded.velocityMode()),
           "Velocity packet mode survives Panic without enabling motion");
        ok(loaded.velocityHorizontalPercent()==velocityHorizontal &&
           loaded.velocityVerticalPercent()==velocityVertical,
           "Velocity axis settings survive Panic without re-enabling motion writes");
        loaded.uiActions().toggle("combat.aimassist");
        ok(loaded.featureEnabled("combat.aimassist"),"explicit reenable after Panic works");
        loaded.close();
        VelocityTests.main(args);
        WTapTests.main(args);
        JumpResetTests.main(args);
        AutoRodTests.main(args);
        AutoSnowballTests.main(args);
        ProjectileManualInputTests.main(args);
        ProjectileTargetFenceTests.main(args);
        ProjectileInventoryFenceTests.main(args);
        ProjectileNativeUseResultTests.main(args);
        ProjectileObservationTests.main(args);
        ProjectileReconciliationTests.main(args);
        ProjectileHookProvenanceTests.main(args);
        CombatFrameAcceptanceTests.main(args);
        TriggerNativeIdentityTests.main(args);
        ChestStealerTests.main(args);
        InventoryManagerTests.main(args);
        AutoArmorTests.main(args);
        ModuleInteractionTests.main(args);
        ModuleInteractionDiagnosticsTests.main(args);
        ModuleActionTelemetryTests.main(args);
        CombatNativeEvidenceTests.main(args);
        LiveAcceptanceCoverageTests.main(args);
        TriggerBotTests.main(args);
        AttackCommitTests.main(args);
        AimTriggerHandoffTests.main(args);
        AimTargetSwitchTests.main(args);
        AimNativeIdentityTests.main(args);
        AimManualInputTests.main(args);
        AimHandoffContinuityTests.main(args);
        ok(count>65,"real integrated module and persisted profile coverage");
        System.out.println("INTEGRATED_MODULE_CATALOG_PASS assertions="+count);
    }
}
