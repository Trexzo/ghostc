# GhostC R2.90 — authorize lifecycle events only at the owner host boundary

## Source-level problem

HostRuntime.enqueue(GameEvent.Kind) is intended as a bounded, thread-safe
way for worker/network producers to submit ordinary immutable events.
It accepted every GameEvent.Kind, including WORLD_CHANGE and
SESSION_DISCONNECT. When dequeued, those two kinds were delivered directly
to registered modules, **without executing** worldPresence() or disconnect(),
rotating SessionServices generation, invalidating the local world, clearing
session-bound intentions or expiring old producer events. This could
deliver counterfeit lifecycle callbacks with a still-live old session.

## Change

Only CLIENT_TICK and RENDER_2D are accepted by asynchronous ingress;
other kinds (including null, WORLD_CHANGE, SESSION_DISCONNECT) throw
IllegalArgumentException before obtaining a sequence, reserving ingress
capacity or dispatching callbacks. Legitimate owner-thread
worldPresence()/disconnect() retain their existing lifecycle dispatch
and atomic session-generation/queue fences. Ordinary worker producers,
bounded ingestion, off-thread safety and closed-host rejection are unchanged.
BoundedIngress remains a generic core event queue; the restriction belongs
at HostRuntime's public asynchronous producer API boundary.

## Verification

HostRuntimeTests.testProducerLifecycleAuthority exercises 8,000 worker
cycles that attempt both forbidden lifecycle kinds (16,000 denials),
200 accepted ordinary tick/render events, and 2,000 owner cycles
attempting the same forbidden kinds (4,000 denials). It verifies
capacity/rejection/sequence invariants, exact draining across two bounded
owner ticks, no spurious WORLD_CHANGE or SESSION_DISCONNECT, exact
join/leave/disconnect session generation increments, and later reconnect
and closed-host behavior. Forge source-wiring tests pin the admission
whitelist before allocating sequences or submitting to ingress.

Release requires exact PR-head Core Java17/21 and Forge Linux/Windows
JDK8 compilation/JAR verification and fake Windows installation/rollback,
tested/merged source-tree equality, and both postmerge workflows green.
Live Minecraft 1.8.9 event timing, GUI, FPS, and extended sessions still
need native testing.
