# GhostC R2.95 — snapshot profiling must not abort a game tick

## Source-level issue
With Runtime Diagnostics opted in, ForgeEventAdapter samples System.nanoTime()
before target enumeration and directly computes end-start on successful snapshot
publication. A legitimate very large forward interval can overflow a signed
long subtraction into a negative elapsed value. SnapshotTelemetry rejects
negative durations; the resulting IllegalArgumentException would interrupt
the rest of ForgeEventAdapter.onClientTick after publishing the snapshot.

A genuinely backward clock sample could similarly abort the owner tick.
Neither event should let optional diagnostics interfere with gameplay.

## Change
SnapshotTelemetry.elapsedNanos(start,end) performs a lightweight signed-
monotonic timestamp check. For a backward sample it returns -1 and the Forge
adapter skips *only* the optional telemetry record. For a valid forward
interval it uses unsigned elapsed comparison to saturate at Long.MAX_VALUE,
which remains valid numeric-only input for the existing fixed-memory
Fenwick histogram and exported aggregate/mode reports.

The snapshot still publishes before optional instrumentation. No target data,
server identifiers or raw timing origin are stored or exported; the existing
histogram counts, 4096+ microsecond cap, precision and opt-in focus policy
are unchanged. No new allocation occurs in the per-player loop.

## Regression
SnapshotTelemetryTests.clockArithmetic exercises 24,000 seeded extreme
MIN-to-MAX intervals (saturating, not negative), 24,000 subframe values,
24,000 backward intervals, equal timestamps and ordinary negative-origin
timing. It also verifies skipped invalid recordings do not mutate counters,
while a saturated valid sample remains bounded and is classified correctly.
Python Forge wiring checks verify the caller guards the recording and does
not retain raw signed subtraction. Existing snapshot publication ordering
checks preserve exact tick admission.

Certification requires all four exact-PR-head Java and Forge CI jobs,
source-tree parity at merge, and both postmerge workflows. This is source-
level verification, not a claim of native Minecraft launch or observed FPS.
