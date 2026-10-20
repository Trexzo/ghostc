# GhostC R2.16 — per-consumer LOS radii and zero-sort target hot paths

## Source-driven issues addressed

1. **R2.15 still ray-checked beyond the visible-only consumer's radius in mixed modes.** Its `SnapshotDemand` combined HUD range (at most 24 blocks) and Aim Assist range (at most 6) into one admission radius. If Target HUD was unfiltered out to 24 and Aim Assist was visible-only only to 6, every potentially eligible player within 24 was ray-tested—even though only the inner 6 could be used by a visibility-requiring feature.
2. **Two consumers repeatedly sorted lists just to choose one player.** `TargetHudPolicy.select` built a new list and sorted it via `TargetSelector.select(DISTANCE)` for the nearest target. `AimAssistPolicy.suggest` also built/sorted this list before examining eligible players for angular error. At 512 target snapshot entries this incurred avoidable per-client-tick allocation and comparator sorting.

## Implementation

- `SnapshotDemand` now retains independently derived squared distances for the farthest enabled **visible-only** and **unfiltered** target consumers, plus their union. Forge rejects nonfinite and out-of-union candidates as before, but calls `canEntityBeSeen` only when the candidate is inside the visible-only radius. All admitted records must pass their relevant group's radius, so hidden candidates outside the unfiltered radius are dropped; candidates within the unfiltered radius remain selectable without needing visibility results beyond the visible-only radius.
- `TargetSelector.nearest` is a one-pass, allocation-free closest-eligible scan with the same finite/range/health/visibility/player and distance/ID tie rules as the original sorted `TargetSelector.select(DISTANCE)`. `TargetHudPolicy` uses this helper. `TargetSelector.select` remains public and unchanged for all multi-result consumers, and its admission rule is shared with the new single-target scan.
- `AimAssistPolicy` traverses the immutable WorldSnapshot directly, using the same shared eligibility gate and original angular error/target tie policy. No sort/list is created in its hot path. Existing camera/write ownership, FOV, click-to-aim, turn-speed bounds, settings and generated suggestions stay under the existing Forge fault guard.
- The snapshot still publishes a valid empty immutable world when neither feature is enabled, retains the two 256-entry partitions for mixed visibility policies, and clears raw `EntityPlayer` references in `finally`. The ESP renderer retains its separate nearest-80 policy. No packet, identity, login or filesystem path changes.

## Test and acceptance evidence

- Existing `SnapshotDemandTests` extend the original 16-case matrix and 18,000 reproducible input scenarios with exact independently derived squared radii, in-group admission decisions and mixed HUD24/aim6 LOS count/target parity. A constructed crowd verifies only inner visible-only candidates trigger rays while farther unfiltered HUD candidates remain available.
- New `TargetHotPathTests` keeps a **reference implementation** using the original retained sorted TargetSelector API and original Aim Assist math. Tests compare Target HUD nearest ID, Aim Assist target ID, yaw/pitch/error and empty/excluded/tie behavior across 1,800 seeded player worlds (multiple preferences) and repeated 512-entry snapshots.
- All previous 33 Java test suites are retained, adding a 34th; cumulative R08–R36 milestone documents number 29. Release version **2.16.0**.
- Merge gates: Java17 and Java21 `--release 8` core tests, Forge 1.8.9 Linux/Windows JDK8 compile/reobfuscation/JAR inspection and Windows fake-profile install/collect/rollback.

**Important limits:** Source-level removed sorting and additional LOS checks are verified by code and offline tests, not measured real Minecraft wall-clock, FPS or gameplay results. Real 1.8.9 in-game startup, visual target correctness, ray-check overhead and a representative 60-minute soak remain outstanding.
