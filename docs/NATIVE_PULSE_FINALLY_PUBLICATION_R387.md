# GhostC R3.87 — native-pulse status publication on aborted combat frames

Parent: CI-verified R3.86 draft PR #224 at `ed6d36786b57dde5d33f058ad6c459d0c3042ede`. The change is stacked on that exact combat branch; it does not touch main, visuals or installed profiles.

## Source finding

The native ATTACK/USE settlement in `ForgeCombatController` protects uncertain KeyBinding output through `abandon()`, but `ForgeEventAdapter.onClientTick` had its second `host.nativePulseStatus` publication after the full combat/suspension/reset branch. An uncaught exception in that branch could skip the end-of-frame warning update. The earlier read-only publication still runs before combat, but cannot report an uncertainty first created within this frame.

## Narrow fix

- Wrap the **existing** combat tick, manual-hotbar suspension, projectile suspension and ordinary reset in one `try/finally`.
- In `finally`, publish exactly the current `combat.nativePulseUncertainMask()` through `host.nativePulseStatus`, including when processing throws.
- The original exception is **not suppressed**; native admission, session identity proof, timers, input rates, module toggles, packets and world lifecycle logic are unchanged.
- Retain R3.74 pre-combat read-only status reconciliation and all R3.75 warning-render isolation.
- This is a status-visibility improvement, **not** a new quarantine guard or automatic native recovery mechanism.

## Regression coverage

`NativePulseFinallyPublicationTests` uses the actual owner-thread `NativePulseSettlement` and `HostRuntime` to inject ordinary exceptions and an `AssertionError` before native return, after native return and after complete settlement. Checks include ATTACK and USE lane separation, same-world reconnect, Panic, identity-verified recovery, late telemetry failures, no-op dispatch and 30,000 randomized fault frames. The source-wiring contract verifies the **actual** Forge adapter wraps the existing dispatch branch in `try/finally`, rather than just demonstrating the pattern in a test helper.

Run through existing mandatory Core Java 17/21 and Forge Linux/Windows Java 8 CI. Original Minecraft 1.8.9 gameplay, live keyboard timing, OpenGL rendering and server acknowledgements remain unverified; an offline test does not certify them.
