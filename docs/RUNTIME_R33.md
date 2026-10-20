# GhostC R2.13 — nearest-first shared world snapshot publisher

## Audited source defect

R2.12 corrected the Player ESP 80-player render cap, but the Forge END-tick snapshot publisher used by `TargetHudPolicy` and `AimAssistPolicy` still iterated `theWorld.playerEntities` and stopped after the **first 256 qualifying entries**. Minecraft's loaded player list has no distance-order contract. On crowded worlds or after unusual entity ordering, nearby relevant targets could be excluded entirely. The early duplicate-ID set also admitted IDs before validating nonfinite geometry/health, so malformed entries could hide otherwise-valid alternatives.

## Production implementation

- Reuse the existing first-party `EspNearestBudget<T>` in the Java 8 core, increasing its supported fixed maximum capacity from 80 to **256** without altering any ESP behavior. The ESP renderer still explicitly constructs an independent 80-entry buffer, and the snapshot publisher constructs its own 256-entry buffer once on the owner thread.
- `ForgeEventAdapter.onClientTick` now prefilters dead/zero-health/nonfinite player states, calculates squared distance from the validated local pose and offers every qualified candidate to the 256-entry nearest-first priority buffer. The buffer retains ascending distance with stable entity-ID tie-breaks and rejects nonfinite distances and duplicate entity IDs; it does not depend on loaded-player list order.
- Only after selection does the Forge adapter construct the list of `WorldSnapshot.Entity` values in nearest-first order, preserving the existing immutable WorldSnapshot, generation and tick fencing, visibility checks and current player exclusion. `TargetSelector`, Target HUD, Aim Assist, action coordinators and combat settings keep their previous contracts. No packet/network, inventory, input interception or anti-cheat logic is introduced.
- Every selected raw `EntityPlayer` reference is released in a finally block, even on snapshot/publish exceptions or world changes. The per-tick snapshot remains bounded to 256, with no retained mutable entity references in the core consumer's immutable snapshot.

## Deterministic regressions

New `NearestSnapshotTests` covers 700 loaded players (600 distant decoys before 100 nearby targets), verifies that nearby Target HUD and Aim Assist targets remain admitted, checks 128 randomized full-list shuffles for stable 256-item ordering, validates ID ties/capacity/nonfinite candidates/thread ownership, and runs 250 crowded 500-candidate tick sequences with strict budget limits. Updated `EspNearestBudgetTests` requires 256 to be legal and 257 rejected, preserving the existing 80-player ESP cap. Forge source wiring contracts forbid the old `players.size()==256` early break and require pre-selection and cleanup.
 
Version **2.13.0**; the 30 existing Java test suites are retained, yielding 31. Milestone docs R08–R33 total 26. CI gates: Java17/21 tests, Linux/Windows JDK8 Forge1.8.9 compilation/reobfuscation/JAR inspection and simulated Windows install/collect/rollback.

## Runtime acceptance boundary

No source test establishes real Minecraft target reachability, visual HUD correctness, actual aim motion, FPS or the 60-minute soak. A licensed local Forge 1.8.9 runtime test with an appropriately crowded world or controlled synthetic test fixture remains necessary. Keep Issues #2/#3/#4/#5/#6 open for actual game evidence.
