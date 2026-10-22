# GhostC R2.74 — focused keyboard help without keybind overlap

## Reproduced source-level defect

R2.70 displayed a static `TAB SHIFT+ARROWS HOME/END R` prompt
at `bindY+6`, overlapping the keybind row drawn at `bindY+5`.
The prompt also described numeric shortcuts when the focused setting
was Boolean or Choice.

## Fix

- Use the existing 18px footer strip below visible settings for help/pagination.
  No longer draw either prompt over keybind state or active key capture.
- Only draw help when the selected module has a focused, visible typed setting.
  Boolean: Enter/Space toggle and R reset. Choice: Left/Right, Home/End,
  Enter and R. Number: Left/Right, Shift+Arrows, Home/End and R.
- Suppress footer hints/pagination when a higher-priority GUI error or
  `host.uiWarning()` is active, preserving the warning's own bottom text.
- Pixel-fit footer strings through the existing `HudTextFit` policy and
  keep the original 18px settings reservation, module input ordering and
  persistence pathways unchanged.

## Verification

Pure Java8 `GuiSettingKeyboardTests.hintPolicy` checks exact type-specific
prompts and invalid focus. Forge source-wiring tests pin bottom-footer
placement, warning ownership, clipping and absence of the obsolete
`bindY+6` overlay. Full Core Java17/21 and Forge Linux/Windows JDK8
regression, JAR and simulated install/rollback gates are mandatory.
Native 1.8.9 visual readability and long-session FPS require in-game acceptance.
