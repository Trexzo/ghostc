# GhostC R2.92 — fail-closed host event sequence exhaustion

## Source-level failure
HostRuntime used AtomicLong.incrementAndGet() to allocate the immutable GameEvent
sequence for worker ingress and owner callbacks. Beyond Long.MAX_VALUE, the
counter wraps into a negative number, which GameEvent rejects. If this happens
inside tick(), worldPresence() or disconnect(), the host may already have
advanced the client tick, invalidated a world generation, or dispatched world
leave before failing to allocate the event. Worse, the ingress fence compares
event sequence to the last world-boundary value: a wrapped identifier cannot
preserve the ordering contract.

## Change
Allocate strictly positive event IDs through a CAS reservation that checks the
remaining numeric range before changing the sequence. Worker contention cannot
steal or duplicate the final IDs; exhausted requests throw IllegalStateException
without queue admission or capacity-rejection metric side effects.

Synchronous tick/render/world transition code reserves its event before owner
mutations. Disconnect reserves two consecutive IDs as one atomic transaction
when it must emit both WORLD_CHANGE and SESSION_DISCONNECT; it reserves only
one when world leave has already occurred. The world identity transition still
owns its original ingress boundary and callback dispatch guard. Ordinary
non-overflow behavior and profile defaults are unchanged.

The counter is intentionally not reset per session: worker events and pending
late publications still require monotonic identities across reconnects.

## Regression
HostRuntimeTests.testEventSequenceOverflow races 48 worker producers with two
positive event IDs remaining; exactly two may enqueue, and all other requests
must fail without wrapping, changing capacity metrics, or dropping accepted
events. With the counter exhausted, tick/render/world-leave/disconnect refuse
without advancing tick, session generation, world presence, or consuming the
queue. A separate boundary trial refuses disconnect with only one ID remaining
and accepts it with exactly two IDs, verifying both world leave and disconnect
generations. Another trial accepts a final single world-change event.

The Forge source-wiring regression protects CAS overflow denial, two-event
reservation and owner-boundary test registration. CI must pass Core Java17/21,
Forge Linux/Windows JDK8 JAR verification and simulated install/rollback on the
exact PR head. Merge/source-tree parity and post-merge workflows are separate
release gates. No native Minecraft 1.8.9 GUI, FPS, gameplay or long-session
acceptance is inferred from these headless tests.
