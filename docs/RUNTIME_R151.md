# GhostC R3.31 — atomic ingress admission across world epochs

A worker calling `HostRuntime.enqueue()` previously reserved an event
sequence independently from the owner thread's world-transition fence.
A producer could obtain a sequence **after** the fence was sampled but
**before** the world-enter, world-leave or disconnect callbacks completed.
That event would then look new enough to dispatch in the next session.

R3.31 makes reservation and publication one atomic admission operation
relative to the epoch boundary:

- `enqueue()` stamps and publishes while holding a short producer/epoch
  monitor. It refuses event submissions during a world transition.
- The owner captures the expired-through sequence and begins the
  producer embargo under the same monitor, then **releases that monitor
  before executing any lifecycle or module callbacks**.
- A disconnect holds one logical embargo across **both** WORLD_CHANGE
  (when applicable) and SESSION_DISCONNECT callbacks.
- The embargo ends in `finally`, including unexpected lifecycle failure.
  New-world events are admitted normally after the transition finishes.
- Existing bounded stale-event dropping, sequence overflow protection,
  owner-only lifecycle event submission, and terminal close sealing remain.

The Java 8 regression `IngressEpochAdmissionTests` runs 96 host sessions.
Each exercises six concurrent producer attempts inside each of four
lifecycle callbacks, three old-world queued events, reconnect admission,
bounded event dispatch and terminal close. A producer must never deadlock
behind a callback or be admitted while the epoch is incomplete.
The old queued events must not be dispatched after reconnect.

Offline acceptance: Core Java 17/21, Forge Linux/Windows JDK8,
Windows package Verify/Prepare/Rollback/tamper, and matching PR/merge
trees. **Native Minecraft 1.8.9 UI, rendering, gameplay input and FPS
remain unverified without a separate in-game acceptance run.**
