# GhostC R2.65 — keyboard reset of a single typed setting

## Problem

ClickGUI supports Tab/Shift+Tab setting focus, Space/Enter Bool/Choice edits, and Left/Right Number adjustments. After tuning a value, the user previously had no direct way to restore that setting's factory default without manually approximating a slider position, cycling a choice, or restoring a profile.

## Behavior

- With the F7 ClickGUI open, use Up/Down to select a module, Tab to focus an individual setting, then **R** to reset **that setting only**.
- Bool resets to its original constructor boolean, Choice to its original option, and Number to its original exact Double. This is not a module reset; sibling settings, module enable status, keybind and unrelated HUD positions are untouched.
- Defaults are retained as immutable original typed values inside the base Setting class, not as last-loaded profile or last-rendered values.
- The UI invokes `host.uiActions().reset(selected,settingId)`. The owner-thread and stable-id validation paths are preserved. It returns whether the setting changed and persists only actual mutations; resetting an already-default value does not rewrite the profile.
- Ignore repeated R keydown events in the focused settings handler. While editing search, R remains ordinary input. In HUD editor, R retains its prior widget reset behavior. During keybind capture, R remains a candidate key to bind, not a setting reset.
- UI hint for a focused setting includes **R: RESET**; no new profile fields and no effect on packets, target selection or combat.

## Verification

- `SettingDefaultResetTests`: all three types, exact constructor defaults, per-setting isolation, missing IDs, off-owner calls, no-op persistence, 40,000 seeded resets, and actual HostRuntime persisted profile reload.
- Forge wiring asserts R is dispatched only to a focused setting after modal keybind capture, HUD and search ownership; other settings controls unchanged.
- Exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 build/reobfuscation, JAR provenance, fake-profile install/rollback, tested-to-merged tree equality, exact main push workflows.

CI cannot verify actual Minecraft 1.8.9 GUI rendering, physical keyboard latency, FPS, mod coexistence or prolonged stability.
