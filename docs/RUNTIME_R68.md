# GhostC R2.48 — live Target HUD range revalidation

## Source-audited problem

The Target HUD selects a nearby player from the immutable owner-tick world snapshot. R2.42 correctly refreshes health, death and invisibility before painting; R2.47 tightens snapshot publication. However, the HUD redraw previously did not re-check the target's range using **current coordinates**. If either the target or local player moved after snapshot publication, the card could be painted despite no longer satisfying the configured target distance.

## Change

- Add Minecraft-independent `HudLiveTargetGate.withinRange` to admit only finite live target/local coordinates and finite positive range. Use squared three-dimensional distance and fail closed if subtraction/squaring overflows.
- In `ForgeFeatureDriver.drawTarget`, resolve the selected ID from the current world as before, validate current health/death/invisibility as before, and then re-check **both entities' current positions** against `host.targetRange()` immediately before reading/rendering labels.
- Preserve the saved range and visible-only preference, Target HUD formatting, default enablement, other HUDs and all input/combat behavior. No extra line-of-sight ray or network action is added.
- Because snapshot records do not retain raw entity references, this change does not claim to distinguish two distinct entities with an identical recycled ID and identical live geometry. Normal selection is recomputed on the next owner tick.

## Regression and verification

- Existing Java8 `HudLiveTargetGateTests` extends its health/visibility replay with **120,000 seeded live-position cases**, exact distance boundaries, local movement, invalid coordinates/range and overflow behavior.
- Forge source wiring asserts the range check uses both live positions and occurs before label creation/draw.
- Build metadata and inventory advance to 2.48.0 / RUNTIME_R68. Gate merge on exact-head Core Java17/21 and Forge Linux/Windows JDK8 builds, JAR provenance, installation/rollback regressions; compare the merged tree and check any exact-main CI separately.

## Limits

The new policy rejects stale **out-of-range** cards; it is not a real Minecraft launch, a measured visual test, proof of render-frame parity or a 60-minute stability run. G1/G2/G4/G5 still require actual Minecraft 1.8.9 acceptance.
