# GhostC R2.45 — packet completion lifecycle callback isolation

## Source-audited G3 defect

The transport-independent `PacketCoordinator` guarantees a terminal result for every accepted request. In R2.44, `reset()` iterated pending packets and called their untrusted completion callbacks **while still accepting new submissions**. A completion that re-entered `submit()` could enqueue work after its turn in the iteration and leave an old-generation packet behind after a world transition.

Likewise, `shutdown()` set `stopped=true` **after** notifying pending completions. A shutdown callback could `submit()` an additional accepted packet, leaving it queued after the coordinator was closed with no possible flush and no completion. This is an actual service ownership/lifecycle correctness defect; it does not require an enabled Forge packet transport to reproduce.

## Fix

- `reset()` now fences all owner-thread coordinator calls during the reset callback window using a `resetting` state, snapshots/detaches the complete old queue **before** notifications, emits one `STALE_SESSION` completion for every old accepted packet, then releases the guard via `finally`. New-generation requests are admitted after `reset()` returns.
- `shutdown()` marks the coordinator **stopped before** any callbacks, detaches every pending request, and delivers one `SHUTDOWN` completion to each. Re-entrant submissions, nested reset, and new sends are rejected rather than accepted into an irrecoverable queue.
- Callback exceptions remain isolated by the existing callback wrapper. No user-provided exception message is written to logs or shown in the UI.
- Regular `submit`, `cancel`, `flush`, payload cloning, capacity, request token semantics, owner-thread checks, delivery result types, and session generation filtering are otherwise unchanged.

## Deterministic verification

`PacketLifecycleReentryTests` covers reset callbacks trying nested `submit` and `reset`, shutdown callbacks trying both operations, idempotent shutdown, exception-throwing callbacks followed by successful later completions, and `SessionServices.world(false)` cancellation callbacks trying to enqueue requests into the new generation. In 4,000 independent 3-packet reset lifecycles it checks no retained work and exactly one terminal completion per accepted packet. The test also verifies that normal new-generation submission and successful flush still work after reset.

Forge source wiring asserts reset and shutdown fences and the new test's presence in Java17/21 Core CI. The Linux and Windows Java8 Forge builds, provenance, JAR integrity, and Windows fake-profile install/collect/rollback remain unchanged and are required to pass on the exact pull-request head.

## Limitations

This is **pure G3 intent service behavior**; the packet coordinator remains transport independent and does not send Minecraft/Netty traffic. It does not establish live Minecraft start, packet integration, rendering fidelity, measured FPS, or 60-minute stability. Keep G1/G2/G4/G5 gameplay/visual/runtime acceptance open. Do not state exact post-merge CI has passed unless GitHub Actions actually provides the corresponding runs.
