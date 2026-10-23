# GhostC R2.94 — fail-safe frame telemetry for wide monotonic durations

## Source-level failure
FramePerformance.frame captures opt-in render callback intervals and bins
rounded FPS. Its existing `elapsed=nowNanos-previousNanos` used a signed
long comparison. A legitimate forward interval from near Long.MIN_VALUE
to near Long.MAX_VALUE can overflow signed subtraction to a negative
value, incorrectly incrementing `invalidIntervals` and dropping a frame
sample. This contradicts the recently hardened AutoClicker/CPS clock
arithmetic but affects only anonymized FPS diagnostics.

## Change
First establish signed-monotonic ordering with `nowNanos>previousNanos`.
A valid forward interval is nonzero mathematically, even if the signed
subtraction wraps. Convert only elapsed durations up to two seconds
into rounded FPS. For greater unsigned durations, the FPS value must be
zero (strictly less than 0.5 FPS). Keep the fixed 1001-bin histogram,
saturating counters, existing nearest-rank quantiles, snapshot fields,
heap sampling, pauses, and opt-in lifecycle gates unchanged.

Equal timestamps remain non-samples. A genuinely backward signed
timestamp is still counted as an invalid interval, then becomes the
next reference for normal recovery; this is distinct from a long valid
forward interval.

## Verification
FramePerformanceTests.wideForwardIntervals performs 12,000 seeded
near-MIN to near-MAX frame pairs, checking every valid sample lands in
the existing zero-FPS bucket, with no false invalid count and exact heap
sample totals. It then checks 60 FPS after an explicit suspend,
backwards-clock rejection, subsequent recovery, the exact two-second
rounding boundary and just-above-boundary behavior. Forge Python wiring
checks pin the signed-monotonic guard, unsigned elapsed comparison and
new test registration.

Release gate: exact PR-head Core Java17/21 plus Forge Linux/Windows JDK8
build/JAR/transactional install-rollback CI, tested-vs-merged tree
equality, and green post-merge workflows. These are headless evidence
and do not establish actual Minecraft 1.8.9 FPS or soak performance.
