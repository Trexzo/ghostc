# GhostC R2.25 — range-first ESP visibility checks and configurable render draw workload

## Audited R2.24 costs

In R2.24, the Player ESP renderer called `mc.thePlayer.canEntityBeSeen(player)` on every alive loaded player when visible-only was enabled **before** checking `getDistanceSqToEntity` against the configured ESP range. Every far-away player therefore incurred an unnecessary line-of-sight query. The R2.24 distance-fade path also recomputed `getDistanceSqToEntity` while rendering, even though `EspNearestBudget` already stored that same validated squared distance during nearest-player admission.

In addition, the owner could not choose a smaller player draw limit: all selected nearest-80 entities underwent the GL drawing path. That capped memory but did not let a user trade ESP density for reduced CPU/GL submissions in a crowd.

## R2.25 implementation

- Move the finite nonnegative squared-distance and ESP range check **before** the optional `canEntityBeSeen` call. Valid in-range and visible entities retain exactly the same behavior; beyond-range and malformed candidates no longer produce visibility queries. `renderable` still performs its established finite bounding box / health / skeleton geometry admission before the `nearest.offer` call.
- Add `render.esp.max_players`, a persisted typed numeric GUI setting **1–80**, default **80** for full backward compatibility with existing profile files and onscreen selection. The setting affects the number of GL-drawn players, **not the independent nearest-80 selection capacity or world/player entity scan**. With a configured N, draw only the closest `min(N,nearest.size())` candidates after deterministic distance/entity-ID selection.
- The original filled-style painter order remains **far-to-near within the chosen nearest N** rather than choosing the farthest N from the 80-entry buffer. Other (nonfilled) styles keep closest-first GL order. The pure Java8 `EspDrawLimit` policy validates all cap/count/slot indexes and makes this dispatch independently testable without Minecraft.
- For an enabled `distance_fade`, compute alpha using `nearest.distanceAt(slot)`, the same validated squared distance used for candidate sorting. This avoids the old redundant Forge player-distance method call and preserves exact fade selection semantics. When fading is disabled, preserve the short-circuit with no squared-distance fetch or fade square root.
- All nine ESP styles, opt-in health gauge, RGB/fill opacity, maximum 80 candidate memory, immutable settings/profile storage and GL-state restoration retain their previous behavior. No packet, combat, authentication or publishing changes.

## Automated evidence / remaining checks

- New `EspDrawLimitTests`: 9,000 deterministic shuffled crowds with up to 300 entities, capacity 80, configured budgets 1–80, nearest subset membership, both painter directions, nonselected overlay omission, unique IDs, reused squared distances, finite optional fade output and no retained entity refs. Edge/invalid inputs and actual HostRuntime GUI slider/profile persistence and owner-thread control are also exercised.
- `FeatureTests` extends existing default and persistence checks. Forge source wiring requires the range gate to precede LOS, closest-subset painter ordering for filled styles, and `nearest.distanceAt(slot)` reuse, as well as the JAR class inventory.
- Version **2.25.0**, **43 Java test suites** and **38 milestone documents** (R08–R45). Required merge gates are Java17/21 core, Linux+Windows JDK8 Forge1.8.9 compile/reobfuscation/JAR verification and Windows fake-profile install/collect/rollback.

**Proof boundary:** This change removes an unconditional out-of-range LOS work path and allows fewer submitted ESP overlays; it does not prove a quantified Minecraft FPS improvement. Real in-game rendering, neighboring mods, crowd responsiveness, GUI reachability and a representative 60-minute soak still require runtime acceptance. Keep the relevant open issues.
