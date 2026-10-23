# GhostC R2.83 — snapshot visitor mutation and recursion isolation

## Source-level risk
WorldSnapshotAdmission publishes nearest visible/hidden candidates and
an optional crosshair pin through an owner-thread visitor. The visitor
previously could synchronously call `clear`, `offer`, `pin`, or another
`forEach*` while the internal indices were being walked. The stream
would then silently truncate, mix old/new entity references or recurse,
depending on what the visitor did, violating current-frame snapshot
integrity.

## Fix
A lightweight boolean emission guard prevents any candidate mutation or
nested emission while visiting the live sorted buffers. It applies to
both ordinary and pinned streams. Read-only size and visibility queries
remain legal. The guard resets in a finally clause even when visitor
code throws, allowing the next frame to clear and rebuild its buffer.
This adds no per-frame candidate-list allocation and preserves existing
distance-then-entity-ID ordering and the 256+256+1 cap.

## Verification
Pure Java8 `PinnedSnapshotTests.visitorMutationGuards` checks denied
clear/offer/pin and recursive normal/pinned streams at every visited
slot, unchanged buffer and publication order, read-only size queries,
throwing visitor cleanup and subsequent retry. It also performs 12,000
seeded optional-pin/visible/hidden frame replays with deliberate
mutation attempts from callbacks, verifying exact output and stable
sizes. Existing crowded nearest admission, world clear and performance
regressions remain.

Before certification require exact PR-head Core Java17/21 and Forge
Linux/Windows JDK8 compile and reobfuscation/JAR checks, simulated
Windows installation/rollback, identical tested and merged source
trees, and successful postmerge workflows. Native Minecraft 1.8.9
snapshot publication, FPS and visual gameplay require separate tests.
