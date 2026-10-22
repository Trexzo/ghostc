# GhostC R2.73 — idempotent keybind persistence

## Behavior

Assigning the currently-bound key through mouse or keyboard capture used to
save the profile, even though the resulting binding did not change. Repeated
Backspace/Delete unbinds had the same unnecessary write behavior.

The existing `UiActions.bind` mutation gateway now snapshots the selected
module's previous key, always calls `ModuleRegistry.bind` for canonical
validation and owner-thread/lifecycle checks, then persists only when the
key differs. No changes to duplicate-key rejection, reserved F7, capture
focus, module toggle events, profile serialization, or action ownership.

## Verification

- Java8 `GuiKeybindCaptureTests.unchangedBindings` covers same-key and
  repeated-zero binds, changed assignments, reserved/duplicate/invalid keys,
  unknown modules, off-owner calls and calls after shutdown.
- 60,000 deterministic randomized valid key assignments compare key state
  and persistence call counts to an independent change/no-change oracle.
- Forge source-wiring assertions enforce validation before conditional
  persistence, and existing key capture/focus tests remain active.
- Release gate: exact PR-head Core Java17/21, Forge Linux/Windows JDK8
  reobfuscation/JAR validation and fake-profile install/rollback, tested/merged
  tree SHA equivalence, then exact-main post-merge CI.

In-game Minecraft 1.8.9 keyboard/mouse interactions and long-session
performance are not established by these headless tests.
