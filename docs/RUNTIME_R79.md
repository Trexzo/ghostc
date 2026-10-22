# GhostC R2.59 — keyboard editing of typed ClickGUI settings

## User-visible feature

R2.55 added Up/Down module navigation and Return module toggle, but selecting a setting still required a mouse. With R2.59, the F7 modules page can be operated entirely from the keyboard:

- Up/Down moves between the currently visible search/category-filtered modules (R2.55 behavior). Enter toggles the module while no setting row is focused.
- Tab moves focus to the first setting, then advances through the selected module's typed setting rows. Shift+Tab goes backward. The highlight and settings scroll follow focus without changing unrelated module settings.
- On a focused Boolean or Choice: Space, Enter or Numpad Enter uses the existing `UiActions.bool` / `UiActions.cycle` path (including per-action persistence and validation).
- On a focused Number: Left/Right nudges exactly 5% of the normalized range through `NumericSliderScale` and `UiActions.fraction`. The value is clamped at min/max; repeated boundary presses do not request redundant saves.
- Clicking a module, search box, category or keybind capture resets keyboard-setting focus. Switching to the HUD editor clears focus. HUD arrow nudges, keybind capture and search typing remain unchanged. Existing pointer slider drags still persist once at release.
- Tab exits search-field focus and enters the selected module's settings; no game hotkeys or packet behavior added.

## Implementation and regression

- Pure Java8 `GuiSettingKeyboard` handles cyclic row traversal, viewport reveal, and normalized numeric stepping.
- Real `GhostClickGui` renderer/hit path has per-row focus tint, with no new persistence schema or settings.
- `GuiSettingKeyboardTests`: empty/stale/overlong settings lists, viewport and direction boundaries, typed Bool/Choice/Number mutation through the established action gateway, plus 45,000 seeded settings and numeric-range scenarios (over 300,000 assertions).
- GitHub Core CI Java17/21, Forge Linux/Windows JDK8 compile/reobfuscate, JAR class inventory and fake-profile install/collect/rollback. Verified exact tested PR tree and post-merge workflows are separate gates.

Actual Minecraft GUI interaction, aesthetics, FPS and long-session stability remain outside offline CI.
