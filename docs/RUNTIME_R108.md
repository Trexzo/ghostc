# GhostC R2.88 — transport error terminality and unique packet tokens

## Source-level failure
`PacketCoordinator.flush` removed an accepted packet from the queue
before invoking the caller-supplied `Transport.deliver`. Its catch clause
handled only `Exception`. If external transport code threw an `Error`
(e.g. AssertionError/LinkageError), the removed request received **no
terminal completion**, and its transport-failed metric was not incremented.
The flushing guard did reset, but the accepted packet could never be
recovered or explicitly canceled because it was already detached.

The packet sequence counter could additionally overflow to a negative
or sentinel identifier after `Long.MAX_VALUE` requests, violating the
positive unique-token contract used for precise cancellation.

## Fix
Catch `Throwable` **at the transport call boundary only**, account one
`TRANSPORT_FAILED` receipt, and rethrow the original `Error` (with its
identity and stack) after settlement. An ordinary `Exception` remains a
contained transport failure as before. A successful delivery is counted
only after the transport call returns. The existing `finally` releases
the flush recursion guard; all other queued packets remain pending,
subject to legitimate reset/shutdown invoked by the completion.

Refuse a new packet submission once its token sequence reaches
`Long.MAX_VALUE`. Do not insert a wrapped ID, invoke a completion for an
unaccepted request, or reuse exhausted identities. Existing capacity
rejection still returns -1 and invokes REJECTED as before.

## Regression
`PacketLifecycleReentryTests.fatalTransportTerminality` executes 4,000
deterministic cycles with a transport that throws AssertionError. Failure
completions trigger one of ordinary recovery, reset, shutdown or new
same-session submission, verifying original error propagation, exact
terminal receipts, callback-triggered outcomes, metrics, remaining queue
ownership and successful later flush. Explicit Long.MAX_VALUE token
boundary tests ensure positive last token, overflow refusal without
queue mutation, individual cancellation and no ID reuse. Source-wiring
checks lock the failure-before-rethrow ordering.

As always, exact PR-head Core Java17/21 and Forge Linux/Windows JDK8
compile, reobfuscation/JAR verification and simulated Windows
install/rollback; tested/merged tree equality and successful postmerge
workflows are required for certification. No actual Minecraft 1.8.9
networking, gameplay, rendering, FPS or long-session behavior is
certified by these headless checks.
