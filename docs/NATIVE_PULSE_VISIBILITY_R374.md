# GhostC R3.74 — visible native pulse quarantine

Parent: R3.73 draft PR #211 at `c42afff30b2f6068448e7188f8e5b9ce5cb2cc6e`. Scope: diagnostics and warning presentation only; combat timing, native key dispatch, and two-factor recovery authorization are unchanged.

## Source audit

R3.72/R3.73 deliberately quarantine uncertain native ATTACK and USE events, but the pre-R3.74 HUD exposed only the latest action telemetry and feature faults. No persistent user-facing readout distinguished an uncertain ATTACK, uncertain USE or both. A disabled module could therefore appear inexplicably nonfunctional after partial KeyBinding settlement.

## Change

- `NativePulseSettlement.uncertainMask()` returns an owner-thread-only bit mask (ATTACK=1, USE=2). `reconcileNative` observes world/player identity with the same R3.73 two-factor recovery proof, independently of module enablement and without sending any native input.
- `ForgeEventAdapter` observes that status at END tick after host session lifecycle processing, and again after combat processing to capture an exception on the same frame. Existing warning text area prioritizes this concrete output quarantine over ordinary feature warnings; it does not need Runtime Diagnostics to be enabled.
- `HostRuntime.nativePulseStatus` accepts masks 0–3, rejects foreign threads and records *transitions only*, optionally counting `COMBAT_PULSE_ATTACK_UNCERTAIN`, `COMBAT_PULSE_USE_UNCERTAIN` and `COMBAT_PULSE_RECOVERED`. No backfill, per-frame repeats, identities, exception details, packets, or server outcome claims.
- Warning persists through Panic, module toggles, focus changes, and same-object reconnect. It clears after identity-verified native recovery. No new GUI component, layout adjustment, additional KeyBinding or combat action is introduced.

## Verification

`NativePulseVisibilityTests` includes an actual temporary `HostRuntime`, Panic, opt-in/opt-out counters, warning status for individual/combined lanes, same-object reentry, recovery, invalid state, owner-thread protection and 90,000 deterministic state traces. The mandatory Forge Java8 `nativePulseVisibilityProbe` checks the actual Minecraft 1.8.9 `KeyBinding.onTick` output and World registry linkage. Source-wiring tests enforce both END-tick observation sites and warning location.

Exact-head Core Java17/21 and Forge Linux/Windows Java8 build/reobfuscation plus Windows downloaded-artifact rehearsal are mandatory. Headless tests cannot prove actual gameplay, physical keyboard timing, packets or server hit acknowledgements.
