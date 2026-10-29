# GhostC R3.58 — Projectile/manual-input ownership recovery

**Parent:** R3.57 AutoSnowball draft PR #191, CI-green exact commit `6c3c5c8334d17f4cb1c19bf23124b5b691a816f9`. This is an isolated GitHub draft and changes no visual branches or `main`.

## Fault reproduced in source review

R3.56/R3.57 checked `inventory.currentItem` and currently held hotbar number keys at client END tick. A player can change slots **away and back**, tap a number key and release it, or briefly use the bound right-click key between two END ticks. The sampled slot/binding may look unchanged, so a pending AutoRod or AutoSnowball could continue or restore the previous slot over human input.

R3.58 closes this narrow gap using **actual native Forge input-event evidence**, not an inferred mouse state or a synthetic scan.

## Actual runtime changes

1. `ProjectileManualInputGate`: one owner-thread bounded boolean latch, coalescing any number of real native hotbar number-key presses, LWJGL mouse-wheel movements or physical bound use-item button presses until **exactly the next** client END tick. The callback never intercepts or cancels human input. A world/GUI lifecycle transition resets stale-event evidence. A rapid away-and-back or press-and-release still leaves a pending manual intent.
2. `ForgeEventAdapter.onKey/onMouse` records only eligible in-game/current-world physical events. On the next END tick it consumes the latch once, calls `autoRod.reset(true)` and `autoSnowball.reset(true)`, and withholds GhostC's optional combat output for that tick. `reset(true)` makes restoration fail closed even if the native current slot happens to equal our temporary rod/snowball selection. Neither controller overwrites a manually selected slot.
3. Both actual native projectile controllers also detect a *currently held* use-item input during their owned transaction and cancel in favor of the user's right click. This protects when an input callback was missed or the input was held into the sampling boundary.
4. `ProjectileOwnerArbiter` explicitly represents `NONE / ROD / SNOWBALL / CONFLICT`. A transaction retained by Snowball continues to win over newly eligible Rod; Rod has first refusal on simultaneous fresh candidates. If both modules ever claim the hotbar at once, the immutable frame enters **CONFLICT** and cancels both without an optional native attack or item-use write.
5. The original `AutoRodPolicy` and `AutoSnowballPolicy` SELECT → USE → RESTORE, world/player identity, native Minecraft `sendUseItem`, committed-only cooldown, F7 module defaults and F6 Panic semantics remain unchanged. There are **no packets, Netty mutation, key-state spoofing, motion changes, anti-server behavior or extra automation modules**.

## Tests and evidence limits

- `ProjectileManualInputTests`: 80,000 seeded owner/input frames including coalescing, repeated manual events within one native tick, explicit quick away-and-back with unchanged final slot, wrong-world restore, same-world safe restore when no manual input, foreign-thread writes denied, all four owner states and simultaneous Rod/Snowball claim failure.
- Mandatory `IntegratedModuleCatalogTests` and `tools/test_forge_wiring.py` require actual Forge key + mouse event registration, use-item press and wheel/number-key latch, native owner/CONFLICT gate, cancellation and no-overwrite paths.
- Exact-head Core Java17/21 and Forge 1.8.9 Java8 Linux/Windows reobfuscation + native method linkage/CI and downloaded Windows artifact rehearsal still required. **These tests do not launch original Minecraft**. Actual input-event ordering, rapid mouse wheel timing, manual-use animation and server projectile acceptance remain unverified without a separate in-game run.

No archive, local install, merge, force-push or visual branch edits.
