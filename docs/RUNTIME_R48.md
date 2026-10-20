# GhostC R2.28 — readable Target HUD name and health labels

## Source-audited issue

The 160-pixel Target HUD drew a player name at x+9, rejecting only names longer than 26 characters, regardless of actual Minecraft glyph widths. With `show_health` active, it drew the numeric HP label at x+width-70 independently. Medium or long player names could therefore overlap the HP label while still satisfying the 26-character gate.

## New implementation

- Java8 Minecraft-independent `TargetHudTextLayout` uses the actual supplied font-width function to reserve 9px margins and a 6px separation between the name and right-aligned HP text. Long names shorten at Unicode codepoint boundaries and end in an ASCII ellipsis `...`. Minecraft formatting color markers and control characters are removed from the text before display.
- Original health values `20.0 / 20.0` remain the **default**. New `hud.target.health_text` persisted choice supports `values` or optional `percent`. This uses the established ClickGUI settings, persistence, typed validation and original profile schema.
- Extremely large health values that do not fit the reserved label width fall back to compact percentages; malformed nonfinite/negative health or invalid max health produces a `?` label and safe zero health fraction instead of formatting `NaN`.
- The target selector, visible-only/range settings, original 160px widget, 43px/29px heights, position editor, purple bar, identity/world fencing and render fault guard remain unchanged. No combat, packet, auth or updater paths modified.

## Regression and acceptance

- New `TargetHudTextLayoutTests` covers **14,000 seeded** variable-font player names (wide/narrow glyphs, formatting markers, supplementary Unicode), two HP readout modes, health on/off, randomized custom health and exact pixel non-overlap. Boundary checks cover short-name and numeric-display compatibility, Unicode ellipsis, invalid health/mode, extreme values and GUI profile roundtrip.
- Existing `FeatureTests` verifies the added setting default, choice and saved-profile compatibility. Forge source/wiring verification asserts font measurement, right-aligned health text and lack of the old hardcoded text offset. Required JAR class inventory includes the new pure layout policy.
- Release metadata **2.28.0**, **46 Java suites** and **41 milestone guides** R08–R48. Merge gates: Java 17/21 Core+source tests, Linux/Windows JDK8 Forge 1.8.9 reobfuscation/JAR checks, Windows simulated profile install/collect/rollback.

**Runtime boundary:** CI does not prove real Minecraft font rendering or GUI readability. Verify both health modes, partial and long names, text alignment and other-mod compatibility in Minecraft 1.8.9; an extended 60-minute live gameplay soak remains outstanding. Keep G2/G4/G5 issues open.
