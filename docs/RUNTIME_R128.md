# GhostC R3.08 — terminal ingress admission at host shutdown

## Failure mode

HostRuntime.close() previously set its volatile closed flag only after
configuration persistence, diagnostics export, module onDisable callbacks and
service shutdown. Any callback or worker could call enqueue during that
interval and have new events accepted into BoundedIngress even though the
owner was already shutting down. Those accepted events could not be drained
by further ticks, so acceptance success was misleading and they were
retained for the lifetime of the closed host reference.

## Change

BoundedIngress now supports an idempotent terminal seal. Submissions and
the seal synchronize through a narrow admission monitor that includes
capacity reservation AND publication to the concurrent queue. A producer
in the middle of publication completes before seal returns; submissions
after sealing always fail with the existing rejected count increment.
No module code runs under this monitor. Already accepted events stay
bounded and retain their pre-seal order and can still be drained by their
owner; the existing CAS capacity accounting remains compatible with
consumer polling.

HostRuntime.close seals ingress after passing owner-thread/reentry checks
and before invoking configuration persistence, module onDisable callbacks
or service close. The late closed flag remains in its usual location for
the rest of runtime lifecycle, but cannot permit a worker to sneak an
event into a half-shutdown client.

## Regression

HostRuntimeTests.testIngressShutdownAdmissionFence enables a custom module
whose onDisable attempts a producer enqueue while closed is still false,
and proves the attempt is rejected before the terminal flag is published.
It also races 12 producers through 14,400 event submissions against
seal, checks accepted+rejected conservation, pending count exactness,
post-seal refusal, and full owner-thread drain of pre-seal accepted events.
Existing 256-capacity, stale-session, sequence-overflow and reentry
regressions remain enabled. Forge source-wiring enforces seal ordering.

Certification requires exact-head Core CI Java17/21, Forge Linux/Windows
JDK8, matching tested/merged Git tree, and passing postmerge workflows.
No native Minecraft gameplay or FPS testing is claimed.
