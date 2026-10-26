# GhostC R4 parallel track — reversible Panic / Disable All

**Status: Draft feature, offline CI gated; original Minecraft runtime validation not yet performed.**

## Coordination and ownership (26 October 2026)

This PR branches from exact certified 3.0.31 main `0b5e7a0bb7ea3553ee374596c0b50b3dd6812d2e`.

It intentionally does **not** edit the parallel combat PR #163 (`feature/ghostc-r3.32-combat-depth`) or visual Module List PR #164 (`feature/r4-modulelist-hud-20261026`). This PR avoids all their changed file paths, including `HostRuntime.java`, `ForgeCombatController.java`, `ForgeFeatureDriver.java`, `tools/test_forge_wiring.py` and both GitHub Actions workflow YAMLs. Integration/merge must be done later under a fresh tree audit; do not merge this Draft merely because Core CI is green.

## Functional behavior

- **F6** (LWJGL key 64) becomes the dedicated, reserved Panic shortcut, distinct from F7 ClickGUI (65) and the existing bindable F8 status module (66).
- A focused Minecraft game world, no active GUI, matching native world/player identity, actual F6 key-down, and no LWJGL repeat are required.
- `PanicController.disableAll(host)` atomically prevents *new module enables* while it disables every currently enabled module and clears all queued action, rotation, inventory and packet-intent state. Packet completion callbacks and individual module `onDisable` callbacks are not allowed to re-enable peers during the embargo.
- The operation clears the owner's pressed-input edges and asks Forge feature/combat adapters to reset their private timers. Ordinary Minecraft physical inputs and game state are not modified. It does not disconnect the player.
- Module `onDisable` failures are quarantined by `ModuleRegistry`'s existing exception handling; fault metadata is not wiped.
- Active module states are saved using the existing safe profile persistence path. Numerical settings, UI configuration, the profile itself and other local files remain untouched. Explicit module re-enabling and normal F8 behavior continue working.
- It is **not** an injector unload, process concealment, screenshare bypass, self-destruct, cleanup of audit traces or file deletion.

## Verification

- No workflow changes. Existing `CoreTests` invokes new `PanicTests` through the original Java17+Java21 `--release 8` CI.
- A real HostRuntime fixture verifies all-module disabling, pending attack/use-state cancellation, native camera fallback, packet terminal completion, malicious packet completion attempting to re-enable a module, malicious `onDisable` attempting to re-enable and recursively panic, idempotence and no world disconnect.
- Separate profile-reload fixture verifies numeric settings remain and enabled modules stay off after restart, while explicit module re-enable and F8 still work.
- Existing 150k-key input oracle and 10k random keybind suite updated for the new reserved F6. F7/F8 contracts remain.
- Forge 1.8.9 compilation on Windows/Linux, downloaded artifact verification, and runtime acceptance all remain hard gates.

## In-game acceptance needed

In a locally controlled Minecraft 1.8.9 world, enable Combat/HUD features, generate an action, press F6 once and verify no further automated actions, HUD painting or input pulses. Confirm holding F6 has no repeats; F6 inside chat/GUI does nothing; F7 and F8 still work; rejoin does not resume any disabled module; explicit re-enable works. Save and reload the profile, compare file integrity and settings, and repeat during module faults/low FPS. Record actual input/camera traces and Windows FPS alongside video. Compilation and offline tests are **not** original-Minecraft gameplay evidence.
