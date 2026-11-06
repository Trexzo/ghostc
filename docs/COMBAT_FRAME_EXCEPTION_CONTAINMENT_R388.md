# GhostC R3.88 — shared combat-frame RuntimeException containment

Parent: exact, CI-passed R3.87 draft PR #225 head `d57437ed915d0d64fd9e574b6849e4b6e273b6fe`. Scoped to exception containment in the native Forge END-tick combat branch and a separate owner-thread, non-identifying readout. No merge, installation, release, visual changes or default-setting changes.

## Confirmed source gap

`HostRuntime.runFeature` / `FeatureFaultGuard` already isolate **ordinary RuntimeException failures in individual combat module callbacks** and disable only that module. R3.87 additionally guaranteed the native-pulse status was published in `finally` after dispatch. However, exceptions escaping the shared `ForgeCombatController.tick()`, projectile/manual suspension or controller reset were still propagated through `ForgeEventAdapter.onClientTick`, aborting all later unrelated same-tick controllers and potentially repeating an incomplete shared combat frame.

## Narrow patch

- `CombatFrameFaultFence` is a pure owner-thread, weak-native-identity, fail-closed state machine for faults **outside** the existing per-module feature guard. After a shared fault, subsequent synthetic combat is denied until both a newer world-session generation and a *different* exact native world or player object have been observed. World absence, focus loss, Panic, a module toggle and same-world re-entry do not clear the fence.
- Wrap existing Forge combat/suspension/reset dispatch in a `catch(RuntimeException)`, quarantine only that shared controller and attempt a safe state/intent reset. A cleanup RuntimeException cannot rearm the quarantined frame. Serious JVM `Error` types remain unhandled. The earlier `finally` still publishes ATTACK/USE native-pulse settlement separately, even after a caught controller exception.
- The frame-fault latch is read-only from `HostRuntime.combatDispatchStatus`. The HUD displays a generic persistent warning without exception messages, target identities, world identifiers or packet data. It remains below native ATTACK/USE uncertainty in warning priority and above ordinary module faults. Diagnostics opt-in counts transition edges (`COMBAT_DISPATCH_FAULT`, `COMBAT_DISPATCH_RECOVERED`) rather than frames.
- `combatCalled` is gated by shared-frame admission; native-write acceptance diagnostics distinguish a denied/aborted combat frame from a completed one. Other Forge controllers (movement, WTap/Jump Reset, inventory) continue after a **caught shared combat RuntimeException**, subject to their existing independent plan admission.
- No new synthetic input, camera writers, packets, scheduler values or attack timing changes. The existing `NativePulseSettlement` two-lane quarantine and separate R3.86 module lifecycle revisions are untouched.

## Tests / acceptance

- `CombatFrameFaultFenceTests`: real `HostRuntime` lifecycle/diagnostics/Panic, original `NativePulseSettlement`, per-feature fault isolation, owner-thread rejection, same-world versus new-world recovery, zero opt-out telemetry/backfill and 30,000 seeded fault/recovery frames.
- Mandatory Forge 1.8.9 / Java 8 `nativeCombatFrameFaultProbe`: real Forge END-tick API reflection and original Minecraft `KeyBinding.onTick`, confirming independent shared and native-pulse recovery without launching Minecraft.
- Static Forge wiring contract: checks the actual catch/reset/finally, combat admission, warning priority and CI test registration. R3.87's earlier final-publication contract is updated to accommodate the new catch without relaxing its native status requirement.

**Limits:** These are offline policy and linkage checks. Actual Minecraft 1.8.9 gameplay, OpenGL warning visibility, physical key timing, networking, and server hit acknowledgements remain unverified. This patch does not claim to contain exceptions elsewhere in the Forge event loop or serious JVM Errors. Do not merge to main until full original-game acceptance is performed.
