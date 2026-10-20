# GhostC R2.27 — candidate-bound visibility and geometry pruning

## Source-level issue identified in R2.26

R2.25 moved the ESP range check before visibility line-of-sight rays. However, the renderer still sent **every living in-range player** through the potentially expensive `canEntityBeSeen` visibility query and geometry validation, even if the fixed nearest-80 buffer was already full of closer valid players. Those farther candidates could not change the final drawn set, regardless of whether they were visible. This was wasted source-level work in crowded worlds.

## New exact selection preflight

- Introduce `EspNearestBudget.couldEnter(int id,double distanceSq)`, a **read-only, client-owner-thread** admission hint. It returns false for nonfinite/negative distances; for a full buffer, a candidate strictly farther than the last (worst) selected player cannot enter. For equal squared distances, a candidate with the same or larger entity ID than the current worst cannot displace it, matching the existing stable distance/ID tie-break.
- An already-selected ID is checked independently: an equal or farther duplicate cannot enter; a strictly closer duplicate remains eligible to replace the old entry. This retains the exact `offer` behavior and avoids duplicate-driven entity identity drift.
- Forge calls the new gate **after** the existing range/finiteness check and **before** line-of-sight and geometry computation. When the buffer is not full or a candidate can improve its ranking, the existing `canEntityBeSeen`, `renderable` and `nearest.offer` gates remain unchanged.
- No sorting or storing of new player objects is introduced. This avoids work rather than reordering candidates. The final nearest-80 selection, optional 1–80 draw limit, filled volume far-to-near painter order, source of squared distances for fade, health placements, RGB, profiles and diagnostics submissions stay as before.
- No packet/gameplay/account/authentication code is modified. Existing `EspNearestBudget.offer` remains unchanged and authoritative.

## Deterministic correctness evidence

- New `EspLosPruningTests` compares an independent **original no-pruning selection** against the optimized gate across **5,000 seeded crowded scenes** containing 80–320 candidate records, randomized list order, occlusion, malformed ranges, geometry rejection, duplicate IDs and exact-distance ties.
- Every scene checks selected player IDs, squared distances and source object identity in the complete nearest-80 buffer, plus all chosen 1/5/24/80 draw-limit subsets in both filled and nonfilled painter ordering. The original and optimized paths must return identical chosen results, and optimized LOS/geometry call counts can never increase.
- A deterministic ordered 300-player all-visible scene shows **300** original LOS checks versus **80** with safe pruning. The numbers are an offline algorithmic demonstration, **not actual Minecraft runtime performance**.
- Existing nearest-budget regressions cover hints for invalid, tied, near, far, and repeated IDs, no selection mutation and cross-thread access rejection. Updated Forge wiring/source checks require the pruning gate between range check and LOS/geometry, and all previous suites remain.
- Version **2.27.0**, **45 Java test suites**, cumulative **40 milestone documents** (R08–R47).
- Mandatory release gates: Java17/21 core/wiring regressions, Linux/Windows JDK8 Forge 1.8.9 compilation/reobfuscation/JAR integrity and Windows fake-profile install/collect/rollback.

**Verification limit:** CI proves deterministic candidate equivalence and source-level reduction of unreachable ray work; it cannot establish actual Minecraft FPS, visible pixel parity, mod compatibility or a 60-minute stability session. Keep runtime acceptance issues open pending real-game evidence.
