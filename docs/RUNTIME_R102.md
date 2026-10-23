# GhostC R2.82 — session transition callback ownership

## Source-level issue

`SessionServices.world` and `disconnect` previously incremented the
generation and reset coordinators without guarding against callbacks.
`PacketCoordinator.reset` invokes pending packet completions synchronously.
A completion could recursively call `world`, `disconnect`, `tickAdvance`,
`publish` or `close` during the incomplete reset, resulting in an extra
generation change, stale snapshot publication or nested lifecycle failure.

The old reset order also cleared action, rotation and inventory work *before*
notifying packet completions. Those callbacks could submit fresh intents
using the newly advanced generation, and those requests survived after the
old-world invalidation returned.

## Fix

- A single owner-thread transition fence covers the full world/disconnect
  reset window, including same-state `world` calls. Nested generation changes,
  ticks, snapshot publication and terminal close are rejected during this
  window. Generation overflow is rejected before mutation.
- The snapshot is invalidated before callbacks. Pending packets are reset
  first; action, rotation and inventory coordinators are cleared after all
  completion callbacks return. This also removes intents created through
  previously held direct coordinator references in those callbacks.
- Closing seals the facade and clears the visible world state before
  `SHUTDOWN` completions, so callback re-entry cannot revive a half-closed
  service. Repeated `close()` remains idempotent.
- Normal idempotent `world(true)`, repeated disconnect semantics and fresh
  ticks after a finished transition remain unchanged.

## Verification and limitations

Java8 `PacketLifecycleReentryTests.sessionTransitionCallbacks` replays
4,500 world-leave/disconnect transitions. Each queued completion attempts
six nested lifecycle operations, submits fresh coordinator work, and verifies
one exact generation increment, unchanged tick, no leaked intents and
successful later reconnect/tick. Additional cases verify close-time packet
notifications cannot reopen the service and fire only once.

Source-wiring checks enforce the transition guard, snapshot-before-callback,
packet-before-other-coordinator reset order, and closed-before-shutdown order.
Exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 compilation,
reobfuscation, JAR verification and fake-profile install/rollback are required.
Match tested/merged source trees and rerun post-merge CI before certification.

This validates headless lifecycle logic, not native Minecraft 1.8.9
reconnection, event timing, visual state, FPS or long-session stability.
