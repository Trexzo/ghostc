# GhostC R2.87 — remove stale hidden copies after visible upgrades

## Source-level failure
`WorldSnapshotAdmission.offer` gives visible observations priority over
previous hidden observations for the same entity ID. But when the
visible nearest-256 partition was full, a valid new visible observation
could fail admission. Hidden cleanup was incorrectly conditional on
successful visible admission. The old hidden copy remained in the
published snapshot even though a newer valid observation had determined
the entity was visible.

This also prevented the optional visible crosshair pin from using its
additional bounded slot: the obsolete hidden copy suppressed that slot
because the same entity ID appeared resident in the hidden partition.

## Change
Every valid visible observation invalidates the stale hidden copy by
entity ID regardless of whether the visible nearest-256 accepts it.
The nearest-256 selection itself remains unchanged. A rejected far
visible candidate will not displace a nearer visible candidate, and it
will not be emitted unless an explicit crosshair pin is present. An
invalid/null/nonfinite visible observation cannot evict a valid hidden
copy because input validation occurs before either partition is touched.

This is a targeted hidden-first/visible-later correction. It does not
introduce an unbounded per-frame seen-ID set or claim to resolve every
possible duplicate input ordering beyond the admitted visible entries.

## Verification
`VisibilitySnapshotTests.visibilityUpgradeOutsideCap` first reproduces
the full visible-nearest partition, retained hidden duplicate, and
suppressed visible pin. It verifies stale hidden eviction, unchanged
visible quota, exactly one visible pin and invalid observation handling.
Then 1,600 seeded saturated scenes vary earlier hidden admission order
and pinning, followed by 12,000 sparse valid visibility upgrades.
Source-wiring checks require unconditional hidden cleanup after valid
visible offer and preserve the existing nearest/pinned admission path.

Release gates remain exact-head Core Java17/21, Forge Linux/Windows
JDK8 compilation and reobfuscated JAR verification, simulated Windows
installation/rollback, tested/merged tree parity and successful
postmerge workflows. Native Minecraft 1.8.9 snapshot visuals,
gameplay and performance remain unverified.
