# GhostC R2.61 — ClickGUI search focus owns keyboard input

## Source-audited usability bug

The R2.55 module Up/Down and Enter shortcuts, extended in R2.59 with Tab/arrow-key setting edits, previously appeared **before** the search field's text-edit handler in `GhostClickGui.keyTyped`. Pressing Enter while composing a search could toggle the selected module. Up/Down could move the selected module even though the search field had the keyboard focus.

## Behavior after fix

- On the F7 module page, Ctrl+F focuses search and selects the current query so it can be replaced immediately.
- With search focused, typing and text cursor navigation go to the existing Minecraft `GuiTextField`; no module selection, toggles, setting changes or keybind captures occur.
- Enter/Numpad Enter releases search focus without toggling a module. Tab (or Shift+Tab) explicitly transfers to typed setting navigation, preserving R2.59 behavior.
- Escape/F7 still closes the GUI; explicit keybind capture still takes priority; HUD-editor arrow nudges retain existing behavior.
- Search-filter reconciliation and scrolling run only after search text actually changes, not after arrow-only cursor motion, preserving the user's viewport position.
- The new `GuiSearchFocus` is a small pure Java8 keyboard ownership policy with no new settings, profile migration, packet or gameplay writes.

## Verification

- `GuiSearchFocusTests` cover the complete three-flag routing matrix and 75,000 seeded focus, Enter, Tab and module-control transition cases.
- Source-wiring checks require the focused-search handler to execute before module selection/toggle paths, but after explicit keybind capture and top-level close actions.
- Exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 compile/reobfuscate/JAR and fake-profile install/rollback; tested-tree equality and post-merge exact commit Core/Forge results separately.

Native Minecraft 1.8.9 interaction, FPS, shader compatibility, visual quality and extended stability remain unverified by CI.
