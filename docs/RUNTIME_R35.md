# GhostC R2.15 — demand-aware target snapshot admission

## Audited performance issue in R2.14

The R2.14 visible/occluded snapshot publisher evaluated `minecraft.thePlayer.canEntityBeSeen(p)` for every living loaded `EntityPlayer` every client END tick. It also built up to 512 candidates when neither Target HUD nor Aim Assist was enabled. Those behaviors created unnecessary line-of-sight ray checks, work and immutable snapshot allocations in crowded scenes, even when an enabled consumer could only target players nearby.

These observations are from the source code. No measured live-game frame-time comparison is available.

## New first-party policy and runtime wiring

- `SnapshotDemand.of` derives a **per-tick, validated** target-data policy from existing `hud.target` and `combat.aimassist` enabled state, `visible_only` choices, and their persisted target ranges. Target HUD range remains at most 24 blocks and Aim Assist at most 6 blocks. Disabled consumers do not enlarge the admission radius.
- **Neither target feature enabled:** Forge does not enumerate loaded players or call LOS ray checks, and publishes a legitimate empty immutable `WorldSnapshot`. This preserves `SessionServices.publish` freshness, tick/generation admission and the combat bridge's requirement for a current snapshot before permitting optional click/hit/block operations.
- **Unfiltered-only target consumers:** Forge walks the player list once, rejects out-of-range and malformed candidates, then retains the closest 256 with visibility marked as false for the unfiltered query. It performs **zero LOS ray checks** because callers explicitly ignore the visibility flag. Existing target ordering and entity ID tie-breaks remain unchanged.
- **Visible-only target consumers:** Out-of-range or invalid players are skipped *before* visibility ray checks. Only visible players enter the bounded nearest-256 partition; other candidates are not allocated into the shared immutable snapshot.
- **Mixed visible-only and unfiltered consumers:** Within-range players get an LOS check; two nearest-256 buffers retain appropriate visible and occluded candidates, keeping R2.14's occlusion-starvation prevention and stable global distance/ID ordering.
- **Always retained:** Existing ESP renderer with its own 80-player budget, module IDs, settings, null/invalid-world handling, generation and tick fences, and `try/finally` clearance of raw `EntityPlayer` references. No protocol, packet, process memory or client authentication changes.

## Regression and limits

- `SnapshotDemandTests` checks the 16 boolean configurations of two modules and visibility preferences, enabled-only range validation, nonfinite distance rejection and no-consumer behavior; 18,000 seeded variable-range cases verify gate consistency.
- Synthetic 1,350-player inputs confirm no-enumeration/no-ray mode, zero LOS queries for unfiltered-only targets, far-distance filtering, visibility-only starvation prevention, mixed-mode correctness for Target HUD and Aim Assist, and remaining cap limits.
- Forge source-level tests verify the exact demand construction, range-before-LOS ordering, preserved tick publication/cleanup; Forge JAR checks require the new policy class. Version **2.15.0** retains the 32 existing Java regression classes plus a new 33rd class and 28 R08–R35 documentation milestones.
- Required CI: Java17/21 core and source/wiring tests, Linux and Windows JDK8 Forge1.8.9 compilation/reobfuscation/JAR integrity, Windows fake-profile install/collect/rollback.

**Open acceptance:** These are code and offline-policy results. Only a real licensed Minecraft 1.8.9 session can establish FPS or tick-time improvements, target HUD/aim visual parity, and 60-minute stability. Existing runtime issues remain open.
