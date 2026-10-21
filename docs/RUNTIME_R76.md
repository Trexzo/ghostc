# GhostC R2.56 — optional Target HUD skin

## Feature

Target HUD appearance was previously fixed to its original purple and charcoal colors. R2.56 adds a **user-selected, persisted** `hud.target.skin` setting with two choices. `classic` is the unchanged default; `glass` provides a coordinated jade accent, dark blue-green panel and thin top/bottom/right border. The same text baselines, health bar geometry and panel dimensions are deliberately retained to avoid reworking established HUD positioning and clipping.

## Implementation

- `TargetHudPalette` is Minecraft-independent. It exposes two immutable shared token sets without allocating temporary colors/collections on render.
- Forge `ForgeFeatureDriver.drawTarget` reads the `HostRuntime.targetSkin()` choice, resolves its colors and paints the existing card footprint using the selected palette. Only `glass` renders an extra 1px outline. Both choices share the already-fenced live target identity, position, health, visibility, text-sanitization, label-fitting and screen-admission behavior.
- `HostRuntime` registers `skin` alongside the existing Target HUD settings. Earlier profiles omit this new key and therefore restore to `classic` without changing their serialized content or schema. Choice validation rejects invalid skins.
- No new Minecraft input, network, movement, target selection, GL capture, additional FPS metric or combat behavior.

## Verification

- Java8 `TargetHudPaletteTests`: bit-exact pre-R2.56 classic ARGB colors, distinct opt-in Glass colors, invalid choice rejection, reusable palette identity across 100,000 seeded frames, and persisted choice switching/recovery via existing UI actions.
- Core Java17/21 CI, Forge source wiring coverage, Forge Linux/Windows JDK8 compilation/reobfuscation, artifact integrity, fake Windows profile installation/collection/rollback.
- Separately verify tested-branch vs merge tree and post-merge commit workflows.

## Outstanding

Actual Minecraft 1.8.9 appearance, GUI control ergonomics, OptiFine/mod compatibility, measured FPS and stability are unverified until live runtime acceptance.
