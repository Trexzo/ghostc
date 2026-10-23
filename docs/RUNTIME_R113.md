# GhostC R2.93 — signed-overflow-safe nanoTime elapsed arithmetic

## Source-level issue
System.nanoTime() has an arbitrary signed origin. When a long-lived monotonic
measurement jumps forward from near Long.MIN_VALUE to near Long.MAX_VALUE,
subtracting a prior sample may overflow signed long even though the elapsed
interval is nonnegative.

FixedClickScheduler tested signed (nowNanos - armedAtNanos < period), so such
a forward interval could suppress an overdue synthetic click indefinitely.
CpsMeter tested signed (now - firstClick >= 1 second); an overflowed negative
value could keep stale click counts far beyond the one-second window.

## Change
Use Java 8 Long.compareUnsigned to compare elapsed nanoseconds in both hot
paths. The existing signed-monotonic timestamp validation remains intact.
No click rate, first-press, focus, no-catch-up, click-window or owner-thread
semantics change. The scheduler still emits at most one synthetic pulse for
a late frame and the CPS meter still removes stale edges from its deques.

## Deterministic regression
CombatTests.schedulerWideElapsed runs 30,000 randomized cases at 4, 10 and
25 CPS near Long.MIN_VALUE, checking initial arming, exact interval threshold,
no same-timestamp duplicate, overdue pulse after a huge forward jump,
no catch-up burst, release and rearm.

FeatureTests.cpsWideElapsed runs 18,000 randomized tests of left/right click
expiry beginning near Long.MIN_VALUE, checking exact one-second boundaries
and expiration after a jump to Long.MAX_VALUE. Additional tests check a
recent click near MAX and initial MIN timestamp behavior. Python Forge source
wiring guards both overflow-safe comparisons and the regression registrations.

Merge gate: Core Java 17/21 and Forge Linux/Windows JDK8 on exact PR head,
tested/merged Git-tree parity, and postmerge Core/Forge success. These are
source-level tests, not Minecraft 1.8.9 in-game, FPS or soak verification.
