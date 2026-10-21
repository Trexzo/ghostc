# GhostC R2.49 — live-world identity guard for ESP

## Source-audited defect

Combat, Aim Assist and target snapshots already require an exact current-world entity object before acting on a cached candidate. Player ESP instead walked `theWorld.playerEntities` and screened health, visibility, range and geometry without checking that `theWorld.getEntityByID(id)` still resolves to the **same Java object**. When a player has despawned or its numeric ID was reused, a stale list reference could produce an ESP overlay.

## Change

- Reuse the Minecraft-independent `LiveCombatTargetGate`, requiring exact object identity in the current world's ID map after cheap range/nearest and invisibility checks but **before expensive LOS rays or geometry**.
- Revalidate selected players immediately before GL emission, suppressing absent/replaced, dead, invalid-health or newly invisible players under visible-only mode. Do not draw the replacement from a stale ID.
- Preserve nearest-N selection order, render styles, opacity, existing LOS budget, settings, default enablement and gameplay/network paths. The draw-time gate does not add LOS rays.

## Verification

- Existing Java8 gate tests add 50,000 randomized ESP admission/paint identity/liveness cases and explicit same-ID replacement/despawn assertions.
- Forge source wiring tests enforce pre-LOS and pre-GL identity checks. Gate merging on Core Java17/21 and Linux/Windows Forge JDK8/reobfuscation, provenance, and fake-profile install/collect/rollback on the final PR head; separately check exact merged main CI.
- This is a source-level correctness fix, not verified Minecraft 1.8.9 visual appearance, server compatibility, FPS or a 60-minute soak. G1–G5 runtime acceptance requirements remain.
