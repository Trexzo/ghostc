# GhostC R2.91 — action and rotation FIFO ordinal overflow fences

## Source-level risk
ActionCoordinator and RotationCoordinator use increasing private long
ordinals as deterministic tie-breakers between equal-priority requests.
Neither protected ++ordinal/++nextOrdinal from reaching Long.MAX_VALUE.
On overflow, the next accepted request would receive Long.MIN_VALUE and
be treated as older than existing work. That violates FIFO ordering
and could change the chosen action or rotation unexpectedly.

## Change
Both request APIs now refuse submission before incrementing an exhausted
sequence. This refusal is transactional: existing pending requests, their
arguments and their ordering remain unchanged, including when the
exhausted request would replace an existing owner/phase/scope key.
The caller receives IllegalStateException, not a negative wrapped
ordinal silently inserted into the arbitration map.

ActionCoordinator.reset and RotationCoordinator.clear reset their
internal ordinals to zero *after* removing all requests. A session
transition therefore releases ordinal exhaustion safely, because no
previous request survives the boundary. Rotation shutdown also clears
its private counter as part of terminal teardown. Priority, per-phase
and scope identity, generation/tick eligibility and tie-order semantics
otherwise remain unchanged.

## Regression
The pure Java8 ServicesTests.arbitrationOrdinalBoundaries method runs
9,000 repeated near-Long.MAX_VALUE request/refusal/reset/retry trials
for both services. It verifies transactional overflow rejection on
new and replacement owner keys, preserved original winners and
new-session equal-priority FIFO. An integration case forces exhaustion
inside SessionServices then transitions worlds and verifies both
private counters reset while the new session can submit work.

Before merge, gate exact PR-head Core Java17/21 and Forge
Linux/Windows JDK8 compilation/reobfuscation/JAR checks and simulated
Windows installation/rollback. Compare tested vs merged source-tree
hashes and obtain successful postmerge workflows before certification.
Native Minecraft 1.8.9 input arbitration, GUI, FPS and long-session
stability remain unverified.
