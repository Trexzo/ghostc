# GhostC R3.56 — native AutoRod

**Parent:** CI-green R3.55 draft #189, exact head `b2e2b674c03ec3745ea113ec34d8b1baa678cf36`. Branch/PR isolated from other combat work, independent visual PRs and `main`.

## What the new module does

A real, independently toggleable **Combat → AutoRod** module in the existing F7 ClickGUI. Default **OFF**. A *new, physical* attack press while a valid visible player is beneath the current native crosshair, and a fishing rod is present in the hotbar, can start a short native hotbar transaction. It selects the fishing rod, uses the normal Minecraft 1.8.9 `PlayerControllerMP.sendUseItem` method, and restores the previously selected slot on a later client tick.

Configurable bounded settings: `cooldown_ticks` 10–100 (default **30**); `range` 2–4.5 blocks (default **3.25**); `require_sword` (default **true**), and `ignore_teammates` (default **true**). If a cast is already out (`player.fishEntity != null`), it does not start a second cycle. A target must be a live `EntityPlayer` still represented by the identical entity object in the current world, healthy, line-of-sight visible and in range. No target, rod, valid binding or physical attack edge means no action.

## Single-owner behavior and manual priority

`AutoRodPolicy` has one owner thread and generation-fenced `NONE → SELECT → USE → RESTORE` stages; every stage must be committed **after** its native owner output. All requests are bounded to that tick, and `ActionCoordinator.Type.HOTBAR_SELECT` / `USE` enforce exclusive optional action ownership. `ForgeAutoRodController.tick` executes **before** `ForgeCombatController.tick` on the one native END tick; while AutoRod owns selection/use/restoration, existing optional AutoClicker, TriggerBot, Block Hit and Aim Assist camera output are suppressed. This does not block native *human* physical Minecraft attacks or ordinary keyboard input.

Manual hotbar number-key presses and any detected change to `inventory.currentItem` override AutoRod. On a manual slot change, AutoRod cancels its own transaction rather than silently returning the player to the old slot. On GUI/focus, F6 Panic, world/epoch swap, module fault/disable, or WTap reserved sprint period, the module resets; it attempts to restore the previous slot only when the **same player/world** still has the rod slot selected and no manual hotbar key is held. It never restores an old-world item. Settings edits cancel an in-progress use and favor restoration over a stale cast.

The native use method is invoked only while a genuine `ItemFishingRod` remains equipped and the current world/target/input gates hold. No direct C08/C09 packet crafting, Netty hooks, incoming packet mutation, raw motion edits, rotation spoofing, or mouse event synthesis. There is no guarantee that all third-party hotbar writers or rapid wheel movements within a single native tick can be detected; actual original-game acceptance is needed.

`ACTION_AUTOROD_ATTEMPT`, `DISPATCHED`, `LOCAL_COMPLETE` and `CANCELLED` are opt-in diagnostics describing **local** native action progress only; none implies a successful hook hit or server acknowledgement.

## Verification gates

- `AutoRodTests`: true physical press acquisition and no initial held-key trigger; selection/use/restoration sequencing, duplicate tick refusal, exact cooldown boundary, manual override (including during rollback), world-change/GUI/WTap suppression, failed native-use rollback, foreign-thread owner rejection; 50,000 deterministic mixed frames; real F7 settings, profile roundtrip and F6 Panic.
- Mandatory Forge 1.8.9 Java8 `nativeAutoRodProbe`: verified original `PlayerControllerMP.sendUseItem(EntityPlayer,World,ItemStack)`, `updateController`, `ItemFishingRod` and local native player class; in `gradle check` on Linux and Windows.
- Mandatory Core `tools/test_forge_wiring.py` checks the real Forge integration and that new AutoRod never substitutes direct packet writes, synthetic key presses, or an independent attack owner.
- Full exact-head Core Java17/21 and Forge Linux/Windows Java8 build/reobfuscation, downloaded artifact acceptance rehearsal, plus later *launched genuine Minecraft 1.8.9* manual hotbar/use/GUI/WTap/AutoClicker acceptance.
- **No original game session has been launched. CI is not proof of Minecraft gameplay timing, server cast success, damage, or compatibility with server-specific mechanics.**

Draft-only GitHub workflow. No auto-merge, no force push, no installation/ZIP, no visual branch edits.
