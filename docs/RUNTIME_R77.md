# GhostC R2.57 — Target HUD Nearest / Crosshair selection

## User-visible behavior

GhostC Target HUD previously selected the nearest eligible snapshot player unconditionally. R2.57 adds a persisted, typed `hud.target.selection` choice. The default `nearest` is untouched. The opt-in `crosshair` mode shows a card only for the actual live player under the Minecraft reticle (not merely a nearby player); if there is no valid player hit, it intentionally shows **no card**.

## Implementation

- The Forge adapter checks `mc.objectMouseOver` for an entity hit, `EntityPlayer`, not local player, and that its entity ID resolves to **the same object** in the current world before forwarding its ID. Hits that refer to despawned/replaced players or old world objects are rejected.
- `TargetHudPolicy` remains game-independent. The original nearest overload is retained; the new opt-in selection overload consults only the current tick's immutable `WorldSnapshot`, and requires the selected entity ID to be a living, positive-health player within the requested range and visibility preference. There is no fallback to a different player when crosshair mode is chosen.
- Existing Forge render-time `HudLiveTargetGate.eligible` and `withinRange` remain authoritative if the player dies, vanishes, moves or changes visibility after tick publication.
- Settings registration adds the `selection` Choice to `hud.target` so old profiles automatically keep `nearest`; choice is persisted using the same UI action and profile mechanism. Palette/skin, text layout, HUD footprints and user anchors do not change.
- This is a read-only HUD feature: no synthetic input, packet change, camera write or new network access.

## Verification

- 30,000 seeded player snapshot scenarios comparing the new crosshair result against an independent visibility/range/health/player admission oracle, plus nearest-mode parity.
- Explicit tests for null crosshair, stale/missing snapshot entity, invisible/dead/nonplayer/out-of-range target, unknown mode, profile round-trip.
- Forge source-wiring checks ensure the exact-world identity gate runs **before** policy selection; Core Java17/21 and Forge Linux/Windows JDK8 build, artifact integrity, fake Windows installation/collection/rollback must pass on the exact head. Confirm tested tree = merged tree and main post-merge CI.

## Remaining native acceptance

Minecraft 1.8.9 actual HUD/reticle behavior and visuals, FPS, OptiFine/multi-mod compatibility and 60-minute stability are not certified by GitHub offline tests.
