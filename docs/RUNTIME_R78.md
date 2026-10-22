# GhostC R2.58 — crowded crosshair snapshot admission

## Reproduction and scope

R2.57 introduced Target HUD `crosshair` mode, but its selection policy requires the crosshair player's ID to appear in the published owner-tick `WorldSnapshot`. The original `WorldSnapshotAdmission` populates only the nearest 256 visible plus 256 hidden entities. In a crowded scene an eligible player under the crosshair may be farther than 256 other players within that partition's eligibility radius and be silently excluded. The reticle may be correct and the display may still go blank.

## Change

- Keep both established 256-item sorted visible/hidden partitions unchanged.
- When the Target HUD is both enabled and set to `crosshair`, the Forge source adapter captures the reticle player once, subject to exact live-world ID-to-object identity. The candidate must be visited in this tick's `playerEntities` scan and pass the same positive-health, finite-position, distance, visibility and demand-admission checks before pinning. No stale prior-world target is admitted.
- `WorldSnapshotAdmission.pin` reserves at most one additional reference; it does not replace or evict the ordinary nearest-256 candidates. `forEachWithPinned` merges the extra candidate in stable distance+ID order, but emits no duplicate when the pinned entity already appears in either normal partition. Thus the maximum source snapshot population is 513, well within `WorldSnapshot`'s 4,096 hard bound.
- The existing final publication visitor still rechecks live entity identity, death, health, coordinates and range; it can drop a pinned or ordinary candidate if changed during collection. The per-tick `finally` resets the admission buffers and pinned pointer.
- Pure `forEach`, visible/hidden capacities, Aim Assist target ordering, no-target zero-ray path, settings, skins and saved profiles remain unchanged. The Crosshair HUD is read-only, with no input/packet/aim writes.

## Verification

- New Java8 `PinnedSnapshotTests` cover 350 visible+350 hidden players, an otherwise-excluded reticle player, one optional slot, deterministic sorted ordering across shuffles, duplicates, null/invalid candidates, thread ownership, bounded churn and release/reset behavior. Also verify original two-partition visitor remains unchanged when pinning is unused or not requested.
- Forge source tests enforce reticle identity and snapshot admission order. Exact PR-head Core JDK17/21 and Forge Linux/Windows JDK8 compilation/reobfuscation, provenance and fake-profile install/rollback must pass.
- Exact merged-tree equality and main post-merge CI are separate acceptance checks.

No Minecraft game launch or visual/FPS/multi-mod/long-session acceptance is claimed.
