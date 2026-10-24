# GhostC R2.99 — live feature-fault warning recovery

## Issue

FeatureFaultGuard deliberately quarantines a crashing HUD, ESP or movement
feature and retains only its stable ID and exception class. In the existing
HostRuntime, its on-fault listener copied a warning into a separate string,
but the string was never cleared when a user reenabled the module and a later
render callback succeeded. On-screen "Module disabled" could therefore be
shown indefinitely after recovery, mask newer UI/profile warnings and give
an inaccurate account of live feature health.

## Change

The owner-thread FeatureFaultGuard is the single warning authority. It keeps
one cached warning corresponding to its newest unresolved fault entry.
On quarantine, refresh from the existing bounded 32-entry recent-fault map.
On successful callback retry, remove that module's fault entry and refresh
the warning from any older unresolved failure; when none remain return empty.
A healthy callback of a different feature does not clear an unrelated fault.

HostRuntime.faultWarning delegates to this cached value instead of storing
a permanently latched string. Fault count increment and persistence still
belong to the existing on-fault listener. Rendering only reads the cached
String (no new map allocation on every overlay/ClickGUI frame). Raw exception
messages, stack traces, player IDs and server addresses remain unretained.

## Verification

FeatureFaultTests.liveWarningRecovery verifies two concurrent failures,
newest-first warning, 40,000 cached same-object reads, successful independent
recovery with fallback to another unresolved warning, full warning removal
after the final successful retry, matching history and stable fault counters.
Existing quarantine, private-fault-content and 12,000 recursion-boundary
regressions remain enabled. Forge source wiring retains HUD/footer warning
priority and requires the guard's live getter.

Certification: Core Java17/21 and Forge Linux/Windows JDK8 exact-head CI,
tested/merged tree equality, and green post-merge workflows. No native game
launch or visual acceptance claim.
