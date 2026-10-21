# GhostC R2.55 — keyboard-first filtered ClickGUI module navigation

## User-facing gap

The Forge ClickGUI could search, filter, scroll and toggle modules with the mouse, but lacked module-list keyboard navigation. Users could not move to a filtered result with arrows or toggle it using Enter. The retained selected module could also be out of view after programmatic filter reconciliation. This complicates normal module setup and accessibility, especially at high UI scale.

## Changes

- Added pure `GuiSelection.step` to select the immediate preceding/following item **within** the current visible filtered module list. No wrap-around; empty lists yield null; a stale selected module resolves to the nearest end of the current filtered list.
- Added pure `GuiSelection.reveal` to adjust module-list scroll just enough to show the selected row, clamped to the valid viewport. Visible selections preserve existing scroll position. Regular mouse wheel does not invoke reveal and remains independent.
- `GhostClickGui.keyTyped` now accepts Up/Down navigation and Return/Numpad Return to toggle the selected module. The existing keybind capture branch and HUD editor arrow nudges take precedence. Search text entry continues to work; Up/Down do not insert text, so filtered search results are keyboard navigable. Empty search/category results never toggle any hidden module.
- Added a short sidebar Up/Down hint. No new persisted settings or changed keybindings. Runtime action toggles still go through `host.uiActions()`, including permission/fault/persistence behaviors.

## Regression/verification

- Added Java8 `GuiKeyboardNavigationTests` with bidirectional 35-module list navigation, 5-row viewport reveal, boundary/no-wrap behavior, preserved mouse-wheel scroll, hidden-to-filter selection, empty lists and invalid inputs.
- Seeded 40,000 filter, category, direction and viewport transitions verify that the active item remains a visible, filtered row.
- Added Core workflow invocation, required test inventory and Forge wiring assertions for keyboard ordering and keybind/HUD precedence.
- Gate on exact PR-head Java17/21 Core CI and Linux/Windows JDK8 Forge reobfuscation, artifact provenance and fake-profile install/collect/rollback. Then verify tree identity and post-merge CI.

## Runtime boundary

This improves the real Forge ClickGUI input path in source, but no actual game launch, GUI visual/keyboard interaction, FPS measurement or 60-minute stability is claimed until independent native acceptance.
