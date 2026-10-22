# GhostC R2.68 — crowded-team Target HUD snapshot completeness

## Source-audited defect

R2.67 Target HUD `ignore_teammates` filters already-published snapshot entities. Snapshot admission, however, independently retains the 256 closest visible and 256 closest hidden candidates for all consumers. On crowded multiplayer servers, a visible partition filled by teammates can omit the closest enemy entirely. Filtering the snapshot then yields no HUD target despite an in-range, live non-teammate.

## Corrected behavior

- For an enabled Target HUD with **Nearest** selection and **ignore_teammates=true**, the Forge owner-tick scanner additionally identifies the single closest live enemy (same non-null scoreboard team logic as R2.66/R2.67). It requires the normal range/health/world-identity and current visible-only admission gates.
- The candidate uses the one already-existing optional `WorldSnapshotAdmission.pin` slot. It is not a third nearest pool: the unchanged visible/hidden budgets remain 256 each, and published snapshot capacity is at most **513** records.
- A new pure Java8 `TargetHudTeamPin` tracks only nearest distance/ID primitives, choosing the closest eligible candidate with ID tie-break; it retains no raw Minecraft references. Clear it at start and in `finally` after snapshot publication.
- The pin logic runs only in filtered-nearest mode. Crosshair mode retains its own single pinned reticle target and never competes for the same pin. HUD with teammate filter disabled and Aim Assist's shared candidate priorities are unchanged. No additional LOS rays, packet or attack actions are introduced.
- The R2.67 final live-world team gate is still applied at paint time, so players joining the local team after a snapshot cannot appear accidentally.

## Verification

- `TargetHudCrowdedTeamPinTests` reproduce a 700-teammate crowded snapshot where 256 nearer visible candidates evict the nearest enemy, demonstrate the existing 513-slot snapshot pin fixes R2.67 nearest filtered selection while legacy nearest remains unchanged, and verify deterministic crosshair parity.
- 60,000 randomized nearest/range/visibility/tie-break sweeps, invalid input boundary checks, and lifecycle cleanup.
- Forge source wiring checks require opt-in mode, team and world identity validation before pinning, same single `pin` and `forEachWithPinned` pathways, no new snapshot budget and cleanup.
- Merge acceptance requires exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 compilation, JAR provenance plus simulated installation/rollback, matching tested/merged tree, and passing post-merge exact main CI.

Native Minecraft 1.8.9 behavior, FPS, scoreboard interoperability and long-session compatibility remain untested in-game by automated CI.
