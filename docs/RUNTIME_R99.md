# GhostC R2.79 — stable event dispatch through mutable module lifecycle

## Risk
The core `ModuleRegistry.dispatch` iterated a mutable
`LinkedHashMap.values()` while invoking arbitrary module `onEvent` code.
Callbacks are allowed to register or disable modules on the owner thread.
New registration during the loop could invalidate the Java iterator with a
`ConcurrentModificationException`, escape the per-module fault isolation
boundary, and end the client's event processing for that tick.
Reentrant `dispatch` from an event handler could recursively consume
the stack rather than faulting the offending module.

## Change
Maintain an immutable-for-each-event ordered `Entry[]` snapshot that
is rebuilt on registration only. Each tick captures its current array
without allocating an `ArrayList`. A module registered from a callback
is first eligible for the NEXT event, not mid-dispatch. Per-entry state
checks continue to exclude peers disabled earlier in the same event.

A recursion guard rejects nested dispatch as `IllegalStateException`,
which the existing outer fault boundary contains. The guard is always
cleared in `finally`. If a callback shuts down the registry, no
remaining snapshot listeners are invoked. All original module registration,
enable/disable, keybind and shutdown ownership rules remain unchanged.

## Verification
- Java8 `ModuleLifecycleReentryTests`: late registration, stable ordering,
  next-event delivery, 20,000-event steady-state replay, disabling a peer,
  shutdown from an event callback, recursive dispatch containment, peer
  continuity and later dispatch after fault.
- Source-wiring test pins registration-only cached array creation,
  recursion, state, shutdown and cleanup guards.
- Exact PR-head Java17/21 Core, Forge Linux/Windows JDK8 compile and
  JAR checks, simulated install/rollback, identical tested and merged trees
  and successful postmerge workflows required for certification.

This verifies headless lifecycle correctness, NOT live Minecraft event
rendering or frame-time/FPS under actual gameplay.
