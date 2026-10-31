# GhostC R3.71 — Block Hit native commit and denied-action recovery

**Parent:** CI-green R3.70 draft PR #208 at `5be90773d655d9525b54d6217046d453009a1d89`. GitHub-only combat draft; no main, visual, installation or release modifications.

## Verified source-level defect

`BlockHitPolicy.due(...)` advanced its last-pulse timestamp before Forge checked the winning native USE action and the R3.70 player/world/focus/keybinding/sword gate. In post-attack or on-damage modes, a due event cleared `pendingSince` before native dispatch too. On a rejected final native USE, the interval moved and the event was lost although no pulse was emitted. Hit Select already had two-phase `preview/committed/cancelPrepared` and TriggerBot did not commit cooldown until KeyBinding; Block Hit lacked that parity.

## Changes

- Add `BlockHitPolicy.preview`, `committed`, and `cancelPrepared` for Forge's native path. `preview` uses the same pure `due` timing and temporarily stages cadence/event consumption. A real successful native `KeyBinding.onTick` commits; arbitration denial, binding/remap veto or feature failure restores `lastPulse` and `pendingSince`. The current physical hurt-time observation is preserved to avoid manufacturing a new `on_damage` edge.
- Keep original `due(...)` overloads and their legacy atomic behavior for existing callers. Panic/reset invalidates all prepared action state, and cross-thread commit is forbidden.
- On a final AutoClicker-native rejection where Hit Select was otherwise approved, reset its fixed/range schedulers to demand a fresh held-input arming. Hit Select-denied attempts retain their existing paced scheduler behavior (no catch-up or cadence changes).
- Hit Select still consumes cooldown **only after native attack pulse**. TriggerBot still commits cooldown only on successful native pulse and invalidates final-denied target acquisition. Block Hit consumes no cooldown/event without native use. No new key pulse, packet, camera write or settings.

## Tests / limitations

`BlockHitNativeCommitTests`: interval, post-attack, on-damage, canceled/native commit, event expiration, Hit Select/TriggerBot handoff and 90,000 seeded mixed input frames checked against independent interval oracle. Mandatory `NativeBlockHitCommitTests` exercises the actual Minecraft 1.8.9 Java8 KeyBinding pulse after a staged native Block Hit request; Forge source-wiring checks native commit order and cancellation.

Exact-head Core Java17/21 and Forge Java8 Linux/Windows checks, reobfuscation and downloaded-artifact acceptance required. Headless tests do **not** validate actual gameplay timing, server damage, live physical inputs or packet acknowledgement. Keep draft until authorized original Minecraft runtime test.
