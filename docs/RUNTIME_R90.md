# GhostC R2.70 — precise keyboard adjustments for typed numeric settings

## User experience

F7 ClickGUI numeric setting editing previously offered only a 5%-range step. That makes small tuning difficult, especially for ranges spanning large distances, CPS, color component values, and angles.

With a numeric setting row focused (Tab/Shift+Tab):
- Left/Right continues to adjust by 5% of the configured range.
- Shift+Left/Right adjusts by 1% of the range.
- Home/End selects exact minimum/maximum via the existing normalized slider/value validation path.
- **R** still restores the original setting default; search field input, HUD-editor arrows and R reset, modal key capture, and unfocused module navigation keep their existing priority.
- Numeric bounds are validated in the core Java8 policy. The existing `UiActions.fraction` owner-thread and profile-save gateway remains authoritative, including avoiding needless writes when normalized fraction cannot change.

## Verification

- Pure Java8 `GuiSettingKeyboard` overload and explicit endpoint helper; backward-compatible original step method.
- Existing keyboard test suite extended with 90,000 seeded min/max/value/modifier combinations, endpoint/overflow/invalid tests.
- Forge wiring asserts modifier and Home/End input are routed through the focused typed-setting branch, while retaining search and capture precedence.
- Merge gates: exact PR-head Core Java17/21 and Forge Linux/Windows JDK8 builds, provenance/JAR and fake-profile install/rollback; matched merge tree SHA; post-merge exact main CI.

Native Minecraft interaction, FPS, mod coexistence and prolonged stability remain unverified by CI.
