# GhostC R2.53 — packet flush callback reentry fence

## Source-audited G3 failure mode

`PacketCoordinator.flush(transport,generation,budget)` synchronously calls the supplied Transport and Completion callbacks. Until R2.53, either callback could invoke `flush` recursively on the same coordinator. The nested call would process additional queued packets **outside the outer flush budget**, with no bound on depth if each completion submitted another packet and flushed it. The coordinator is a host-independent contract and is not wired to Minecraft Netty transport.

## Implementation

- Add an owner-thread `flushing` scope around the entire budgeted `flush` iteration. Reject only nested `flush` with `IllegalStateException`. A `finally` clause releases the scope after normal return or failure.
- Do not change `submit`, `cancel`, `reset`, `shutdown`, queued byte ownership, generation checks, terminal completion, or transport exception policy. Callback-submitted packets remain queued for a **subsequent explicit** flush.
- Keep reset and shutdown callbacks able to cancel all other pending packets even when invoked by the completion of an already-detached packet. The captured flush snapshot skips entries cleared by reset; terminal completions remain exactly once.
- No packet injection, network bypass, user-facing settings, rendering or gameplay changes.

## Regression/verification

- Extend Java8 `PacketLifecycleReentryTests` with transport-origin and completion-origin nested flush attempts, callback-submitted work, exact two-entry outer budget, and post-flush legal delivery.
- Confirm reset from a completion remains legal and cancels the remaining detached batch once.
- Repeat 3,000 coordinators with nested callback attempts to check that no recursion or pending-work leak occurs.
- Forge source contract asserts the flag covers the whole flush and is released in finally; the normal Core Java17/21 and Forge Linux/Windows JDK8, JAR provenance and Windows simulated install/collect/rollback gates apply.
- This is **offline core correctness**, not evidence of Minecraft 1.8.9 runtime startup, packet compatibility, FPS, visual fidelity or 60-minute soak.
