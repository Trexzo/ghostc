# GhostC R2.86 — inventory tick eligibility and bounded queue pruning

## Source-level defect

`InventoryCoordinator.submit` previously persisted an expiry but not
the intended start tick. `poll` therefore ranked all unexpired pending
transactions, including ones scheduled *in the future*. A high-priority
future inventory operation could preempt work already eligible on the
current tick, violating scheduling and action order.

The poll and cancel-owner paths also copied all queued values into
a new `ArrayList` before removing expired or canceled entries.
That avoidable full-copy allocation occurred on owner ticks when any
inventory work was queued.

## Fix

- Preserve each transaction's `start` from its validated submission tick,
  including when producing ACTIVE, CONFIRMED and REJECTED result copies.
- `poll` discards stale-session/expired entries and chooses only those
  whose start is at or before the queried tick. Equal priorities still
  resolve by ascending transaction ID; future entries remain QUEUED.
- Use one iterator pass for pending pruning and candidate selection,
  and iterator deletion for cancel-owner, with no per-poll list copy.
  The empty queue fast path avoids scanning anything.
- Fail closed rather than overflow `nextId` into negative/sentinel or
  reused inventory acknowledgement tokens.

## Verification

Pure Java8 `ServicesTests.inventoryScheduling` covers 12,000 seeded
future/mid/immediate priority mixes, wrong-generation pruning, exact
start eligibility, active single flight, explicit acknowledgement,
expiry before activation, pending-owner cancellation and active-owner
cancellation. Equal-priority FIFO and near-`Long.MAX_VALUE` token
overflow cases are also checked. Source-wiring tests assert retained
start, eligibility guard, iterator-only queue cleanup and overflow
fence. Existing service/session tests remain in the gate.

Require exact-head Core Java17/21 and Forge Linux/Windows JDK8 build,
reobfuscated JAR verification, simulated Windows install/rollback,
tested/merged tree equality and green postmerge workflows before
certification. Native Minecraft 1.8.9 inventory interactions, visual
behavior, FPS and extended session stability remain unverified.
