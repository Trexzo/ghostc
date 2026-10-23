# GhostC R2.85 — host event lifecycle ownership

## Source-level failure
The core ModuleRegistry protects recursive `dispatch`, and SessionServices
protects callbacks within `world/disconnect` reset. However HostRuntime
could still let a module callback invoke `worldPresence` or `disconnect`
during owner-event delivery (including externally enqueued events drained
inside a tick). The host would change the world/session state *before*
the nested registry dispatch threw, and could then continue processing
remaining old-session queued events with a previously captured fence.

## Fix
An owner-thread-only `hostDispatching` fence spans the entire `tick`
(including all bounded ingress events), `render2d`, `worldPresence` and
`disconnect` callback windows. Lifecycle-changing methods reject
reentrancy before mutating generation, world flags, queues, counters
or triggering additional module events. The guard is reset in
`finally` even after exceptions. Same-state world signals and repeated
disconnects are still idempotent from the normal external caller.

`close` is also refused from an active event callback, and a separate
`closing` fence prevents shutdown-time module callbacks from recursively
re-entering host closure. Normal owner-thread closure still works.

## Regression
`HostRuntimeTests.testHostEventReentryFence` sends 2,500 current-session
queued producer events through client ticks. Every module event callback
attempts nested world leave/join, duplicate world callback, disconnect,
tick, render and close, asserting refusal and no epoch/tick mutation.
It verifies exact direct/async event counts, disconnect and reconnect,
one network-disconnect event, and normal later closure. A throwing module
also exercises cleanup/release of the host guard. Forge wiring pins the
prohibition across ingress draining and lifecycle methods.

Release requires exact PR-head Core Java17/21 and Forge Linux/Windows JDK8
compilation, reobfuscation, JAR verification, simulated Windows
install/rollback, tested/merged source-tree equality and successful
postmerge workflows. Native Minecraft 1.8.9 live event order, FPS
and long-session stability remain separate.
