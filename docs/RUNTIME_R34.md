# GhostC R2.14 — visible-only target starvation repair

## Exact source issue

R2.13 changed shared world snapshots to preserve the 256 *nearest* loaded players regardless of their visibility. Target HUD and Aim Assist both default to `visible_only`, filtering those 256 after publication. If 256 nearer players were behind walls, they could exhaust the entire snapshot and leave no eligible visible target even though other, more distant players were visible and within configured range. This was not addressed by ordering loaded entities by distance.

## R2.14 implementation

- New Java-8-compatible, Minecraft-independent `WorldSnapshotAdmission<T>` owns **two independent** `EspNearestBudget<T>(256)` instances: one for currently visible players and one for occluded/invisible players. Every candidate passes the existing finite-health/position/squared-distance check before inclusion; visibility is read once on the Forge client thread before offering the candidate.
- A streaming two-way merge emits candidates in global squared-distance and entity-ID order without allocating a merged array. Up to 512 immutable `WorldSnapshot.Entity` records may be published, below the core's established hard limit of 4,096. A visible-only query sees its closest 256 visible players, while an unfiltered query still has its nearest 256 across both groups. Existing TargetSelector distance, range, health and visibility decisions remain authoritative.
- The admission buffer also prevents duplicate entity IDs crossing the visible/hidden partitions. Visible entries take precedence when the same ID appears in both; nearer duplicates in each `EspNearestBudget` replace farther ones. Invalid candidates cannot poison admission.
- The pre-existing immutable-world publication, exact generation/tick contract and original module IDs/settings remain intact. The existing ESP renderer still maintains its separate maximum of 80 eligible players; it does not use these target snapshot lists. A `finally` release wipes all raw `EntityPlayer` references after snapshot publication.
- There is a deliberate worst-case tradeoff: at most 512 candidates rather than 256 are materialized each tick, and the Forge adapter checks visibility for candidates before admission. This improves correctness for both `visible_only` settings in crowded scenes but requires real Minecraft profiling before claiming negligible overhead.

## Test coverage / acceptance

- New `VisibilitySnapshotTests` covers 400 nearer occluded entities loaded before 400 visible players (both within HUD range), verifies 256 selected from each class, nearest visible Target HUD and Aim Assist access, and nearest hidden target access when visibility filtering is disabled.
- 88 randomized full-list shuffles verify stable 512-element global distance+ID ordering, 190 repeated 700-player frame populations test bounded admission and reference cleanup, and duplicate-ID, malformed distance, owner-thread and world reset cases test isolation. The existing `EspNearestBudgetTests` extends duplicate replacement/compaction tests; R2.13 nearest snapshot regressions remain unchanged.
- This batch retains 31 established Java test classes, adding a 32nd; cumulative milestone docs R08–R34 total 27. Version metadata is **2.14.0**.
- Required gates before merge: Java 17/21 core and Forge source contracts, Linux/Windows JDK8 Forge1.8.9 compilation/reobfuscation/JAR inspection, and Windows fake-profile install/collect/rollback.

**Do not confuse offline verification with a game benchmark:** A real licensed Minecraft 1.8.9 test is still required to evaluate 512-player snapshot performance, correct visual Target HUD, Aim Assist, occlusion transitions and extended gameplay stability. Do not close the outstanding acceptance issues based only on CI.
