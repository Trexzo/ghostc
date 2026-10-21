# GhostC R2.46 — live-world crosshair combat target validation

## Source-audited user-facing defect

The Forge owner tick reads Minecraft's `objectMouseOver.entityHit` (a crosshair reference from rendering) and previously considered it a valid combat target whenever it was a non-dead `EntityLivingBase` other than the local player. The code did **not** verify that this cached reference still belonged to the currently loaded world. A despawn, world transition, or entity-ID reuse can leave a valid-looking Java object even though that object is no longer the world entity. Zero, negative, NaN, and infinite entity health could also pass. The boolean `validTarget` is shared by three opt-in combat features, so a stale reference could enable an extra action intended only for a real target.

The same owner tick previously did not explicitly stop synthetic combat when the local player is dead or has non-finite/depleted health. This is a final-admission correctness issue, independent of whether a specific Minecraft server reproduces it.

## R2.46 implementation

- Add Minecraft-independent Java8 `LiveCombatTargetGate.eligible(cached,inCurrentWorld,local,dead,health)`. It requires a non-null cached crosshair entity, **exact Java object identity** with the object returned by the active world's entity map for that ID, exclusion of the local player, no death flag and finite positive live health. Numeric ID alone is intentionally insufficient.
- `ForgeCombatController` applies the guard directly after validating the crosshair hit type and `EntityLivingBase`, resolving the candidate's ID from **`mc.theWorld.getEntityByID(...)`**. `validTarget` is computed only from the admitted entity, so the existing target-required Clicker, Hit Select and Block Hit share one consistent, current-world admission decision.
- The early local-player guard now refuses synthetic attack, use and camera intents if the player is dead or has non-finite/nonpositive health. Existing fail-closed `reset()` cancels owned scheduling/state.
- No new direct attack, packet interception, aim angle algorithms, bypass policy or Minecraft-only dependencies are introduced in the core. The vanilla physical click path is unchanged. All combat features retain their current default-disabled setting.

## Deterministic regression

- `LiveCombatTargetGateTests` checks null/despawned/self/dead/nonpositive/nonfinite health, same-object acceptance, and explicit recycled-ID object mismatches. It exercises **160,000 seeded combinations** of cached and world objects with health states, plus **10,000 respawn transitions** comparing stale and newly loaded objects (20,000 object-identity checks).
- Includes an `ActionCoordinator` negative-case integration ensuring that stale cached hits cannot produce a target-dependent synthetic intent in the modeled gate, and a pure helper owner/read-only test.
- Forge wiring checks identity resolution **before** `validTarget`, local-player liveness before any synthetic scheduling, preservation of the three target-policy gates and packaging of `LiveCombatTargetGate.class`.
- Exact-head Java17/21 Core, Linux/Windows JDK8 Forge compile/reobfuscation, binary/JAR provenance and Windows fake-profile install/collect/rollback must pass before merge. Then verify merged Git tree identity with the tested PR tree; only claim post-merge CI when corresponding runs exist.

## Limits and remaining acceptance

Headless core tests and Forge compilation verify code contracts, not a live in-game result or measured attack suppression on a server. The existing R2.42 Target HUD uses its separate live lookup/health fence. Aim Assist continues to select from the same-tick snapshot; R2.46 does not claim to solve every race across that path. Minecraft 1.8.9 launch, in-game visual parity, measured FPS and 60-minute stability remain unverified, and G1/G2/G4/G5 gates stay open.
