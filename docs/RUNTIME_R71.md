# GhostC R2.51 — action intent phase identity

## Source-audited G3 defect

`ActionCoordinator` defines three distinct scheduling phases (`PRE_INPUT`, `SIMULATE`, `OUTPUT`) but stored pending requests by `owner#type`, omitting phase. If the same module proposed the same action type for different phases, the newer request overwrote the earlier one before any arbitration. This violated phase-local intent ownership even though the consumer explicitly filters by phase. The current Forge combat adapter uses `OUTPUT`, but the core contract supports all three phases.

## Implementation

- Key pending requests by `owner#type#phase`. Module IDs are restricted by `ModuleRegistry.validateId`, and the type and phase are fixed enums, so string identities are unambiguous.
- Re-requesting the same owner/type/phase replaces that exact pending intent. Different phases of the same action and different action types remain independent.
- `cancelOwner` now removes every action type and phase for that owner, including partially formed intents from faulted modules.
- Preserve **at most one successful consumption per action type, tick, and generation** using the existing consumed/type maps, not per-phase consumption. A second phase remains pending and can be consumed only on a later tick within TTL; it cannot cause a same-tick double attack.
- Preserve 256 total pending requests across all phase keys, with exact-key replacement accepted when full; no additional gameplay action or packet emission.

## Regression and verification

- Extend Java8 `CoordinatorHygieneTests` for two phases of one action, distinct consumption ticks, same-phase replacement, cancellation, 256-capacity admission and eviction, future expiry, and 4,000 seeded deterministic owner-tick cycles with generation resets.
- Forge source wiring tests ensure phase-inclusive key composition, per-phase cancellation, existing type-global consumption, and capacity guard.
- Exact PR-head CI required: Java17/21 Core; Linux and Windows JDK8 Forge compilation/reobfuscation and JAR provenance; Windows simulated profile install/collect/rollback. Verify merged tree equality and post-merge CI separately.

## Limits

No direct Minecraft behavior changed. Build tests cannot establish Minecraft 1.8.9 startup, runtime GUI appearance, combat behavior, measured FPS, multi-mod interoperability or 60-minute stability. G1–G5 acceptance remains open.
