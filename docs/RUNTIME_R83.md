# GhostC R2.63 — safe module fault reset from ClickGUI and keybinds

## Source-audited bug

The core `ModuleRegistry` already contains an intentional safety latch: a module in `FAULTED` cannot be enabled until `setEnabled(id,false)` explicitly clears its fault. However, `UiActions.toggle`, the GUI module click/Enter action, and `ModuleRegistry.toggleKey` treated every non-enabled module as a candidate for immediate enable. Consequently, the GUI rendered `FAULTED` as `OFF` while its toggle repeatedly threw `Reset fault first`; a bound key could do the same.

## New behavior

- Three visible module states in the F7 list: `ON`, `OFF`, and a red `RESET` for `FAULTED` modules. Selecting one shows only its sanitized exception class (the already-capped `lastFault`), never an exception message.
- A single GUI click, keyboard Return on the selected module, or assigned module keypress while faulted **resets to Disabled**, calls no enable/disable callbacks, and clears the old fault marker. This action is persisted through the existing `UiActions` profile-save gateway for GUI toggles.
- A **separate second input** opts in to re-enable the module. This prevents an accidental immediate retry loop and preserves the original core lifecycle fence.
- `ModuleRegistry.setEnabled(id,true)` still rejects `FAULTED`; recursive transitions and late/shutdown mutations remain sealed. Successful ordinary toggles remain otherwise unchanged.
- This affects `ModuleRegistry` faults, not the independent `FeatureFaultGuard` render-callback quarantine, which intentionally disables a failed feature rather than marking its registry entry `FAULTED`.
- No new game packets, attacks, settings schema or profile migration.

## Verification

- Java8 `FaultRecoveryUiTests` covers onEnable, onDisable and onEvent failures, sanitized exception-type UI, persisted GUI actions, keybind press/reset/re-enable sequence, failed repeated enable from core, cross-thread calls, recursive callback boundaries, and repeated fault/recovery cycles.
- Forge source-wiring tests require the visible red `RESET`, existing click/Return path, safe `UiActions` and keybind reset logic, and retained core fault guard.
- Require exact PR-head Core Java17/21, Forge Linux/Windows JDK8 compile/reobfuscate, JAR provenance and simulated installation/rollback, merged-tree SHA parity and exact-main postmerge workflows.

Real Minecraft 1.8.9 visual, GUI input, FPS, mod coexistence and long-session gameplay acceptance are not proven by CI.
