# GhostC R2.89 — bounded synchronous capacity-rejection callbacks

## Source-level failure

When `PacketCoordinator.submit` encounters a full bounded queue, it
increments `rejected`, invokes the caller-supplied completion with
`REJECTED`, then returns -1. This completion runs synchronously. A
completion that submits another request into the same still-full queue
will itself receive a synchronous `REJECTED` callback, creating an
unbounded recursive chain and risking a stack overflow, despite the
existing guard on recursive flushes. An exception thrown from the
completion is contained by the existing callback-failure accounting,
but only after a potentially very deep recursive call chain.

## Change

A dedicated owner-thread `rejecting` flag spans the immediate
capacity-rejection callback. A *nested full-queue submission* throws
`IllegalStateException` before it mutates rejection metrics or
delivers a second callback. This is an unaccepted request, not a
silently lost accepted packet. The original outer submission still
returns -1 and gets exactly one REJECTED completion.

The flag resets in `finally` even after a callback error. Legitimate
callback reset/shutdown/cancel remain allowed; if they make space,
subsequent nested submissions may be accepted normally, with positive
IDs and later transport delivery. No change is made to accepted packet
completion semantics, owner confinement, flush budgets, stale-session
reset, or overflow fencing.

## Verification

`PacketLifecycleReentryTests.rejectedCallbackReentry` executes 6,000
deterministic full-queue cycles across six modes: uncontrolled recursive
rejection contained as one callback failure; explicitly caught nested
refusal; callback reset and accepted replacement; callback shutdown;
throwing REJECTED completion; and callback cancellation followed by
accepted replacement. Each checks exact callbacks, queue ownership,
metrics and post-callback flush/final settlement where applicable.
Forge source-wiring checks ensure nested rejection is denied before
counting or invoking another completion, and the guard always clears.

Require exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 builds,
JAR verification, simulated Windows installation/rollback, tested/merged
source-tree equality and successful postmerge workflows. This is
headless coordinator correctness only; native Minecraft 1.8.9 packet
integration, GUI, FPS and long-session behavior remain unverified.
