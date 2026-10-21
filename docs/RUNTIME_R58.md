# GhostC R2.38 — pay only for actually drawn HUD/ESP work

## Source audit

Two first-party rendering paths performed work that never appeared on-screen:

1. `PlayerEspRenderer` maintained the nearest 80 players in its reusable sorted `EspNearestBudget` each rendered world frame, regardless of the saved `max_players` setting. `EspDrawLimit.count(max_players, nearest.size())` then drew only the closest N. On crowded worlds with N=1 or N=5 this can cause up to 79 unneeded buffer admissions, with visibility and geometry checks performed on candidates that could never be drawn.
2. `ForgeFeatureDriver.drawDiagnostics` created 14 diagnostic strings (including snapshots and FPS aggregates) on **every optional HUD frame**, even when `DiagnosticsHudLayout` returned a compact panel with only 1–13 rows, discarding all invisible strings.

## R2.38 implementation

- `EspNearestBudget.setLimit(N)` enables a validated owner-thread-only active cap from 1 to the immutable backing-array capacity, with no new buffer allocations. Both `couldEnter` and `offer` use this active limit. Shrinking prunes references above N; growing does not recreate discarded candidates. Normal use clears before configuring the next frame.
- Forge ESP reads its existing saved `max_players` setting and calls `nearest.clear(); nearest.setLimit(drawBudget);` **before** iterating world players. Once the nearest N are admitted, worse candidates are excluded prior to LOS and geometry validation. Existing distance/ID ordering, valid-geometry filtering, painter order and distance-fade calculations are preserved.
- The `WorldSnapshotAdmission` nearest-visible and nearest-hidden buffers are **not reconfigured** and continue to use their legacy maximum of 256 candidates per group. ESP still retains a full 80 when `max_players=80` (the default).
- The optional diagnostics HUD now formats a row only after `DiagnosticsHudLayout.sourceRow(slot)` has selected it. A new per-panel `DiagnosticLines` helper caches expensive aggregate FPS and snapshot reads, so shared data is fetched no more than once per rendered panel. All original 14 labels, values, order and fonts remain unchanged when the viewport fits the full panel; compact viewport priority order is unchanged.

## Deterministic regression and CI

- An extended host-independent Java suite replays **420 frames × 240 player candidates** with shuffled input order, tied distances, optional eligibility exclusions and `max_players` uniformly exercised from 1 to 80. It compares every chosen ID and squared distance against the original nearest-80 algorithm truncated at N, asserts that excluded far IDs are absent, and verifies shrinking/expanding active limits and independent 256-target snapshot capacity.
- Forge static contract tests assert the active limit is set before the player loop and before LOS/geometry checks, that full and compact diagnostics use the existing `sourceRow` mapping, the 14 index cases are still present, and FPS/snapshot samples are acquired lazily and cached per panel.
- Both Java 17/21 core suites and Linux/Windows JDK8 Forge builds, reobfuscation, JAR/provenance checks, Windows acceptance/rollback fixtures must pass on the exact PR head before merge; verify new `main` merge SHA separately.

## Acceptance boundary

This is a **structural reduction in redundant rendering work**, not a measured FPS improvement. Drawn output parity is verified for the pure admission policy, but the exact in-game ESP pixels, GUI scale transitions, performance in crowded servers, and 60-minute stability cannot be certified through headless CI. The G1/G2/G4/G5 licensed Minecraft runtime acceptance tasks remain open.

Recommended focused runtime check: compare ESP `max_players=1, 5, 80` in a local/authorized populated world with diagnostics on; verify the same nearest overlays at equal distance and confirm `max_players=80` remains visually unchanged. Resize the game to compact diagnostics layout and confirm the visible lines are identical. Record actual before/after FPS and render timings before claiming a performance gain.
