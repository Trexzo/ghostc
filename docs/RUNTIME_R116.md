# GhostC R2.96 — feature callback reentry safety

## Problem
ModuleRegistry protects event dispatch and onEnable/onDisable lifecycle
callbacks from recursive re-entry. FeatureFaultGuard.execute separately owns
Forge feature work (HUD widgets, movement, ESP) but had no per-feature
recursion fence. A callback accidentally invoking itself via host.runFeature,
directly or through another peer callback, could grow the Java stack
without bound. Java StackOverflowError is intentionally not caught by the
feature quarantine, so this could terminate the client render path.

## Change
Add an owner-thread HashSet of currently executing feature IDs. Refuse
recursive entry for the *same* module by throwing IllegalStateException
before invoking the nested callback. The outer feature's existing
RuntimeException fault boundary quarantines it, records only the
exception class, and reports through the established listener. Nesting
distinct enabled modules remains permitted. A finally block releases
the active marker even on a serious JVM Error or reporting failure.

No event dispatch, module keybinds, renderer settings, GUI layout, feature
defaults, persistence format, or in-game combat policy changes.

## Regression
FeatureFaultTests.reentrantCallbackFence exercises direct A-A recursion,
indirect A-B-A cycles, a legitimate A-B nested callback, intercepting
an illegal nested attempt without executing it, and guard cleanup after
a serious Error. It repeats 12,000 fault/quarantine/re-enable/retry cycles,
asserting one module fault per attempt and no persistent reentry latch.
Python source-wiring regressions pin guard acquisition/release and the
existing fault-quarantine path.

Certification requires exact-head Core CI Java17/21 and Forge CI
Linux/Windows JDK8 success, merge tree parity and green post-merge
workflows. Native Minecraft launch and visual/FPS acceptance are not
covered by these tests.
