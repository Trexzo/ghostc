# GhostC R2.42 — live Target HUD health and visibility

## Audited read-only rendering issue

The Target HUD correctly selects a potential target using the current owner's immutable tick snapshot. But the renderer retrieves the Minecraft entity by ID and then uses the **snapshot's cached health** for the numeric value and bar width, while using the live player's maximum health for the denominator. If the player takes damage, dies, or gains invisibility between tick publication and overlay rendering, the UI may display a stale value/card until the next owner tick.

This does not require or justify changing gameplay targeting, attacks, movement, network packets, inventory, saved settings or the combat controller.

## Changes

- New Java 8-compatible, Minecraft-independent `HudLiveTargetGate.eligible` takes the currently resolved player, the local player, the current dead flag, current health, invisibility and existing visible-only preference.
- After selecting a snapshot ID and resolving `EntityPlayer` from the **active** `mc.theWorld`, Target HUD requires a real nonlocal, nondead player with finite, positive *live* health, and excludes now-invisible players when `visible_only` remains enabled.
- The actual `TargetHudTextLayout.layout` invocation uses the **same current live health** for values/percentage and fill fraction; it continues to use the actual player's live maximum health. No change to text-fitting, layout, theme, size, or saved HUD anchors.
- Invisible players remain eligible when the user intentionally disables Target HUD's `visible_only` preference. This is entirely a read-only display gate.

## Verification

- `HudLiveTargetGateTests` runs a fixed suite of missing/local/dead/invisible/nonfinite/zero-health cases, **120,000 seeded randomized permutations** of liveness, visibility and current health, and **18,000 stale-snapshot comparisons**. It checks that snapshot health never overrides a current live state and that unrestricted rendering is a superset of visible-only rendering.
- Forge wiring regression requires the actual active-player health read and admission gate **before** any Target HUD card rendering, and requires the `TargetHudTextLayout` numeric health input to be `liveHealth` rather than `target.health`.
- Java 17/21 CI compiles the host-independent source and tests using `--release 8`; Linux/Windows JDK8 Forge builds compile/reobfuscate the mod, verify that `HudLiveTargetGate.class` is packaged, check JAR provenance, and run Windows fake-profile installation and rollback rehearsal. Merge only when the exact PR head passes all gates, then verify the separate `main` merge SHA.

## Remaining acceptance

A synthetic source/test pass is not proof of real Minecraft 1.8.9 GUI launch, live in-world health refresh, font rendering, measured FPS or a one-hour gameplay soak. Those G1/G2/G4/G5 in-game acceptance tasks remain open. One controlled real in-game session can validate that damage/death/invisibility changes are reflected correctly and other HUD modules still look right.
