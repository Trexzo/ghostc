# GhostC R2.80 — atomic packet-owner cancellation with reentrant completions

## Source-level failure

`PacketCoordinator.cancelOwner` took a pending-queue snapshot, then
iterated it with calls to `cancel(id)`. Each cancellation invoked the
completion before advancing the batch. A completion is arbitrary owner-
thread callback code, and is allowed to invoke coordinator shutdown.
In that case the next `cancel` rejected the now-stopped coordinator,
so `cancelOwner` escaped midway through its promised batch rather than
returning the original matched count. Recursive `cancelOwner` calls
also made the outer result depend on which callback settled first.

## Change

Stage all currently queued requests with the matching owner and detach
them from the live `LinkedHashMap` iterator before invoking *any*
completion. Increment the cancellation metric by the detached count
upfront, and invoke each `CANCELLED` completion exactly once in FIFO
order. Reentrant reset/shutdown applies only to still-queued requests;
the detached batch retains its original terminal classification and
returns its original count. A callback may submit new same-owner work,
which remains queued for a later explicit flush rather than being
retroactively caught in an in-progress cancellation.

This does not change `cancel(id)`, payload immutability, priority,
packet flush budgets, lifecycle fences or actual transport integration
(the core still only produces transport-independent packet intentions).

## Verification and limits

Java8 regression cases exercise a first completion invoking shutdown,
reset or nested same-owner cancellation; new same-owner submissions,
thrown callbacks, FIFO receipts and exact metrics. Additional 6,000
deterministic packet-owner replay cycles cover those transitions and
terminal outcomes. Forge source-wiring pins detachment-before-callback
and forbids the old callback-interleaved `cancel(id)` loop.

Gate with exact PR-head Core Java17/21 and Forge Linux/Windows JDK8
builds, JAR verification and simulated install/rollback; then check
identical tested/merged trees and exact-main postmerge workflows.
Real Minecraft 1.8.9 packet wiring/gameplay acceptance is not
established by headless coordinator tests.
